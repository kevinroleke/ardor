/*
 * Copyright © 2025-2026 Jelurida Swiss SA
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

import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

/**
 * The `DroppedTransactionStore` class handles a collection of {@link UtxComparableData comparable data}
 * for transactions that have been dropped. It offers functionality to add,
 * retrieve, and clean up entries once the transactions have expired.
 *
 * <p>This class uses a concurrent map to store known dropped transactions' data and a priority queue to manage
 * its expiration. The priority queue ensures that expired data is cleaned up</p>
 *
 * <p>The store has a maximum size limit, and if the number of transactions exceeds this limit, the oldest
 * transactions (those closest to expiration) are removed during cleanup.</p>
 */

public class DroppedTransactionStore {

    private final Map<Long, Entry> knownDroppedTransactions = new ConcurrentHashMap<>();
    private final PriorityQueue<Entry> expirationQueue =
            new PriorityQueue<>(Comparator.comparingLong(Entry::getExpiration));

    private final long maxSize;

    DroppedTransactionStore(long maxSize) {
        this.maxSize = maxSize;
    }

    void add(Long id, UtxComparableData data, int expirationTime) {
        Entry entry = new Entry(id, data, expirationTime);
        if (knownDroppedTransactions.putIfAbsent(id, entry) == null) {
            synchronized (expirationQueue) {
                expirationQueue.offer(entry);
            }
        }
    }

    UtxComparableData get(Long id) {
        Entry result = knownDroppedTransactions.get(id);
        return result == null ? null : result.toComparableData();
    }

    /**
     * Cleanup any entries that expired. If the size is bigger than the maxSize
     * proveded when the store is created, remove the entries that will expire
     * sooner
     *
     * @param currentTime timestamp in same format and resolution as the 
     *                    expiration provided to {@link #add(Long, UtxComparableData, int)}
     */
    public void cleanup(int currentTime) {
        while (true) {
            Entry entry;
            synchronized (expirationQueue) {
                entry = expirationQueue.peek();
                if (entry == null
                        || currentTime < entry.expiration && expirationQueue.size() <= maxSize) {
                    break;
                }
                expirationQueue.poll();
            }
            knownDroppedTransactions.remove(entry.id);
        }
    }

    public int getSize() {
        return expirationQueue.size();
    }

    private static class Entry {
        private final Long id;
        private final boolean isChildBlock;
        private final boolean isFxtChain;
        private final long poolRetentionScore;
        private final int expiration;

        public Entry(Long id, UtxComparableData comparableData,
                     int expiration) {
            this.id = id;
            this.isChildBlock = comparableData.isChildBlock;
            this.isFxtChain = comparableData.isFxtChain;
            this.poolRetentionScore = comparableData.poolRetentionScore;
            this.expiration = expiration;
        }

        public UtxComparableData toComparableData() {
            // Treat dropped transactions as least appealing as possible
            return new UtxComparableData(
                    isChildBlock,
                    isFxtChain,
                    Integer.MAX_VALUE,
                    false,
                    false,
                    poolRetentionScore,
                    Long.MAX_VALUE,
                    Long.MAX_VALUE);
        }

        public int getExpiration() {
            return expiration;
        }
    }

}
