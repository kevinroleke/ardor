/*
 * Copyright © 2021-2022 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */
package nxt.blockchain;

import nxt.BlockchainTest;
import nxt.http.callers.SendMoneyCall;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TransactionProcessorTest extends BlockchainTest {
    @Test
    public void testUnconfirmedTransactionsWaiting() {
        //process (discard) any waiting transactions created during initialization
        processWaitingTransactions();

        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId()).feeNQT(0).
                privateKey(ALICE.getPrivateKey()).recipient(BOB.getId());

        sendMoneyCall.amountNQT(10 * IGNIS.ONE_COIN).callNoError();

        assertTransactionsCounts(1, 0);

        generateBlock();

        assertTransactionsCounts(0, 1);

        int height = getHeight();

        sendMoneyCall.amountNQT(11 * IGNIS.ONE_COIN).callNoError();
        sendMoneyCall.amountNQT(12 * IGNIS.ONE_COIN).callNoError();
        sendMoneyCall.amountNQT(13 * IGNIS.ONE_COIN).callNoError();

        assertTransactionsCounts(3, 1);

        generateBlock();

        assertTransactionsCounts(0, 4);

        sendMoneyCall.amountNQT(14 * IGNIS.ONE_COIN).callNoError();

        assertTransactionsCounts(1, 4);

        processWaitingTransactions();

        assertTransactionsCounts(5, 0);

        popOffTo(height);

        //pop-off re-queues all unconfirmed transactions no matter of their height
        assertTransactionsCounts(0, 5);

        processWaitingTransactions();

        assertTransactionsCounts(5, 0);
    }

    public static void processWaitingTransactions() {
        TransactionProcessorImpl.getInstance().processWaitingTransactions();
    }

    private void assertTransactionsCounts(int unconfirmed, int waiting) {
        Assert.assertEquals(unconfirmed, TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactionIds().size());
        Assert.assertEquals(waiting, TransactionProcessorImpl.getInstance().getAllWaitingTransactions().length);
    }
}
