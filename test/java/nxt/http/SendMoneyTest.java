/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2022 Jelurida IP B.V.
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

package nxt.http;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.blockchain.FxtChain;
import nxt.blockchain.TransactionProcessorImpl;
import nxt.blockchain.TransactionProcessorTest;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.GetTransactionCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SignTransactionCall;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.AEUR;
import static nxt.blockchain.ChildChain.IGNIS;

public class SendMoneyTest extends BlockchainTest {

    @Test
    public void sendMoney() {
        JO response = SendMoneyCall.create(FxtChain.FXT.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * FxtChain.FXT.ONE_COIN).
                feeNQT(FxtChain.FXT.ONE_COIN * 10).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forger
        Assert.assertEquals(0, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(0, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtUnconfirmedBalanceDiff());
        generateBlock();
        // Forger
        Assert.assertEquals(10 * FxtChain.FXT.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(10 * FxtChain.FXT.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * FxtChain.FXT.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(100 * FxtChain.FXT.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void sendTooMuchMoney() {
        JO response = SendMoneyCall.create(FxtChain.FXT.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(ALICE.getInitialFxtBalance()).
                feeNQT(10 * FxtChain.FXT.ONE_COIN).
                call();
        Logger.logDebugMessage("sendMoney: " + response);
        Assert.assertEquals(6, response.getLong("errorCode"));
    }

    @Test
    public void sendAndReturn() {
        JO response = SendMoneyCall.create(FxtChain.FXT.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * FxtChain.FXT.ONE_COIN).
                feeNQT(10 * FxtChain.FXT.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney1: " + response);
        response = SendMoneyCall.create(FxtChain.FXT.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                recipient(ALICE.getStrId()).
                amountNQT(100 * FxtChain.FXT.ONE_COIN).
                feeNQT(10 * FxtChain.FXT.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney2: " + response);
        // Forger
        Assert.assertEquals(0, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(0, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
        generateBlock();
        // Forger
        Assert.assertEquals(20* FxtChain.FXT.ONE_COIN, FORGY.getFxtBalanceDiff());
        Assert.assertEquals(20* FxtChain.FXT.ONE_COIN, FORGY.getFxtUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(-10*FxtChain.FXT.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-10*FxtChain.FXT.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(-10*FxtChain.FXT.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-10*FxtChain.FXT.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void signAndBroadcastBytes() {
        JO response = SendMoneyCall.create(FxtChain.FXT.getId()).
                publicKey(ALICE.getPublicKeyStr()).
                recipient(BOB.getStrId()).
                amountNQT(100 * FxtChain.FXT.ONE_COIN).
                feeNQT(10 * FxtChain.FXT.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        // No change transaction not broadcast
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtUnconfirmedBalanceDiff());

        response = SignTransactionCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                unsignedTransactionBytes(response.getString("unsignedTransactionBytes")).
                callNoError();
        Logger.logDebugMessage("signTransaction: " + response);

        response = BroadcastTransactionCall.create().
                transactionBytes(response.getString("transactionBytes")).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Sender
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * FxtChain.FXT.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(100 * FxtChain.FXT.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void signAndBroadcastJSON() {
        JO response = SendMoneyCall.create(FxtChain.FXT.getId()).
                publicKey(ALICE.getPublicKeyStr()).
                recipient(BOB.getStrId()).
                amountNQT(100 * FxtChain.FXT.ONE_COIN).
                feeNQT(10 * FxtChain.FXT.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        // No change transaction not broadcast
        Assert.assertEquals(0, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(0, ALICE.getFxtUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtBalanceDiff());
        Assert.assertEquals(0, BOB.getFxtUnconfirmedBalanceDiff());

        response = SignTransactionCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                unsignedTransactionJSON(response.getString("transactionJSON")).
                callNoError();
        Logger.logDebugMessage("signTransaction: " + response);

        response = BroadcastTransactionCall.create().
                transactionBytes(response.getString("transactionBytes")).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Sender
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtBalanceDiff());
        Assert.assertEquals(-100 * FxtChain.FXT.ONE_COIN - 10 * FxtChain.FXT.ONE_COIN, ALICE.getFxtUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * FxtChain.FXT.ONE_COIN, BOB.getFxtBalanceDiff());
        Assert.assertEquals(100 * FxtChain.FXT.ONE_COIN, BOB.getFxtUnconfirmedBalanceDiff());
    }

    @Test
    public void testReferencedTransactionBundling() {
        generateBlock();

        //submitted and bundled but not confirmed
        JSONAssert referenced = new JSONAssert(SendMoneyCall.create(AEUR.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                recipient(ALICE.getStrId()).
                amountNQT(100 * AEUR.ONE_COIN).
                feeNQT(AEUR.ONE_COIN).
                build().invoke());

        Assert.assertEquals(Boolean.TRUE, GetTransactionCall.create(AEUR.getId()).fullHash(referenced.fullHash()).
                build().invokeNoError().get("isBundled"));

        JSONAssert response = new JSONAssert(SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * IGNIS.ONE_COIN).
                referencedTransaction(AEUR.getId() + ":" + referenced.fullHash()).
                feeNQT(IGNIS.ONE_COIN).
                build().invoke());

        //not bundled because the referenced is not confirmed
        Assert.assertEquals(Boolean.FALSE, GetTransactionCall.create(AEUR.getId()).fullHash(response.fullHash()).
                build().invokeNoError().get("isBundled"));

        generateBlock();

        TransactionProcessorTest.processWaitingTransactions();
        //processWaitingTransactions adds the waiting transactions to the unconfirmed pool so it runs the bundling

        //should be bundled now
        Assert.assertEquals(Boolean.TRUE, GetTransactionCall.create(AEUR.getId()).fullHash(response.fullHash()).
                build().invokeNoError().get("isBundled"));
    }
}
