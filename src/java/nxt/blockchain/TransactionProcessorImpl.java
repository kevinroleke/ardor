/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.blockchain;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.atomictxs.AtomicChain;
import nxt.blockchain.atomictxs.AtomicChainsSet;
import nxt.blockchain.atomictxs.AtomicParentAppendix;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.EntityDbTable;
import nxt.dbschema.Db;
import nxt.peer.NetworkHandler;
import nxt.peer.NetworkMessage;
import nxt.peer.Peers;
import nxt.peer.TransactionsInventory;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Listeners;
import nxt.util.Logger;
import nxt.util.ThreadPool;
import nxt.util.security.BlockchainPermission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class TransactionProcessorImpl implements TransactionProcessor {

    private static final boolean enableTransactionRebroadcasting = Nxt.getBooleanProperty("nxt.enableTransactionRebroadcasting");
    private static final boolean testUnconfirmedTransactions = Nxt.getBooleanProperty("nxt.testUnconfirmedTransactions");
    public static final int maxUnconfirmedTransactions;

    static {
        int n = Nxt.getIntProperty("nxt.maxUnconfirmedTransactions");
        maxUnconfirmedTransactions = n <= 0 ? NetworkMessage.MAX_LIST_SIZE : Math.min(n, NetworkMessage.MAX_LIST_SIZE);
    }

    private static final TransactionProcessorImpl instance = new TransactionProcessorImpl();
    private static final BlockchainPermission blockchainPermission = new BlockchainPermission("getTransactionProcessor");

    public static TransactionProcessorImpl getInstance() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(blockchainPermission);
        }
        return instance;
    }

    public static void init() {}

    private final AtomicChainsSet<UnconfirmedTransaction> transactionCache = new AtomicChainsSet<>(
            Comparator.comparing(AtomicChain<UnconfirmedTransaction>::getComparableData));
    private volatile boolean cacheInitialized = false;
    private final DroppedTransactionStore knownDroppedTransactions =
            new DroppedTransactionStore(Nxt.getIntProperty("nxt.droppedTransactionsStoreSize"));

    final DbKey.LongKeyFactory<UnconfirmedTransaction> unconfirmedTransactionDbKeyFactory = new DbKey.LongKeyFactory<UnconfirmedTransaction>("id") {

        @Override
        public DbKey newKey(UnconfirmedTransaction unconfirmedTransaction) {
            return unconfirmedTransaction.getDbKey();
        }

    };

    final EntityDbTable<UnconfirmedTransaction> unconfirmedTransactionTable = new EntityDbTable<UnconfirmedTransaction>("public.unconfirmed_transaction", unconfirmedTransactionDbKeyFactory) {

        @Override
        protected UnconfirmedTransaction load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            UnconfirmedTransaction fromCache = getFromCache(dbKey);
            if (fromCache != null) {
                return fromCache;
            }
            return UnconfirmedTransaction.load(rs);
        }

        @Override
        protected void save(Connection con, UnconfirmedTransaction unconfirmedTransaction) throws SQLException {
            unconfirmedTransaction.save(con);
            addToCache(unconfirmedTransaction);
        }

        @Override
        public void popOffTo(int height) {
            try (Connection con = unconfirmedTransactionTable.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT * FROM unconfirmed_transaction WHERE height > ?")) {
                pstmt.setInt(1, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        UnconfirmedTransaction unconfirmedTransaction = load(con, rs, null);
                        waitingTransactions.add(unconfirmedTransaction);
                        removeFromCache(unconfirmedTransaction.getDbKey());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            super.popOffTo(height);
            unconfirmedDuplicates.clear();
        }

        @Override
        public void truncate() {
            super.truncate();
            clearCache();
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC, id ASC ";
        }

    };

    private final Set<TransactionImpl> broadcastedTransactions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Listeners<List<? extends Transaction>,Event> transactionListeners = new Listeners<>();

    private final AtomicChainsSet<UnconfirmedTransaction> waitingTransactions =
            new AtomicChainsSet<UnconfirmedTransaction>(
                    Comparator.comparing(AtomicChain<UnconfirmedTransaction>::getComparableData)) {

                @Override
                public boolean add(UnconfirmedTransaction unconfirmedTransaction) {
                    if (!super.add(unconfirmedTransaction)) {
                        return false;
                    }
                    if (transactionsCount() > maxUnconfirmedTransactions) {
                        AtomicChain<UnconfirmedTransaction> removedChain = removeLastChain();
                        for (UnconfirmedTransaction removed : removedChain) {
                            // we add all transactions to the dropped store
                            // with the averaged comparable data for the chain
                            knownDroppedTransactions.add(removed.getId(),
                                    removedChain.getComparableData(),
                                    removed.getExpiration());
                        }
                        if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_TX_INVENTORY)) {
                            Logger.logDebugMessage("Dropped " + removedChain);
                        }
                    }
                    return true;
                }

            };

    private final Map<TransactionType, Map<String, Integer>> unconfirmedDuplicates = new HashMap<>();


    private final Runnable removeUnconfirmedTransactionsThread = () -> {

        try {
            try {
                int now = Nxt.getEpochTime();
                knownDroppedTransactions.cleanup(now);
                if (Nxt.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                List<UnconfirmedTransaction> expiredTransactions = new ArrayList<>();
                try (DbIterator<UnconfirmedTransaction> iterator = unconfirmedTransactionTable.getManyBy(
                        new DbClause.IntClause("expiration", DbClause.Op.LT,
                                now), 0, -1, "")) {
                    while (iterator.hasNext()) {
                        expiredTransactions.add(iterator.next());
                    }
                }
                if (expiredTransactions.size() > 0) {
                    BlockchainImpl.getInstance().writeLock();
                    try {
                        try {
                            Db.db.beginTransaction();
                            for (UnconfirmedTransaction unconfirmedTransaction : expiredTransactions) {
                                removeUnconfirmedTransaction(unconfirmedTransaction.getTransaction());
                            }
                            Db.db.commitTransaction();
                        } catch (Exception e) {
                            Logger.logErrorMessage(e.toString(), e);
                            Db.db.rollbackTransaction();
                            throw e;
                        } finally {
                            Db.db.endTransaction();
                        }
                    } finally {
                        BlockchainImpl.getInstance().writeUnlock();
                    }
                }
            } catch (Exception e) {
                Logger.logMessage("Error removing unconfirmed transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private final Runnable rebroadcastTransactionsThread = () -> {

        try {
            try {
                if (Nxt.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                List<Transaction> transactionList = new ArrayList<>();
                int curTime = Nxt.getEpochTime();
                for (TransactionImpl transaction : broadcastedTransactions) {
                    if (transaction.getExpiration() < curTime || transaction.getChain().getTransactionHome().hasTransaction(transaction)) {
                        broadcastedTransactions.remove(transaction);
                    } else if (transaction.getTimestamp() < curTime - 30) {
                        transactionList.add(transaction);
                        if (transactionList.size() >= 10) {
                            TransactionsInventory.cacheTransactions(transactionList);
                            NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(transactionList));
                            transactionList.clear();
                        }
                    }
                }

                if (transactionList.size() > 0) {
                    TransactionsInventory.cacheTransactions(transactionList);
                    NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(transactionList));
                }

            } catch (Exception e) {
                Logger.logMessage("Error in transaction re-broadcasting thread", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private final Runnable processWaitingTransactionsThread = () -> {

        try {
            try {
                if (Nxt.getBlockchainProcessor().isDownloading() && ! testUnconfirmedTransactions) {
                    return;
                }
                processWaitingTransactions();
            } catch (Exception e) {
                Logger.logMessage("Error processing waiting transactions", e);
            }
        } catch (Throwable t) {
            Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }

    };

    private TransactionProcessorImpl() {
        if (!Constants.isLightClient) {
            if (!Constants.isOffline) {
                ThreadPool.scheduleThread("RebroadcastTransactions", rebroadcastTransactionsThread, 23);
            }
            ThreadPool.scheduleThread("RemoveUnconfirmedTransactions", removeUnconfirmedTransactionsThread, 20);
            ThreadPool.scheduleThread("ProcessWaitingTransactions", processWaitingTransactionsThread, 1);
        }
    }

    @Override
    public boolean addListener(Listener<List<? extends Transaction>> listener, Event eventType) {
        return transactionListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<List<? extends Transaction>> listener, Event eventType) {
        return transactionListeners.removeListener(listener, eventType);
    }

    public void notifyListeners(List<? extends Transaction> transactions, Event eventType) {
        transactionListeners.notify(transactions, eventType);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions() {
        return unconfirmedTransactionTable.getAll(0, -1);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to) {
        return unconfirmedTransactionTable.getAll(from, to);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(String sort) {
        return unconfirmedTransactionTable.getAll(0, -1, sort);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getAllUnconfirmedTransactions(int from, int to, String sort) {
        return unconfirmedTransactionTable.getAll(from, to, sort);
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getUnconfirmedFxtTransactions() {
        return unconfirmedTransactionTable.getManyBy(new DbClause.IntClause("chain_id", FxtChain.FXT.getId()), 0, -1,
                " ORDER BY transaction_height ASC, fee DESC, arrival_timestamp ASC, id ASC "); // order by fee
    }

    @Override
    public DbIterator<UnconfirmedTransaction> getUnconfirmedChildTransactions(ChildChain childChain) {
        return unconfirmedTransactionTable.getManyBy(new DbClause.IntClause("chain_id", childChain.getId()), 0, -1,
                " ORDER BY transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC, id ASC "); // order by fee_per_byte
    }

    @Override
    public UnconfirmedTransaction getUnconfirmedTransaction(long transactionId) {
        DbKey dbKey = unconfirmedTransactionDbKeyFactory.newKey(transactionId);
        return getUnconfirmedTransaction(dbKey);
    }

    private UnconfirmedTransaction getUnconfirmedTransaction(DbKey dbKey) {
        //TODO why do we need this lock?
        Nxt.getBlockchain().readLock();
        try {
            UnconfirmedTransaction transaction = getFromCache(dbKey);
            if (transaction != null) {
                return transaction;
            }
        } finally {
            Nxt.getBlockchain().readUnlock();
        }
        return unconfirmedTransactionTable.get(dbKey);
    }

    @Override
    public List<Long> getAllUnconfirmedTransactionIds() {
        List<Long> result = new ArrayList<>();
        try (Connection con = unconfirmedTransactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM unconfirmed_transaction");
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    public int getUnconfirmedPoolSize() {
        return transactionCache.transactionsCount() + waitingTransactions.transactionsCount();
    }

    private UtxComparableData getLowestPriorityComparableData() {
        AtomicChain<UnconfirmedTransaction> lowestPriorityChainFromCache =
                transactionCache.peekLastChain();
        UtxComparableData fromCache = lowestPriorityChainFromCache == null ?
                null : lowestPriorityChainFromCache.getComparableData();
        UtxComparableData result;
        AtomicChain<UnconfirmedTransaction> lowestPriorityChainFromQueue =
                waitingTransactions.peekLastChain();
        if (lowestPriorityChainFromQueue == null) {
            result = fromCache;
        } else {
            if (fromCache == null) {
                result = lowestPriorityChainFromQueue.getComparableData();
            } else {
                result = fromCache.compareTo(
                        lowestPriorityChainFromQueue.getComparableData()) <= 0 ?
                        fromCache : lowestPriorityChainFromQueue.getComparableData();
            }
        }
        if (result != null) {
            //Use the lowest comparable data with the best possible arrivalTime and id
            // so that we ignore transactions that are only different by arrivalTime
            // or id
            return result.cloneIgnoringTimeAndId(0, Integer.MIN_VALUE);
        }
        return null;
    }

    @Override
    public UnconfirmedTransaction[] getAllWaitingTransactions() {
        UnconfirmedTransaction[] transactions;
        BlockchainImpl.getInstance().readLock();
        try {
            ArrayList<UnconfirmedTransaction> arrayList = waitingTransactions.toTransactionsList();
            transactions = arrayList.toArray(new UnconfirmedTransaction[0]);
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
        return transactions;
    }

    public Collection<UnconfirmedTransaction> getWaitingTransactions() {
        return waitingTransactions.toTransactionsList();
    }

    @Override
    public TransactionImpl[] getAllBroadcastedTransactions() {
        BlockchainImpl.getInstance().readLock();
        try {
            return broadcastedTransactions.toArray(new TransactionImpl[broadcastedTransactions.size()]);
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
    }

    @Override
    public void broadcast(Transaction transaction) throws NxtException.ValidationException {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (transaction.getChain().getTransactionHome().hasTransaction(transaction)) {
                Logger.logMessage("Transaction " + transaction.getStringId() + " already in blockchain, will not broadcast again");
                return;
            }
            if (getUnconfirmedTransaction(transaction.getId()) != null) {
                if (enableTransactionRebroadcasting) {
                    broadcastedTransactions.add((TransactionImpl) transaction);
                    Logger.logMessage("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will re-broadcast");
                } else {
                    Logger.logMessage("Transaction " + transaction.getStringId() + " already in unconfirmed pool, will not broadcast again");
                }
                return;
            }
            transaction.validate();

            //TODO this validation was moved to ChildTransactionImpl.validate and it
            // must be removed after Constants.MAX_DEADLINE_BLOCK
            short maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
            if (transaction.getDeadline() > maxDeadline) {
                throw new NxtException.NotValidException("Deadline " +
                        transaction.getDeadline() + " exceeds the max deadline of " +
                        maxDeadline + "; fee = " + transaction.getFee());
            }

            UnconfirmedTransaction unconfirmedTransaction = ((TransactionImpl) transaction).newUnconfirmedTransaction(System.currentTimeMillis(), false);
            boolean broadcastLater = BlockchainProcessorImpl.getInstance().isProcessingBlock();
            if (broadcastLater) {
                waitingTransactions.add(unconfirmedTransaction);
                broadcastedTransactions.add((TransactionImpl) transaction);
                Logger.logDebugMessage("Will broadcast new transaction later " + transaction.getStringId());
            } else {
                Set<? extends TransactionImpl> displaced = processTransaction(unconfirmedTransaction);
                Logger.logDebugMessage(String.format("Accepted new transaction %s on chain %s", Convert.toHexString(transaction.getFullHash()), transaction.getChain().getName()));
                removeUnconfirmedTransactions(displaced);
                List<Transaction> acceptedTransactions = Collections.singletonList(transaction);
                TransactionsInventory.cacheTransactions(acceptedTransactions);
                NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(acceptedTransactions));
                transactionListeners.notify(acceptedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
                if (enableTransactionRebroadcasting) {
                    broadcastedTransactions.add((TransactionImpl) transaction);
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void broadcastLater(Transaction transaction) {
        broadcastedTransactions.add((TransactionImpl)transaction);
    }

    @Override
    public void clearUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            List<Transaction> removed = new ArrayList<>();
            try {
                Db.db.beginTransaction();
                try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions()) {
                    for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                        unconfirmedTransaction.getTransaction().undoUnconfirmed();
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                }
                unconfirmedTransactionTable.truncate();
                Db.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            unconfirmedDuplicates.clear();
            waitingTransactions.clear();
            broadcastedTransactions.clear();
            clearTransactionsCache();
            if (!removed.isEmpty()) {
                transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void requeueAllUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    requeueAllUnconfirmedTransactions();
                    Db.db.commitTransaction();
                } catch (Exception e) {
                    Logger.logErrorMessage(e.toString(), e);
                    Db.db.rollbackTransaction();
                    throw e;
                } finally {
                    Db.db.endTransaction();
                }
                return;
            }
            List<Transaction> removed = new ArrayList<>();
            try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = getAllUnconfirmedTransactions()) {
                for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                    unconfirmedTransaction.getTransaction().undoUnconfirmed();
                    if (removed.size() < maxUnconfirmedTransactions) {
                        removed.add(unconfirmedTransaction.getTransaction());
                    }
                    waitingTransactions.add(unconfirmedTransaction);
                }
            }
            unconfirmedTransactionTable.truncate();
            unconfirmedDuplicates.clear();
            clearTransactionsCache();
            if (!removed.isEmpty()) {
                transactionListeners.notify(removed, Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    @Override
    public void rebroadcastAllUnconfirmedTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            try (DbIterator<UnconfirmedTransaction> oldNonBroadcastedTransactions = getAllUnconfirmedTransactions()) {
                for (UnconfirmedTransaction unconfirmedTransaction : oldNonBroadcastedTransactions) {
                    if (unconfirmedTransaction.getTransaction().isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                        Logger.logDebugMessage("Skipping duplicate unconfirmed transaction " + unconfirmedTransaction.getTransaction().getJSONObject().toString());
                    } else if (enableTransactionRebroadcasting) {
                        broadcastedTransactions.add(unconfirmedTransaction.getTransaction());
                    }
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    private void removeUnconfirmedTransactions(Collection<? extends TransactionImpl> transactions) {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    removeUnconfirmedTransactions(transactions);
                    Db.db.commitTransaction();
                } catch (Exception e) {
                    Logger.logErrorMessage(e.toString(), e);
                    Db.db.rollbackTransaction();
                    throw e;
                } finally {
                    Db.db.endTransaction();
                }
                return;
            }
            transactions.forEach(this::removeUnconfirmedTransaction);
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    void removeUnconfirmedTransaction(TransactionImpl transaction) {
        if (!Db.db.isInTransaction()) {
            try {
                Db.db.beginTransaction();
                removeUnconfirmedTransaction(transaction);
                Db.db.commitTransaction();
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            return;
        }
        try (Connection con = unconfirmedTransactionTable.getConnection();
             PreparedStatement pstmt = con.prepareStatement("DELETE FROM unconfirmed_transaction WHERE id = ?")) {
            pstmt.setLong(1, transaction.getId());
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                transaction.undoUnconfirmed();
                DbKey dbKey = unconfirmedTransactionDbKeyFactory.newKey(transaction.getId());
                removeFromCache(dbKey);
                transactionListeners.notify(Collections.singletonList(transaction), Event.REMOVED_UNCONFIRMED_TRANSACTIONS);
                if (transaction.getChain() != FxtChain.FXT) {
                    try (DbIterator<UnconfirmedTransaction> iterator = getUnconfirmedFxtTransactions()) {
                        while (iterator.hasNext()) {
                            Transaction fxtTransaction = iterator.next().getTransaction();
                            if (fxtTransaction instanceof ChildBlockFxtTransactionImpl && ((ChildBlockFxtTransactionImpl)fxtTransaction).getChildChain() == transaction.getChain()) {
                                byte[][] childTransactionHashes = ((ChildBlockFxtTransactionImpl)fxtTransaction).getChildTransactionFullHashes();
                                for (byte[] hash : childTransactionHashes) {
                                    if (Arrays.equals(hash, transaction.getFullHash())) {
                                        removeUnconfirmedTransaction((TransactionImpl)fxtTransaction);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage(e.toString(), e);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void processLater(Collection<? extends FxtTransaction> transactions) {
        long currentTime = System.currentTimeMillis();
        BlockchainImpl.getInstance().writeLock();
        try {
            transactions.forEach(fxt -> {
                FxtTransactionImpl fxtTransaction = (FxtTransactionImpl)fxt;
                if (! TransactionHome.hasFxtTransaction(fxtTransaction.getId(), Integer.MAX_VALUE)) {
                    boolean keep = true;
                    if (fxtTransaction instanceof ChildBlockFxtTransactionImpl) {
                        TransactionHome transactionHome = ((ChildBlockFxtTransactionImpl) fxtTransaction).getChildChain().getTransactionHome();
                        for (ChildTransactionImpl childTransaction : fxtTransaction.getChildTransactions()) {
                            if (! transactionHome.hasTransaction(childTransaction)) {
                                childTransaction.unsetBlock();
                                waitingTransactions.add(childTransaction.newUnconfirmedTransaction(Math.min(currentTime, Convert.fromEpochTime(childTransaction.getTimestamp())), true));
                            } else {
                                keep = false;
                            }
                        }
                    }
                    if (keep) {
                        fxtTransaction.unsetBlock();
                        waitingTransactions.add(fxtTransaction.newUnconfirmedTransaction(Math.min(currentTime, Convert.fromEpochTime(fxtTransaction.getTimestamp())), true));
                    }
                }
            });
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    void processWaitingTransactions() {
        BlockchainImpl.getInstance().writeLock();
        try {
            if (unconfirmedTransactionTable.getCount() / 2 > maxUnconfirmedTransactions) {
                Logger.logDebugMessage("Unconfirmed transaction table size exceeded twice the maximum allowed, re-queueing");
                requeueAllUnconfirmedTransactions();
            }
            if (waitingTransactions.transactionsCount() != 0) {
                int currentTime = Nxt.getEpochTime();
                List<UnconfirmedTransaction> transactionsToProcess = new ArrayList<>();
                synchronized (waitingTransactions) {
                    waitingTransactions.getAllChains().forEach(chain -> {
                        if (chain.isComplete()) {
                            transactionsToProcess.addAll(chain);
                        } else {
                            chain.stream().filter(t -> {
                                AtomicParentAppendix app = AtomicParentAppendix.get(t);
                                return app == null || currentTime < t.getTimestamp() + app.getOrphanUnconfirmedPoolDeadline();
                            }).forEach(transactionsToProcess::add);
                        }
                    });
                    waitingTransactions.clear();
                }
                List<Transaction> addedUnconfirmedTransactions = new ArrayList<>();
                boolean processedChildTransactions = false;
                while (true) {
                    for (UnconfirmedTransaction unconfirmedTransaction : transactionsToProcess) {
                        if (!processedChildTransactions && unconfirmedTransaction.getType() == ChildBlockFxtTransactionType.INSTANCE) {
                            continue;
                        }
                        try {
                            unconfirmedTransaction.validate();
                            processTransaction(unconfirmedTransaction);
                            addedUnconfirmedTransactions.add(unconfirmedTransaction.getTransaction());
                        } catch (NxtException.ExistingTransactionException ignored) {
                        } catch (NxtException.NotCurrentlyValidException e) {
                            if (unconfirmedTransaction.getExpiration() >= currentTime
                                    && currentTime - Convert.toEpochTime(
                                    unconfirmedTransaction.getArrivalTimestamp()) <= 3600) {
                                waitingTransactions.add(unconfirmedTransaction);
                            }
                        } catch (NxtException.ValidationException |
                                 RuntimeException ignored) {
                        }
                    }
                    if (!processedChildTransactions) {
                        processedChildTransactions = true;
                    } else {
                        break;
                    }
                }
                if (addedUnconfirmedTransactions.size() > 0) {
                    transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
                }
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    /**
     * Filter out any of the IDs that are known to be already available in the
     * pool or forged.
     * And filter out any ids that are known to be dropped unless they are with
     * better priority than some of the transactions in the pool or the pool is
     * not full
     *
     * @param requestIdsStream stream of ids
     * @param maxSize maxSize
     * @return A list of filtered
     */
    @Override
    public List<ChainTransactionId> filterPeerRequestIds(
            Stream<ChainTransactionId> requestIdsStream, int maxSize) {
        //Filter out anything that is already known
        Stream<ChainTransactionId> stream = requestIdsStream.filter(id -> {
            DbKey dbKey = unconfirmedTransactionDbKeyFactory.newKey(id.getTransactionId());
            synchronized (transactionCache) {
                if (isInCache(dbKey)) {
                    return false;
                }
            }
            return !id.getChain().getTransactionHome().hasTransaction(id.getFullHash(), id.getTransactionId(), Integer.MAX_VALUE);
        });

        //Filter out any known dropped transactions unless there is free space
        int availableSizeToFill = maxUnconfirmedTransactions - getUnconfirmedPoolSize();
        return UtxComparableData.filterWithLimits(stream,
                id -> {
                    UtxComparableData comparableData = knownDroppedTransactions.get(
                            id.getTransactionId());
                    if (comparableData != null) {
                        return comparableData;
                    }
                    return UtxComparableData.HIGHEST;
                },
                availableSizeToFill, maxSize,
                getLowestPriorityComparableData(), null);
    }

    @Override
    public List<Transaction> filterPeerTransactions(List<Transaction> transactions) {
        int availableSizeToFill = maxUnconfirmedTransactions - getUnconfirmedPoolSize();
        long arrivalTime = System.currentTimeMillis();
        return UtxComparableData.filterWithLimits(transactions.stream(),
                t -> new UtxComparableData(t, false, false, arrivalTime, t.getFee(),
                        t.getFullSize()),
                availableSizeToFill, Integer.MAX_VALUE,
                getLowestPriorityComparableData(),
                (t, cd) -> knownDroppedTransactions.add(t.getId(), cd, t.getExpiration()));
    }

    //process ChildBlockTransactions last
    private static final Comparator<Transaction> peerTransactionComparator = (o1, o2) ->
            Boolean.compare(o2.getType() != ChildBlockFxtTransactionType.INSTANCE, o1.getType() != ChildBlockFxtTransactionType.INSTANCE);

    @Override
    public List<TransactionImpl> processPeerTransactions(List<Transaction> transactions) throws NxtException.NotValidException {
        if (Nxt.getBlockchain().getHeight() <= Constants.LAST_KNOWN_BLOCK && !testUnconfirmedTransactions) {
            return Collections.emptyList();
        }
        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }
        transactions.sort(peerTransactionComparator);
        long arrivalTimestamp = System.currentTimeMillis();
        List<TransactionImpl> receivedTransactions = new ArrayList<>();
        List<TransactionImpl> sendToPeersTransactions = new ArrayList<>();
        List<TransactionImpl> addedUnconfirmedTransactions = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        Set<ChildBlockFxtTransactionImpl> displaced = new HashSet<>();
        for (Transaction inputTransaction : transactions) {
            try {
                TransactionImpl transaction = (TransactionImpl)inputTransaction;
                receivedTransactions.add(transaction);
                UnconfirmedTransaction unconfirmedTransaction = transaction.newUnconfirmedTransaction(arrivalTimestamp, false);
                unconfirmedTransaction.validate();
                displaced.addAll(processTransaction(unconfirmedTransaction));
                if (broadcastedTransactions.contains(transaction)) {
                    Logger.logDebugMessage("Received back transaction " + transaction.getStringId()
                            + " that we broadcasted, will not forward again to peers");
                } else {
                    sendToPeersTransactions.add(transaction);
                }
                addedUnconfirmedTransactions.add(transaction);

            } catch (NxtException.NotCurrentlyValidException ignore) {
            } catch (NxtException.ValidationException | RuntimeException e) {
                Logger.logDebugMessage(String.format("Invalid transaction from peer: %s", inputTransaction.getJSONObject()), e);
                exceptions.add(e);
            }
        }
        removeUnconfirmedTransactions(displaced);
        if (!sendToPeersTransactions.isEmpty()) {
            NetworkHandler.broadcastMessage(new NetworkMessage.TransactionsInventoryMessage(sendToPeersTransactions));
        }
        if (!addedUnconfirmedTransactions.isEmpty()) {
            transactionListeners.notify(addedUnconfirmedTransactions, Event.ADDED_UNCONFIRMED_TRANSACTIONS);
        }
        broadcastedTransactions.removeAll(receivedTransactions);
        if (!exceptions.isEmpty()) {
            throw new NxtException.NotValidException("Peer sends invalid transactions: " + exceptions.toString());
        }
        return addedUnconfirmedTransactions;
    }

    private Set<ChildBlockFxtTransactionImpl> processTransaction(UnconfirmedTransaction unconfirmedTransaction) throws NxtException.ValidationException {
        TransactionImpl transaction = unconfirmedTransaction.getTransaction();
        int curTime = Nxt.getEpochTime();
        if (transaction.getExpiration() < curTime) {
            throw new NxtException.NotCurrentlyValidException("Expired transaction");
        }
        int maxTimestamp = curTime + Constants.MAX_TIMEDRIFT;
        if (transaction.getType() == ChildBlockFxtTransactionType.INSTANCE) {
            long nextHitTime = Generator.getNextHitTime(Nxt.getBlockchain().getLastBlock().getId(), curTime);
            if (nextHitTime > 0 && nextHitTime < curTime) {
                maxTimestamp = (int)nextHitTime + Constants.MAX_TIMEDRIFT;
            }
        }
        if (transaction.getTimestamp() > maxTimestamp) {
            throw new NxtException.NotCurrentlyValidException("Transaction timestamp from the future");
        }
        if (transaction.getVersion() < 1) {
            throw new NxtException.NotValidException("Invalid transaction version");
        }
        Set<ChildBlockFxtTransactionImpl> displacedTransactions = Collections.emptySet();
        BlockchainImpl.getInstance().writeLock();
        try {
            try {
                Db.db.beginTransaction();
                if (Nxt.getBlockchain().getHeight() < Constants.LAST_KNOWN_BLOCK && !testUnconfirmedTransactions) {
                    throw new NxtException.NotCurrentlyValidException("Blockchain not ready to accept transactions");
                }
                if (getUnconfirmedTransaction(unconfirmedTransaction.getDbKey()) != null || transaction.getChain().getTransactionHome().hasTransaction(transaction)) {
                    throw new NxtException.ExistingTransactionException("Transaction already processed");
                }
                transaction.validateId();
                if (! transaction.verifySignature()) {
                    if (Account.getAccount(transaction.getSenderId()) != null) {
                        throw new NxtException.NotValidException("Transaction signature verification failed");
                    } else {
                        throw new NxtException.NotCurrentlyValidException("Unknown transaction sender");
                    }
                }

                if (transaction.getType() == ChildBlockFxtTransactionType.INSTANCE) {
                    displacedTransactions = new HashSet<>(findDisplacedChildBlockTransactions((ChildBlockFxtTransactionImpl)transaction));
                }

                if (! transaction.applyUnconfirmed()) {
                    throw new NxtException.InsufficientBalanceException("Insufficient balance");
                }

                if (transaction.isUnconfirmedDuplicate(unconfirmedDuplicates)) {
                    throw new NxtException.NotCurrentlyValidException("Duplicate unconfirmed transaction");
                }

                unconfirmedTransactionTable.insert(unconfirmedTransaction);

                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
        return displacedTransactions;
    }

    private List<ChildBlockFxtTransactionImpl> findDisplacedChildBlockTransactions(ChildBlockFxtTransactionImpl transaction) throws NxtException.NotCurrentlyValidException {
        List<ChildBlockFxtTransactionImpl> displaced = new ArrayList<>();
        try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = getUnconfirmedFxtTransactions()) {
            while (unconfirmedTransactions.hasNext()) {
                FxtTransaction poolTransaction = (FxtTransaction)unconfirmedTransactions.next().getTransaction();
                if (poolTransaction.getType() == ChildBlockFxtTransactionType.INSTANCE
                        && ((ChildBlockFxtTransaction)poolTransaction).getChildChain() == transaction.getChildChain())  {
                    try {
                        poolTransaction.validate();
                    } catch (NxtException.ValidationException e) {
                        continue;
                    }
                    if (poolTransaction.getFee() >= transaction.getFee()) { // transaction with same or higher fee already in the pool
                        if (((ChildBlockFxtTransactionImpl)poolTransaction).containsAll(transaction.getChildTransactions())) {
                            throw new NxtException.NotCurrentlyValidException("A ChildBlockTransaction with same or higher fee "
                                    + "and including the same child transactions is already in the pool");
                        }
                    } else { // offering higher fee for same or more child transactions, remove existing ChildBlockTransaction
                        if (transaction.containsAll(poolTransaction.getChildTransactions())) {
                            displaced.add((ChildBlockFxtTransactionImpl)poolTransaction);
                        }
                    }
                }
            }
        }
        return displaced;
    }

    /**
     * Get the cached unconfirmed transactions
     *
     * @param   exclude                 List of transaction identifiers to exclude
     */
    @Override
    public List<? extends Transaction> getCachedUnconfirmedTransactions(List<Long> exclude, int maxCount) {
        List<UnconfirmedTransaction> result = new ArrayList<>();
        Nxt.getBlockchain().readLock();
        try {
            //
            // Initialize the unconfirmed transaction cache if it hasn't been done yet
            //
            synchronized(transactionCache) {
                if (!cacheInitialized) {
                    try (DbIterator<UnconfirmedTransaction> it = getAllUnconfirmedTransactions()) {
                        while (it.hasNext()) {
                            UnconfirmedTransaction unconfirmedTransaction = it.next();

                            //TODO This check is only done to prevent broadcasting
                            // of transactions that were accepted in the unconfirmed pool
                            // with invalid deadline. It must be removed after
                            // Constants.MAX_DEADLINE_BLOCK when all such
                            // transactions are anyways invalid and should not
                            // enter the unconfirmed pool
                            short maxDeadline = ChildTransactionImpl.getMaxDeadline(unconfirmedTransaction.getTransaction());
                            if (unconfirmedTransaction.getDeadline() > maxDeadline) {
                                Logger.logErrorMessage("Transaction " +
                                        unconfirmedTransaction.getId() +
                                        " deadline exceeds the maxDeadline and will not be loaded to cache");
                                continue;
                            }

                            addToCache(unconfirmedTransaction);
                        }
                    }
                    cacheInitialized = true;
                }

                //
                // Build the result set
                //
                for (UnconfirmedTransaction transaction : transactionCache.toTransactionsList()) {
                    if (result.size() >= maxCount) {
                        break;
                    }
                    if (Collections.binarySearch(exclude, transaction.getId()) < 0) {
                        result.add(transaction);
                    }
                }
            }
        } finally {
            Nxt.getBlockchain().readUnlock();
        }
        return result;
    }

    private boolean isInCache(DbKey dbKey) {
        if (dbKey == null) {
            return false;
        }
        return transactionCache.hasTransaction(((DbKey.LongKey)dbKey).getId());
    }

    private UnconfirmedTransaction getFromCache(DbKey dbKey) {
        if (dbKey == null) {
            return null;
        }
        return transactionCache.getTransaction(((DbKey.LongKey)dbKey).getId());
    }

    private void addToCache(UnconfirmedTransaction unconfirmedTransaction) {
        transactionCache.add(unconfirmedTransaction);
    }

    private void removeFromCache(DbKey dbKey) {
        if (dbKey == null) {
            return;
        }
        transactionCache.remove(((DbKey.LongKey)dbKey).getId());
    }

    private void clearTransactionsCache() {
        transactionCache.clear();
    }

    /**
     * Restore expired prunable data
     *
     * @param   transactions                        Transactions containing prunable data
     * @return                                      Processed transactions
     */
    @Override
    public List<Transaction> restorePrunableData(List<Transaction> transactions) {
        List<Transaction> processed = new ArrayList<>();
        Nxt.getBlockchain().readLock();
        try {
            Db.db.beginTransaction();
            try {
                //
                // Check each transaction returned by the archive peer
                //
                for (Transaction inputTransaction : transactions) {
                    TransactionImpl transaction = (TransactionImpl)inputTransaction;
                    TransactionImpl myTransaction = transaction.getChain().getTransactionHome().findTransaction(transaction.getFullHash());
                    if (myTransaction != null) {
                        boolean foundAllData = true;
                        //
                        // Process each prunable appendage
                        //
                        appendageLoop: for (Appendix.AbstractAppendix appendage : transaction.getAppendages()) {
                            if ((appendage instanceof Appendix.Prunable)) {
                                //
                                // Don't load the prunable data if we already have the data
                                //
                                for (Appendix.AbstractAppendix myAppendage : myTransaction.getAppendages()) {
                                    if (myAppendage.getClass() == appendage.getClass()) {
                                        myAppendage.loadPrunable(myTransaction, true);
                                        if (((Appendix.Prunable)myAppendage).hasPrunableData()) {
                                            Logger.logDebugMessage(String.format("Already have prunable data for transaction %s %s appendage",
                                                    myTransaction.getStringId(), myAppendage.getAppendixName()));
                                            continue appendageLoop;
                                        }
                                        break;
                                    }
                                }
                                //
                                // Load the prunable data
                                //
                                if (((Appendix.Prunable)appendage).hasPrunableData()) {
                                    Logger.logDebugMessage(String.format("Loading prunable data for transaction %s %s appendage",
                                            transaction.getStringId(), appendage.getAppendixName()));
                                    ((Appendix.Prunable)appendage).restorePrunableData(transaction, myTransaction.getBlockTimestamp(), myTransaction.getHeight());
                                } else {
                                    foundAllData = false;
                                }
                            }
                        }
                        if (foundAllData) {
                            processed.add(myTransaction);
                        }
                        Db.db.clearCache();
                        Db.db.commitTransaction();
                    }
                }
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                processed.clear();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
        } finally {
            Nxt.getBlockchain().readUnlock();
        }
        return processed;
    }

    @Override
    public Map<String, Object> getUnconfirmedPoolInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("poolSize", getUnconfirmedPoolSize());
        result.put("droppedStoreSize", knownDroppedTransactions.getSize());
        return result;
    }
}
