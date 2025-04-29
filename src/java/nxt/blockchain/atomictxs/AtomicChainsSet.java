package nxt.blockchain.atomictxs;

import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.Transaction;
import nxt.util.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Set of transactions that tracks the atomic chains formed by the transactions in the set.
 *
 * <p>The iteration order of the transactions in the set is according to a comparator
 * provided in the constructor of this class</p>
 *
 * Public access is synchronized except from
 * the {@link #getAllChains()}. Users of this method should synchronize by locking
 * the AtomicChainsSet instance
 */
public class AtomicChainsSet<T extends Transaction> {
    public static final String CHAIN_ALREADY_EXISTS_MESSAGE = "orderedChains already contains a chain with supposedly " +
            "unknown transactions. Probably the provided comparator is not consistent with equals";

    private final Map<ChainTransactionId, AtomicChain<T>> txIdToChain = new HashMap<>();
    private final Map<ChainTransactionId, AtomicChain<T>> childIdToChain = new HashMap<>();
    private final TreeSet<AtomicChain<T>> orderedChains;
    private final Map<Long, AtomicTransaction<T>> idToTransaction = new HashMap<>();

    /**
     * Construct a new set of atomic chains
     *
     * @param comparator Order of the chains.
     *              If null, the natural ordering of the {@link AtomicChain}s is used.
     *              This comparator must be consistent with equals
     */
    public AtomicChainsSet(Comparator<AtomicChain<T>> comparator) {
        this.orderedChains = new TreeSet<>(comparator.reversed());
    }

    public synchronized boolean add(T transaction) {
        AtomicTransaction<T> atx = new AtomicTransaction<>(transaction);
        if (txIdToChain.containsKey(atx.getId())) {
            //the transaction is already in the set
            return false;
        }
        if (childIdToChain.containsKey(atx.getChildId())) {
            //Another transactions with same child is already in the set
            logMerging(atx, childIdToChain.get(atx.getChildId()));
            return false;
        }

        AtomicChain<T> newChain = null;
        final AtomicChain<T> existingSuffix = txIdToChain.get(atx.getChildId());
        if (existingSuffix != null) {
            //the child is already in the set
            if (atx.getChildId().equals(existingSuffix.getRoot().getId())) {
                //we are the parent
                removeChain(existingSuffix);
                newChain = existingSuffix.addNewRoot(atx);
            } else {
                logMerging(atx, existingSuffix);
                return false;
            }
        }

        if (newChain == null) {
            newChain = new AtomicChain<>(atx);
        }

        final AtomicChain<T> existingPrefix = childIdToChain.get(atx.getId());
        if (existingPrefix != null) {
            if (atx.getId().equals(existingPrefix.getLeaf().getChildId())) {
                //we are the child
                removeChain(existingPrefix);
                newChain = existingPrefix.appendChildren(newChain);
            } else {
                logMerging(atx, existingPrefix);
                return false;
            }
        }
        addChain(newChain);
        return true;
    }

    public synchronized boolean remove(Long id) {
        AtomicTransaction<T> atomicTransaction = idToTransaction.get(id);
        if (atomicTransaction != null) {
            final AtomicChain<T> chain = txIdToChain.get(atomicTransaction.getId());
            if (chain != null) {
                removeChain(chain);
                AtomicChain<T>[] parts = chain.splitAtTransaction(
                        atomicTransaction.getId());
                if (parts[0] != null) {
                    addChain(parts[0]);
                }
                if (parts[1] != null) {
                    addChain(parts[1]);
                }
                return true;
            } else {
                Logger.logWarningMessage("No chain for " + atomicTransaction);
            }
        }
        return false;
    }

    private void addChain(AtomicChain<T> chain) {
        chain.getAtomicTransactions().forEach(at -> {
            txIdToChain.put(at.getId(), chain);
            idToTransaction.put(at.getTransaction().getId(), at);
            if (at.getChildId() != null) {
                childIdToChain.put(at.getChildId(), chain);
            }
        });
        if (!orderedChains.add(chain)) {
            throw new IllegalArgumentException(CHAIN_ALREADY_EXISTS_MESSAGE);
        }
    }

    private void removeChain(AtomicChain<T> chain) {
        chain.getAtomicTransactions().forEach(at -> {
            txIdToChain.remove(at.getId());
            idToTransaction.remove(at.getTransaction().getId());
            if (at.getChildId() != null) {
                childIdToChain.remove(at.getChildId());
            }
        });

        if (!orderedChains.remove(chain)) {
            throw new IllegalArgumentException(
                    "orderedChains is missing a chain provided");
        }
    }

    public synchronized AtomicChain<T> peekLastChain() {
        if (orderedChains.isEmpty()) {
            return null;
        }
        return orderedChains.last();
    }

    public synchronized AtomicChain<T> removeLastChain() {
        if (orderedChains.isEmpty()) {
            return null;
        }
        AtomicChain<T> removed = orderedChains.last();
        if (removed != null) {
            removeChain(removed);
        }
        return removed;
    }

    public synchronized T getTransaction(Long id) {
        AtomicTransaction<T> atomicTransaction = idToTransaction.get(id);
        return atomicTransaction == null ? null : atomicTransaction.getTransaction();
    }

    public synchronized boolean hasTransaction(Long id) {
        return idToTransaction.containsKey(id);
    }

    public synchronized int transactionsCount() {
        return txIdToChain.size();
    }

    public synchronized void clear() {
        orderedChains.clear();
        txIdToChain.clear();
        idToTransaction.clear();
        childIdToChain.clear();
    }

    public synchronized List<AtomicChain<T>> getCompleteChains() {
        return orderedChains.stream().filter(AtomicChain::isComplete).collect(Collectors.toList());
    }

    /**
     * Returns an unmodifiable collection containing all chains in the set.
     * Note that concurrent modifications to the set may lead to concurrency issues
     * with the returned collection. Ensure the set instance is locked during the
     * usage of the returned collection.
     *
     * @return an unmodifiable collection of chains
     */
    public Collection<AtomicChain<T>> getAllChains() {
        return Collections.unmodifiableCollection(orderedChains);
    }

    public synchronized ArrayList<T> toTransactionsList() {
        ArrayList<T> allTransactions = new ArrayList<>();
        orderedChains.forEach(chain ->
            chain.getAtomicTransactions().forEach(at ->
                allTransactions.add(at.getTransaction())
            )
        );
        return allTransactions;
    }

    private static <T extends Transaction> void logMerging(AtomicTransaction<T> at, AtomicChain<T> atomicChain) {
        Logger.logWarningMessage(String.format("The added transaction %s is merging into existing chain %s. Ignored",
                at, atomicChain));
    }

}
