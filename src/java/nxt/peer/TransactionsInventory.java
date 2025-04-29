/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
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
package nxt.peer;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.blockchain.BlockchainProcessor;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.Transaction;
import nxt.util.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TransactionsInventory {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    /** Transaction cache */
    private static final ConcurrentHashMap<ChainTransactionId, Transaction> transactionCache = new ConcurrentHashMap<>();

    /** Pending transactions */
    private static final Set<ChainTransactionId> pendingTransactions = new HashSet<>();

    /** Currently not valid transactions */
    private static final ConcurrentHashMap<ChainTransactionId, Transaction> notCurrentlyValidTransactions = new ConcurrentHashMap<>();

    private TransactionsInventory() {}

    /**
     * Process a TransactionsInventory message (there is no response message)
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.TransactionsInventoryMessage request) {
        List<ChainTransactionId> transactionIds = request.getTransactionIds();

        //
        // Request transactions that are not already in our cache
        //
        Stream<ChainTransactionId> stream = transactionIds.stream().filter(
                transactionId -> transactionCache.get(transactionId) == null
                        && notCurrentlyValidTransactions.get(transactionId) == null);

        // Additionally filter the IDs according to the current content of the pool
        List<ChainTransactionId> requestIds = Nxt.getTransactionProcessor()
                .filterPeerRequestIds(stream, 100);

        if (requestIds.isEmpty()) {
            return null;
        }

        //Prevent requesting the same transaction twice
        synchronized (pendingTransactions) {
            for (Iterator<ChainTransactionId> iterator = requestIds.iterator(); iterator.hasNext(); ) {
                ChainTransactionId transactionId = iterator.next();
                if (pendingTransactions.contains(transactionId)) {
                    iterator.remove();
                } else {
                    pendingTransactions.add(transactionId);
                }
            }
        }

        if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_DETAILS)) {
            Logger.logDebugMessage("Requesting transactions " + requestIds);
        }

        if (requestIds.isEmpty()) {
            return null;
        }
        StringBuilder log = new StringBuilder();
        if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_TX_INVENTORY)) {
            log.append("From ").append(peer.getHost())
                    .append(" at ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .append(" inventoryIDs ")
                    .append(transactionIds.size())
                    .append(" requestIds ").append(requestIds.size());
        }
        Peers.peersService.execute(() -> {
            //
            // Request the transactions, starting with the peer that sent the TransactionsInventory
            // message.  We will update the transaction cache with transactions that have
            // been successfully processed.  We will keep contacting peers until
            // we have received all of the transactions or we run out of peers.
            //
            try {
                if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_TX_INVENTORY)) {
                    Nxt.getTransactionProcessor().getUnconfirmedPoolInfo()
                            .forEach((k, v) -> log.append(" ").append(k).append(" ").append(v));
                    log.append(" start ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER));
                }
                List<Peer> connectedPeers = Peers.getConnectedPeers();
                if (connectedPeers.isEmpty()) {
                    return;
                }
                int startIndex = connectedPeers.indexOf(peer);
                if (startIndex < 0) {
                    startIndex = 0;
                }
                int index = startIndex;
                Set<Transaction> notAcceptedTransactions = new HashSet<>();
                while (true) {
                    Peer feederPeer = connectedPeers.get(index);
                    NetworkMessage.GetTransactionsMessage transactionsRequest =
                            new NetworkMessage.GetTransactionsMessage(requestIds);
                    NetworkMessage.TransactionsMessage response =
                            (NetworkMessage.TransactionsMessage)feederPeer.sendRequest(transactionsRequest);
                    if (response != null && response.getTransactionCount() > 0) {
                        try {
                            List<Transaction> transactions = response.getTransactions();
                            Set<ChainTransactionId> receivedTxIds = transactions.stream()
                                    .map(ChainTransactionId::getChainTransactionId)
                                    .collect(Collectors.toSet());
                            requestIds.removeAll(receivedTxIds);
                            synchronized (pendingTransactions) {
                                pendingTransactions.removeAll(receivedTxIds);
                            }
                            if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_TX_INVENTORY)) {
                                log.append(" prefilter ").append(transactions.size());
                            }
                            transactions = Nxt.getTransactionProcessor().filterPeerTransactions(transactions);
                            if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_TX_INVENTORY)) {
                                log.append(" postfilter ").append(transactions.size());
                            }
                            notAcceptedTransactions.addAll(transactions);
                            List<? extends Transaction> addedTransactions = Nxt.getTransactionProcessor().processPeerTransactions(transactions);
                            cacheTransactions(addedTransactions);
                            addedTransactions.forEach(notAcceptedTransactions::remove);
                        } catch (RuntimeException | NxtException.ValidationException e) {
                            feederPeer.blacklist(e);
                        }
                    }
                    if (requestIds.isEmpty()) {
                        break;
                    }
                    index = (index < connectedPeers.size()-1 ? index + 1 : 0);
                    if (index == startIndex) {
                        break;
                    }
                }
                try {
                    notAcceptedTransactions.forEach(transaction -> notCurrentlyValidTransactions.put(ChainTransactionId.getChainTransactionId(transaction), transaction));
                    //some not currently valid transactions may have become valid as others were fetched from peers, try processing them again
                    List<? extends Transaction> addedTransactions = Nxt.getTransactionProcessor().processPeerTransactions(new ArrayList<>(notCurrentlyValidTransactions.values()));
                    addedTransactions.forEach(transaction -> notCurrentlyValidTransactions.remove(ChainTransactionId.getChainTransactionId(transaction)));
                } catch (NxtException.NotValidException e) {
                    Logger.logErrorMessage(e.getMessage(), e); //should not happen
                }
            } finally {
                if (Peers.isLogLevelEnabled(Peers.LOG_LEVEL_TX_INVENTORY)) {
                    log.append(" end ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                            .append(" missing ").append(requestIds.size());
                    Logger.logDebugMessage(log.toString());
                }
                synchronized (pendingTransactions) {
                    requestIds.forEach(pendingTransactions::remove);
                }
            }
        });
        return null;
    }

    /**
     * Get a cached transaction
     *
     * @param   transactionId           The transaction identifier
     * @return                          Cached transaction or null
     */
    static Transaction getCachedTransaction(ChainTransactionId transactionId) {
        return transactionCache.get(transactionId);
    }

    /**
     * Add local transactions to the transaction cache
     *
     * @param   transactions            Local transactions
     */
    public static void cacheTransactions(List<? extends Transaction> transactions) {
        transactions.forEach(transaction -> transactionCache.put(ChainTransactionId.getChainTransactionId(transaction), transaction));
    }

    /*
      Purge the transaction cache when a block is pushed
     */
    static {
        Nxt.getBlockchainProcessor().addListener((block) -> {
            final int now = Nxt.getEpochTime();
            transactionCache.values().removeIf(transaction -> now - transaction.getTimestamp() > 10 * 60);
            notCurrentlyValidTransactions.values().removeIf(transaction -> now - transaction.getTimestamp() > 10 * 60
                    || transaction.getTimestamp() > now + Constants.MAX_TIMEDRIFT);
        }, BlockchainProcessor.Event.BLOCK_PUSHED);
    }
}
