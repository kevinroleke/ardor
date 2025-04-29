package nxt.blockchain.atomictxs;

import nxt.blockchain.ChildTransaction;
import nxt.blockchain.TestChildTransaction;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AtomicChainsSetTest {

//    public static final Comparator<AtomicChain<ChildTransaction>> COMPARING_BY_COMPLETE_FEE_HASH = Comparator.comparing(
//                    AtomicChain<ChildTransaction>::isComplete).reversed()
//            .thenComparing(Comparator.comparingLong(AtomicChain<ChildTransaction>::getFeePerByte).reversed())
//            .thenComparing(AtomicChain.FULL_HASH_COMPARATOR);

    public static final Comparator<AtomicChain<ChildTransaction>> COMPARING_BY_COMPARABLE_DATA = Comparator.comparing(
                    AtomicChain::getComparableData);

    private ChildTransaction[] createTestChain(int size) {
        ChildTransaction[] result = new ChildTransaction[size];
        result[0] = new TestChildTransaction("00c11d", null);
        for (int i = 1; i < size - 1; i++) {
            result[i] = new TestChildTransaction(String.format("%02dc11d", i), result[i - 1]);
        }
        result[result.length - 1] = new TestChildTransaction("ffd00dff", result[result.length - 2], false, 0,
                15);
        return result;
    }

    @Test
    public void testChainAdditionWithConcatenation() {
        AtomicChainsSet<ChildTransaction> set = new AtomicChainsSet<>(COMPARING_BY_COMPARABLE_DATA);

        ChildTransaction[] tc = createTestChain(6);

        // Add the 3 child-most transactions. The root is tc[3]
        set.add(tc[0]);
        set.add(tc[2]);
        set.add(tc[1]);

        //adding the same transaction second time fails when the chain is incomplete
        Assert.assertFalse(set.add(tc[2]));
        Assert.assertFalse(set.add(tc[1]));

        List<AtomicChain<ChildTransaction>> chains = set.getCompleteChains();
        assertEquals(0, chains.size());

        // Add the 3 transactions at the beginning of the chain starting from the root. The already added tc[0-2]
        // should be concatenated to the end of the chain
        set.add(tc[4]);
        set.add(tc[3]);
        set.add(tc[5]);

        // Verify the chains
        chains = set.getCompleteChains();
        assertEquals(1, chains.size());

        AtomicChain<ChildTransaction> chain = chains.get(0);

        assertEquals(Arrays.asList(tc), new ArrayList<>(chain));

        //Adding the same transaction second time fails when the chain is complete
        Assert.assertFalse(set.add(tc[4]));
    }

    @Test
    public void testMergingChains() {
        ChildTransaction[] tc = createTestChain(4);

        ChildTransaction tx21 = new TestChildTransaction("21", tc[1]);
        ChildTransaction tx31 = new TestChildTransaction("31", tx21, false, 0,
                15);

        AtomicChainsSet<ChildTransaction> set = new AtomicChainsSet<>(COMPARING_BY_COMPARABLE_DATA);
        //Incomplete chain 0-1-2
        Assert.assertTrue(set.add(tc[2]));
        Assert.assertTrue(set.add(tc[0]));
        Assert.assertTrue(set.add(tc[1]));

        //Try to add the other chain 21-31 which merges into the first chain at tc[1]
        Assert.assertTrue(set.add(tx31));
        Assert.assertFalse(set.add(tx21));

        //complete the first chain
        set.add(tc[3]);

        //Try to add 41 when the chain is complete - should fail again
        Assert.assertFalse(set.add(tx31));

        //The second chain is not added
        List<AtomicChain<ChildTransaction>> chains = set.getCompleteChains();
        assertEquals(1, chains.size());

        assertEquals(Arrays.asList(tc), new ArrayList<>(chains.get(0)));
    }

    @Test
    public void testSorting() {
        //2: 3000 per byte
        ChildTransaction tx6 = new TestChildTransaction("6",null, true, 300_000,
                15);
        ChildTransaction tx5 = new TestChildTransaction("5", tx6, false, 300_000,
                15);

        //4: 3150 per byte
        ChildTransaction tx4 = new TestChildTransaction("4",null, false, 315_000,
                15);

        //1: 2900 per byte
        ChildTransaction tx3 = new TestChildTransaction("3",null, false, 290_000,
                15);

        //3: 3100 per byte
        ChildTransaction tx2 = new TestChildTransaction("2",null, true, 315_000,
                15);
        ChildTransaction tx1 = new TestChildTransaction("1",tx2, false, 305_000,
                15);

        AtomicChainsSet<ChildTransaction> set = new AtomicChainsSet<>(COMPARING_BY_COMPARABLE_DATA);
        set.add(tx1);
        Assert.assertEquals(Collections.singletonList(tx1), set.toTransactionsList());;
        set.add(tx2);
        //children in atomic chains are returned first
        Assert.assertEquals(Arrays.asList(tx2, tx1), set.toTransactionsList());
        set.add(tx5);
        //transactions of incomplete chains are in the end
        Assert.assertEquals(Arrays.asList(tx2, tx1, tx5), set.toTransactionsList());
        set.add(tx6);
        Assert.assertEquals(Arrays.asList(tx2, tx1, tx6, tx5), set.toTransactionsList());
        set.add(tx3);
        set.add(tx4);

        Assert.assertEquals(Arrays.asList(tx4, tx2, tx1, tx6, tx5, tx3), set.toTransactionsList());

        List<AtomicChain<ChildTransaction>> chains = set.getCompleteChains();

        List<AtomicChain<ChildTransaction>> expectedChains = Arrays.asList(
                AtomicChain.of(tx4),
                AtomicChain.of(tx2, tx1),
                AtomicChain.of(tx6, tx5),
                AtomicChain.of(tx3));

        assertEquals(expectedChains, chains);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testComparatorInconsistentWithEquals() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(AtomicChainsSet.CHAIN_ALREADY_EXISTS_MESSAGE);

        //1: 3000 per byte
        ChildTransaction tx1 = new TestChildTransaction("1",null, true, 300_000,
                15);
        ChildTransaction tx2 = new TestChildTransaction("2", tx1, false, 300_000,
                15);

        //2: again 3000 per byte
        ChildTransaction tx3 = new TestChildTransaction("3",null, false, 300_000,
                15);

        //A comparator that compares only by fee per byte
        AtomicChainsSet<ChildTransaction> set = new AtomicChainsSet<>(
                Comparator.comparingLong(AtomicChain<ChildTransaction>::getFeePerByte));

        set.add(tx1);
        set.add(tx2);
        set.add(tx3);
    }

    @Test
    public void testRemovals() {
        ChildTransaction[] tc = createTestChain(4);

        ChildTransaction tx1 = new TestChildTransaction("01", null);
        ChildTransaction tx2 = new TestChildTransaction("02", tx1, false, 1000,
                15);

        AtomicChainsSet<ChildTransaction> set = new AtomicChainsSet<>(
                COMPARING_BY_COMPARABLE_DATA);
        set.add(tx2);
        set.add(tx1);

        for (ChildTransaction childTransaction : tc) {
            set.add(childTransaction);
        }
        Assert.assertEquals(Arrays.asList(tx1, tx2, tc[0], tc[1], tc[2], tc[3]), set.toTransactionsList());

        Assert.assertTrue(set.remove(tx2.getId()));
        Assert.assertEquals(5, set.transactionsCount());
        //the remains from the destroyed chains go to the end.
        Assert.assertEquals(Arrays.asList(tc[0], tc[1], tc[2], tc[3], tx1), set.toTransactionsList());
        Assert.assertFalse(set.hasTransaction(tx2.getId()));
        Assert.assertFalse(set.remove(tx2.getId()));

        Assert.assertTrue(set.add(tx2));

        Assert.assertTrue(set.remove(tx2.getId()));
        Assert.assertTrue(set.remove(tx1.getId()));
        Assert.assertEquals(4, set.transactionsCount());
        Assert.assertEquals(Arrays.asList(tc), set.toTransactionsList());

        Assert.assertFalse(set.hasTransaction(tx2.getId()));
        Assert.assertFalse(set.remove(tx2.getId()));

        Assert.assertFalse(set.hasTransaction(tx1.getId()));
        Assert.assertFalse(set.remove(tx1.getId()));

        Assert.assertTrue(set.add(tx2));
        Assert.assertTrue(set.add(tx1));

        Assert.assertTrue(set.remove(tx1.getId()));
        Assert.assertTrue(set.remove(tc[2].getId()));
        Assert.assertEquals(4, set.transactionsCount());
        //All transactions that remain are pending now - both chains were decomposed
        Assert.assertEquals(Arrays.asList(tx2, tc[0], tc[1], tc[3]), set.toTransactionsList());

        //recover one of the chains
        Assert.assertTrue(set.add(tc[2]));
        Assert.assertEquals(5, set.transactionsCount());
        Assert.assertEquals(Arrays.asList(tc[0], tc[1], tc[2], tc[3], tx2), set.toTransactionsList());
    }

    @Test
    public void testRemoveLastTransaction() {
        ChildTransaction tx6 = new TestChildTransaction("6", null);
        ChildTransaction tx4 = new TestChildTransaction("4", tx6);
        ChildTransaction tx5 = new TestChildTransaction("5", tx4);
        ChildTransaction tx3 = new TestChildTransaction("3", tx5, false, 0, 15);

        ChildTransaction tx2 = new TestChildTransaction("2", null);
        ChildTransaction tx1 = new TestChildTransaction("1", tx2, false, 1000, 15);

        AtomicChainsSet<ChildTransaction> set = new AtomicChainsSet<>(
                COMPARING_BY_COMPARABLE_DATA);
        Assert.assertNull(set.removeLastChain());
        set.add(tx1);
        set.add(tx2);
        set.add(tx3);
        set.add(tx4);
        set.add(tx5);
        set.add(tx6);
        Assert.assertEquals(Arrays.asList(tx2, tx1, tx6, tx4, tx5, tx3), set.toTransactionsList());

        Assert.assertEquals(tx3, set.peekLastChain().getRoot().getTransaction());
        set.removeLastChain();
        Assert.assertEquals(Arrays.asList(tx2, tx1), set.toTransactionsList());

        Assert.assertEquals(tx1, set.peekLastChain().getRoot().getTransaction());
        set.removeLastChain();
        Assert.assertEquals(Collections.emptyList(), set.toTransactionsList());
    }

}