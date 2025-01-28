/*
 * Copyright © 2025 Jelurida Swiss SA
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents the data which is used to prioritize {@link UnconfirmedTransaction}s
 * in the unconfirmed pool.
 */
final class UtxComparableData implements Comparable<UtxComparableData> {

    public static final UtxComparableData HIGHEST = new UtxComparableData(true,
            true, Integer.MIN_VALUE, true,
            false, // FIXME if having a reference transactions is prioritizing
            Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);

    private static final long RETENTION_SCORE_UNIT = 100000000L;
    final boolean isChildBlock;
    final boolean isFxtChain;
    private final int height;
    private final boolean isBundled;
    final boolean hasReferencedTransaction;
    final long poolRetentionScore;
    private final long arrivalTimestamp;
    private final long id;

    UtxComparableData(boolean isChildBlock, boolean isFxtChain,
                             int height,
                             boolean isBundled,
                             boolean hasReferencedTransaction,
                             long poolRetentionScore, long arrivalTimestamp,
                             long id) {
        this.isChildBlock = isChildBlock;
        this.isFxtChain = isFxtChain;
        this.height = height;
        this.isBundled = isBundled;
        this.hasReferencedTransaction = hasReferencedTransaction;
        this.poolRetentionScore = poolRetentionScore;
        this.arrivalTimestamp = arrivalTimestamp;
        this.id = id;
    }

    UtxComparableData(Transaction transaction, boolean isBundled,
                      long arrivalTimestamp) {
        isChildBlock = transaction.getType() == ChildBlockFxtTransactionType.INSTANCE;
        isFxtChain = transaction.getChain() == FxtChain.FXT;
        height = transaction.getHeight();
        poolRetentionScore = calculatePoolRetentionScore(transaction);
        this.isBundled = isBundled;
        if (isFxtChain) {
            hasReferencedTransaction = false;
        } else {
            hasReferencedTransaction = ((ChildTransaction) transaction).getReferencedTransactionId() != null;
        }

        this.arrivalTimestamp = arrivalTimestamp;
        id = transaction.getId();
    }

    UtxComparableData(UnconfirmedTransaction utx) {
        this(utx.getTransaction(), utx.isBundled(), utx.getArrivalTimestamp());
    }

    @Override
    public int compareTo(UtxComparableData that) {
        /*
        The original comparator:
        (UnconfirmedTransaction o1, UnconfirmedTransaction o2) -> {
            int result;
            if ((result = Boolean.compare(o1.getType() == ChildBlockFxtTransactionType.INSTANCE,
                    o2.getType() == ChildBlockFxtTransactionType.INSTANCE)) != 0) {
                return result;
            }
            if ((result = Boolean.compare(o2.getChain() != FxtChain.FXT, o1.getChain() != FxtChain.FXT)) != 0) {
                return result;
            }
            if ((result = Integer.compare(o2.getHeight(), o1.getHeight())) != 0) {
                return result;
            }
            if (o1.getChain() == FxtChain.FXT && o2.getChain() == FxtChain.FXT) {
                if ((result = Long.compare(o1.getFee(), o2.getFee())) != 0) {
                    return result;
                }
            }
            if ((result = Boolean.compare(o1.isBundled(), o2.isBundled())) != 0) {
                return result;
            }
            if ((result = Boolean.compare(o2.getReferencedTransactionId() != null,
                    o1.getReferencedTransactionId() != null)) != 0) {
                return result;
            }
            if ((result = Long.compare(o2.getArrivalTimestamp(), o1.getArrivalTimestamp())) != 0) {
                return result;
            }
            return Long.compare(o2.getId(), o1.getId());
        }
         */
        int result;
        if ((result = Boolean.compare(this.isChildBlock, that.isChildBlock)) != 0) {
            return result;
        }
        if ((result = Boolean.compare(this.isFxtChain, that.isFxtChain)) != 0) {
            return result;
        }
        // Transactions that were included in a block are ordered before the
        // rest. The lower is the block height they were included in, the
        // higher the priority
        if ((result = Integer.compare(that.height, this.height)) != 0) {
            return result;
        }
        if ((result = Boolean.compare(this.isBundled, that.isBundled)) != 0) {
            return result;
        }

        //TODO do we want to prioritize transactions that reference other transactions
        // or do we want to deprioritize them? In previous code they were deprioritized:
        //            if ((result = Boolean.compare(o2.getReferencedTransactionId() != null,
        //                    o1.getReferencedTransactionId() != null)) != 0) {
        //                return result;
        //            }
        // If o1 references and o2 does not, then Boolean.compare(false, true)
        // returns less than 0, effectively positioning o2 before o1
        // I keep this logic, but I'm not confident if this was intentional or a bug
        if ((result = Boolean.compare(that.hasReferencedTransaction, this.hasReferencedTransaction)) != 0) {
            return result;
        }
        if ((result = Long.compare(this.poolRetentionScore, that.poolRetentionScore)) != 0) {
            return result;
        }
        if ((result = Long.compare(that.arrivalTimestamp, this.arrivalTimestamp)) != 0) {
            return result;
        }
        return Long.compare(that.id, this.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UtxComparableData)) return false;
        UtxComparableData that = (UtxComparableData) o;
        return isChildBlock == that.isChildBlock
                && isFxtChain == that.isFxtChain
                && height == that.height
                && isBundled == that.isBundled
                && hasReferencedTransaction == that.hasReferencedTransaction
                && poolRetentionScore == that.poolRetentionScore
                && arrivalTimestamp == that.arrivalTimestamp
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isChildBlock, isFxtChain, height, isBundled,
                hasReferencedTransaction, poolRetentionScore, arrivalTimestamp,
                id);
    }

    @Override
    public String toString() {
        return "UtxComparableData{" +
                "isChildBlock=" + isChildBlock +
                ", isFxtChain=" + isFxtChain +
                ", height=" + height +
                ", isBundled=" + isBundled +
                ", hasReferencedTransaction=" + hasReferencedTransaction +
                ", poolRetentionScore=" + poolRetentionScore +
                ", arrivalTimestamp=" + arrivalTimestamp +
                ", id=" + id +
                '}';
    }

    public UtxComparableData cloneIgnoringTimeAndId(long newArrivalTimestamp, long newId) {
        return new UtxComparableData(isChildBlock, isFxtChain, height,
                isBundled, hasReferencedTransaction,
                poolRetentionScore, newArrivalTimestamp, newId);
    }

    private long calculatePoolRetentionScore(Transaction transaction) {
        if (transaction.getFee() == 0) {
            //At 0 fee the score gets negative and the longer is the deadline
            // the more unwanted is the transaction
            return -transaction.getDeadline() * RETENTION_SCORE_UNIT;
        }

        //For each whole coin paid as fee, get 1 unit of retention score.
        BigInteger score = BigInteger.valueOf(transaction.getFee())
                .multiply(BigInteger.valueOf(RETENTION_SCORE_UNIT))
                .divide(BigInteger.valueOf(transaction.getChain().ONE_COIN));

        //The final score is inversely proportional to the deadline.
        score = score.divide(BigInteger.valueOf(transaction.getDeadline()));
        if (score.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        return score.longValue();
    }

    /**
     * This method obtains a `UtxComparableData` object for each element in the
     * stream using the specified <code>mapper</code>. It then sorts the elements based on the
     * `UtxComparableData`. After sorting, elements are removed from the end of
     * the sorted list until the list size is reduced to either the <code>retentionThreshold</code>
     * or <code>minSize</code>, whichever is larger. The list size is also limited by <code>maxSize</code>.
     * The list is returned as result.
     *
     * @param stream Stream to sort and filter
     * @param mapper Mapper returning the {@link UtxComparableData} of the elements
     * @param minSize The minimum size of the returned array. If the collection
     *                size is smaller, this value is capped to the collection size
     * @param maxSize The maximum size of the returned array
     * @param retentionThreshold Elements bigger than this threshold will always
     *                          be retained. If null, only the maxSize is respected
     * @param rejectedElementConsumer If not null the rejected elements will be
     *                                provided to it
     * @return List with the result
     * @param <T> Type of the stream elements
     */
    public static <T> List<T> filterWithLimits(Stream<T> stream,
                                               Function<T, UtxComparableData> mapper,
                                               int minSize, int maxSize,
                                               UtxComparableData retentionThreshold,
                                               BiConsumer<T, UtxComparableData> rejectedElementConsumer) {
        TreeMap<UtxComparableData, T> sortedMap = new TreeMap<>(Comparator.reverseOrder());

        stream.forEach(e -> {
            UtxComparableData data = mapper.apply(e);
            sortedMap.put(data, e);
        });

        minSize = Math.min(minSize, sortedMap.size());

        // Determine the number of elements to retain
        int retainSize = 0;
        if (retentionThreshold != null) {
            for (UtxComparableData data : sortedMap.keySet()) {
                if (retainSize >= maxSize) {
                    break;
                }
                if (data.compareTo(retentionThreshold) <= 0) {
                    break;
                }
                retainSize++;
            }
        } else {
            retainSize = Math.min(retainSize, maxSize);
        }
        retainSize = Math.max(minSize, retainSize);

        ArrayList<T> result = new ArrayList<>();
        if (rejectedElementConsumer == null) {
            //optimization: don't iterate the whole set if we don't need the rejected elements
            for (T element : sortedMap.values()) {
                if (result.size() >= retainSize) {
                    break;
                }
                result.add(element);
            }
        } else {
            for (Map.Entry<UtxComparableData, T> entry : sortedMap.entrySet()) {
                if (result.size() < retainSize) {
                    result.add(entry.getValue());
                } else {
                    rejectedElementConsumer.accept(entry.getValue(), entry.getKey());
                }
            }
        }
        return result;
    }
}
