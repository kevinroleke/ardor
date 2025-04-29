/*
 * Copyright © 2024-2025 Jelurida Swiss SA
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

import nxt.blockchain.atomictxs.AtomicChainsSet;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;

public class UtxComparableDataTest {
    public static class MockTransaction implements DummyTransaction {
        private final long id;
        private final int height;
        private final long fee;
        private final short deadline;
        private final Chain chain;
        private final TransactionType type;

        public MockTransaction(long id, int height, long fee, short deadline,
                               Chain chain, TransactionType type) {
            this.id = id;
            this.height = height;
            this.fee = fee;
            this.deadline = deadline;
            this.chain = chain;
            this.type = type;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public long getFee() {
            return fee;
        }

        @Override
        public int getFullSize() {
            return 100;
        }

        @Override
        public short getDeadline() {
            return deadline;
        }

        @Override
        public Chain getChain() {
            return chain;
        }

        @Override
        public TransactionType getType() {
            return type;
        }
    }

    private static class MockChildTransaction extends MockTransaction implements DummyChildTransaction {


        public MockChildTransaction(long id, int height, long fee,
                                    short deadline, Chain chain,
                                    TransactionType type) {
            super(id, height, fee, deadline, chain, type);
        }

        @Override
        public ChildChain getChain() {
            return (ChildChain) super.getChain();
        }

    }


    private UtxComparableData createComparableData(boolean isChildBlock,
                                                   boolean isFxtChain, int height,
                                                   boolean isBundled,
                                                   boolean isCompleteAtomicChain,
                                                   long fee, short deadline,
                                                   long arrivalTimestamp,
                                                   long id) {

        Transaction mockedTransaction;
        Chain chain = isChildBlock ? FxtChain.FXT : ChildChain.IGNIS;
        TransactionType type = isFxtChain ? ChildBlockFxtTransactionType.INSTANCE : null;

        if (isChildBlock) {
            mockedTransaction = new MockTransaction(id, height, fee, deadline,
                    chain, type);
        } else {
            mockedTransaction = new MockChildTransaction(id, height, fee,
                    deadline, chain, type);
        }
        UtxComparableData comparableData = new UtxComparableData(
                mockedTransaction, isBundled, isCompleteAtomicChain, arrivalTimestamp,
                mockedTransaction.getFee(), mockedTransaction.getFullSize());
        Assert.assertTrue(comparableData.compareTo(UtxComparableData.HIGHEST) < 0);
        return comparableData;
    }

    private UtxComparableData createComparableData(long fee) {
        return createComparableData(false, true, 100, false,
                false, fee * 1000, (short) 1000, 1000, 1);
    }

    @Test
    public void testChildBlockPriority() {
        UtxComparableData cd1 = createComparableData(true, true, 100, false, false,
                1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testFxtChainPriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, false, 100, false,
                false, 1000, (short) 1, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testHeightPriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 200, false,
                false, 1000, (short) 1, 1000, 2);

        // Transactions that were included in a block are before the rest.
        // The lower is the block height they were included in, the higher the priority
        assertOrdering(cd1, cd2);
    }

    @Test
    public void testFeePriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 2000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testDeadlinePriorityWhenFeeIsZero() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 0, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 0, (short) 2, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testFeeToDeadlineRatio() {
        //If you pay 2 times more fee than someone else you should have the priority
        // even if your deadline is almost 2 times higher than his
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 2 * ChildChain.IGNIS.ONE_COIN, (short) (1440 * 2 - 1), 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, ChildChain.IGNIS.ONE_COIN, (short) 1440, 1000, 2);

        assertOrdering(cd1, cd2);

        //If you pay some fee you should be better than someone who pays dust even
        // if his deadline is minor and yours is significant
        cd1 = createComparableData(false, true, 100, false,
                false, ChildChain.IGNIS.ONE_COIN /100, (short) 14400, 1000, 1);
        cd2 = createComparableData(false, true, 100, false,
                false,  20, (short) 1,1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testBundledPriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, true, false,
                1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testCompleteAtomicChainPriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                true, 1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testArrivalTimestampPriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 2000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testIdPriority() {
        UtxComparableData cd1 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 1);
        UtxComparableData cd2 = createComparableData(false, true, 100, false,
                false, 1000, (short) 1, 1000, 2);

        assertOrdering(cd1, cd2);
    }

    @Test
    public void testFilterCollection() {
        List<UtxComparableData> input = new ArrayList<>();
        for (int i = 10; i < 20; i++) {
            input.add(createComparableData(i));
        }

        List<BiConsumer<UtxComparableData, UtxComparableData>> consumers = new ArrayList<>();
        consumers.add(null);
        consumers.add((k, v) -> {});

        //For threshold lower than the minimal element all should be accepted even if
        for (BiConsumer<UtxComparableData, UtxComparableData> consumer : consumers) {

            Assert.assertEquals(expectedTo(10),
                    UtxComparableData.filterWithLimits(
                            input.stream(), t -> t, 3, 10,
                            createComparableData(1), consumer));

            //same as null threshold
            Assert.assertEquals(expectedTo(12),
                    UtxComparableData.filterWithLimits(
                            input.stream(), t -> t, 3, 8,
                            createComparableData(1), consumer));

            //Everything up to the threshold, excluded
            Assert.assertEquals(expectedTo(16),
                    UtxComparableData.filterWithLimits(
                            input.stream(), t -> t, 3, 10,
                            createComparableData(15), consumer));

            // Satisfy the minSize even after the threshold is reached
            Assert.assertEquals(expectedTo(14),
                    UtxComparableData.filterWithLimits(
                            input.stream(), t -> t, 6, 10,
                            createComparableData(15), consumer));

            // Satisfy the maxSize before the threshold is reached
            Assert.assertEquals(expectedTo(17),
                    UtxComparableData.filterWithLimits(
                            input.stream(), t -> t, 3, 3,
                            createComparableData(15), consumer));
        }
    }

    /**
     * From 19 back to 'to', including 'to'
     * @param to limit
     * @return List
     */
    private List<UtxComparableData> expectedTo(int to) {
        List<UtxComparableData> expected = new ArrayList<>();
        for (int i = 19; i >= to; i--) {
            expected.add(createComparableData(i));
        }
        return expected;
    }

    private void assertOrdering(UtxComparableData higherPriority,
                                UtxComparableData lowerPriority) {
        // Test with PriorityQueue
        PriorityQueue<UtxComparableData> priorityQueue = new PriorityQueue<>();
        priorityQueue.add(lowerPriority);
        priorityQueue.add(higherPriority);

        //lower priority must be removed first
        assertEquals(lowerPriority, priorityQueue.remove());
        assertEquals(higherPriority, priorityQueue.remove());

        // Test with TreeSet
        TreeSet<UtxComparableData> treeSet = new TreeSet<>(
                new Comparator<UtxComparableData>() {
                    @Override
                    public int compare(UtxComparableData o1, UtxComparableData o2) {
                        return o2.compareTo(o1);
                    }
                });

        treeSet.add(lowerPriority);
        treeSet.add(higherPriority);

        Iterator<UtxComparableData> iterator = treeSet.iterator();
        assertEquals(higherPriority, iterator.next());
        assertEquals(lowerPriority, iterator.next());
    }

}
