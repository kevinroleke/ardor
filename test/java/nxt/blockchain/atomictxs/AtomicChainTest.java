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

package nxt.blockchain.atomictxs;

import nxt.blockchain.TestChildTransaction;
import nxt.blockchain.Transaction;
import org.junit.Test;

import static org.junit.Assert.*;


public class AtomicChainTest {

    @Test
    public void testSplitAtTransaction() {
        // Create some mock transactions
        TestChildTransaction tx1 = new TestChildTransaction("01", null);
        TestChildTransaction tx2 = new TestChildTransaction("02", tx1);
        TestChildTransaction tx3 = new TestChildTransaction("03", tx2);
        TestChildTransaction tx4 = new TestChildTransaction("04", tx3);

        AtomicChain<Transaction> chain = AtomicChain.of(tx1, tx2, tx3, tx4);

        AtomicChain<Transaction>[] result = chain.splitAtTransaction(tx2.getChainTxId());

        assertNotNull(result[0]);
        assertEquals(1, result[0].size());
        assertEquals(tx1, result[0].get(0));

        assertNotNull(result[1]);
        assertEquals(2, result[1].size());
        assertEquals(tx3, result[1].get(0));
        assertEquals(tx4, result[1].get(1));

        result = chain.splitAtTransaction(tx1.getChainTxId());
        assertNull(result[0]);

        assertNotNull(result[1]);
        assertEquals(3, result[1].size());
        assertEquals(tx2, result[1].get(0));
        assertEquals(tx3, result[1].get(1));
        assertEquals(tx4, result[1].get(2));

        result = chain.splitAtTransaction(tx4.getChainTxId());
        assertNull(result[1]);

        assertNotNull(result[0]);
        assertEquals(3, result[0].size());
        assertEquals(tx1, result[0].get(0));
        assertEquals(tx2, result[0].get(1));
        assertEquals(tx3, result[0].get(2));

        AtomicChain<Transaction> chain2 = new AtomicChain<>(new AtomicTransaction<>(tx1));
        result = chain2.splitAtTransaction(tx1.getChainTxId());
        assertNull(result[0]);
        assertNull(result[1]);
    }


}