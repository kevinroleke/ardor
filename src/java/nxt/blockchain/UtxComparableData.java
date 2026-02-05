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
public final class UtxComparableData implements Comparable<UtxComparableData> {

    public static final UtxComparableData HIGHEST = new UtxComparableData(true,
            true, Integer.MIN_VALUE, true,
            true,
            Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);

    private static final long RETENTION_SCORE_UNIT = 10_000_000_000L;
    final boolean isChildBlock;
    final boolean isFxtChain;
    private final int height;
    private final boolean isBundled;
    final boolean isCompleteAtomicChain;
    final long poolRetentionScore;
    private final long arrivalTimestamp;
    private final long id;

    UtxComparableData(boolean isChildBlock, boolean isFxtChain,
                             int height,
                             boolean isBundled,
                             boolean isCompleteAtomicChain,
                             long poolRetentionScore, long arrivalTimestamp,
                             long id) {
        this.isChildBlock = isChildBlock;
        this.isFxtChain = isFxtChain;
        this.height = height;
        this.isBundled = isBundled;
        this.isCompleteAtomicChain = isCompleteAtomicChain;
        this.poolRetentionScore = poolRetentionScore;
        this.arrivalTimestamp = arrivalTimestamp;
        this.id = id;
    }

    public UtxComparableData(Transaction transaction, boolean isBundled, boolean isCompleteAtomicChain,
                      long arrivalTimestamp, long fee, int fullSize) {
        this.isChildBlock = transaction.getType() == ChildBlockFxtTransactionType.INSTANCE;
        this.isFxtChain = transaction.getChain() == FxtChain.FXT;
        this.height = transaction.getHeight();
        this.poolRetentionScore = calculatePoolRetentionScore(
                transaction.getChain(), fee, transaction.getDeadline(),
                fullSize);
        this.isBundled = isBundled;
        this.isCompleteAtomicChain = isCompleteAtomicChain;
        this.arrivalTimestamp = arrivalTimestamp;
        this.id = transaction.getId();
    }

    @Override
    public int compareTo(UtxComparableData that) {
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
        if ((result = Boolean.compare(this.isCompleteAtomicChain, that.isCompleteAtomicChain)) != 0) {
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
                && isCompleteAtomicChain == that.isCompleteAtomicChain
                && poolRetentionScore == that.poolRetentionScore
                && arrivalTimestamp == that.arrivalTimestamp
                && id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isChildBlock, isFxtChain, height, isBundled,
                isCompleteAtomicChain, poolRetentionScore, arrivalTimestamp,
                id);
    }

    @Override
    public String toString() {
        return "UtxComparableData{" +
                "isChildBlock=" + isChildBlock +
                ", isFxtChain=" + isFxtChain +
                ", height=" + height +
                ", isBundled=" + isBundled +
                ", isCompleteAtomicChain=" + isCompleteAtomicChain +
                ", poolRetentionScore=" + poolRetentionScore +
                ", arrivalTimestamp=" + arrivalTimestamp +
                ", id=" + id +
                '}';
    }

    public UtxComparableData cloneIgnoringTimeAndId(long newArrivalTimestamp, long newId) {
        return new UtxComparableData(isChildBlock, isFxtChain, height,
                isBundled, isCompleteAtomicChain,
                poolRetentionScore, newArrivalTimestamp, newId);
    }

    private long calculatePoolRetentionScore(Chain chain, long fee,
                                             short deadline, int byteSize) {
        if (fee == 0) {
            //At 0 fee the score gets negative and the longer is the deadline
            // the more unwanted is the transaction
            return -deadline * RETENTION_SCORE_UNIT;
        }

        //For each whole coin paid as fee, get 1 unit of retention score.
        BigInteger score = BigInteger.valueOf(fee)
                .multiply(BigInteger.valueOf(RETENTION_SCORE_UNIT))
                .divide(BigInteger.valueOf(byteSize))
                .divide(BigInteger.valueOf(chain.ONE_COIN));

        //The final score is inversely proportional to the deadline.
        score = score.divide(BigInteger.valueOf(deadline));
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
