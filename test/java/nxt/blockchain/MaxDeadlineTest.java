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

import org.junit.Test;

import static org.junit.Assert.*;

public class MaxDeadlineTest {

    static class TestChildTransaction implements DummyChildTransaction {
        private final long fee;
        private final ChildChain chain;

        public TestChildTransaction(long fee, ChildChain chain) {
            this.fee = fee;
            this.chain = chain;
        }

        @Override
        public long getFee() {
            return fee;
        }

        @Override
        public ChildChain getChain() {
            return chain;
        }
    }

    @Test
    public void testGetMaxDeadline() {
        TestChildTransaction transaction = new TestChildTransaction(0, ChildChain.IGNIS);
        short maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
        assertEquals(15, maxDeadline);

        transaction = new TestChildTransaction(ChildChain.IGNIS.ONE_COIN / 100, ChildChain.IGNIS);
        maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
        assertEquals(1440, maxDeadline);

        transaction = new TestChildTransaction(2 * ChildChain.IGNIS.ONE_COIN / 100, ChildChain.IGNIS);
        maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
        assertEquals(2865, maxDeadline);

        transaction = new TestChildTransaction(ChildChain.IGNIS.ONE_COIN, ChildChain.IGNIS);
        maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
        assertEquals(Short.MAX_VALUE, maxDeadline);

        transaction = new TestChildTransaction(ChildChain.AEUR.ONE_COIN / 100, ChildChain.AEUR);
        maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
        assertEquals(1440, maxDeadline);

        transaction = new TestChildTransaction(0, ChildChain.AEUR);
        maxDeadline = ChildTransactionImpl.getMaxDeadline(transaction);
        assertEquals(15, maxDeadline);
    }


}