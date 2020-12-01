/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2020 Jelurida IP B.V.
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

package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.addons.JO;
import nxt.crypto.HashFunction;
import nxt.http.APICall;
import nxt.http.accountControl.ACTestUtils;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.GetAliasCall;
import nxt.http.callers.SendMessageCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SetAliasCall;
import nxt.http.callers.SignTransactionCall;
import nxt.util.Convert;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestApproveTransaction extends BlockchainTest {

    @Test
    public void validVoteCasting() {
        int duration = 10;

        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder()
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + duration)
                .build();

        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        generateBlock();

        JO response = ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(CHUCK.getSecretPhrase())
                .phasedTransaction(IGNIS.getId() + ":" + transactionJSON.get("fullHash"))
                .feeNQT(IGNIS.ONE_COIN)
                .callNoError();

        Logger.logMessage("approvePhasedTransactionResponse:" + response.toJSONString());
        Assert.assertNotNull(response.get("fullHash"));

        generateBlocks(duration);
        Assert.assertEquals(-50 * IGNIS.ONE_COIN - 2 * IGNIS.ONE_COIN,
                ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(50 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-IGNIS.ONE_COIN, CHUCK.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void invalidVoteCasting() {
        int duration = 10;

        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder()
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + duration)
                .build();

        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        generateBlock();
        JO response = ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(DAVE.getSecretPhrase())
                .phasedTransaction(IGNIS.getId() + ":" + transactionJSON.get("fullHash"))
                .feeNQT(IGNIS.ONE_COIN)
                .call();
        Assert.assertNotNull(response.get("error"));
        generateBlock();

        Assert.assertEquals("ALICE balance: ", -2 * IGNIS.ONE_COIN,
                ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals("BOB balance: ", 0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals("CHUCK balance: ", 0, CHUCK.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals("DAVE balance: ", 0, DAVE.getChainBalanceDiff(IGNIS.getId()));

        generateBlocks(duration);

        Assert.assertEquals("ALICE balance: ", -2 * IGNIS.ONE_COIN,
                ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals("BOB balance: ", 0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals("CHUCK balance: ", 0, CHUCK.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals("DAVE balance: ", 0, DAVE.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void sendMoneyPhasedNoVoting() {
        long fee = 2* IGNIS.ONE_COIN;
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * IGNIS.ONE_COIN).
                feeNQT(fee).
                phased(true).
                phasingFinishHeight(baseHeight + 2).
                phasingVotingModel((byte) -1).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);

        generateBlock();
        // Transaction is not applied yet, fee is paid
        // Forger
        Assert.assertEquals(fee, FORGY.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(fee, FORGY.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Sender
        Assert.assertEquals(-fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(0, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));

        generateBlock();
        // Transaction is applied
        // Sender
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void sendMoneyPhasedByTransactionHash() {
        JO response = getSignedBytes();
        Logger.logDebugMessage("signedSendMessage: " + response);
        String fullHash = (String)response.get("fullHash");
        Assert.assertEquals(64, fullHash.length());
        String approvalTransactionBytes = (String)response.get("transactionBytes");

        long fee = 3 * IGNIS.ONE_COIN;
        response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * IGNIS.ONE_COIN).
                feeNQT(fee).
                phased(true).
                phasingFinishHeight(baseHeight + 3).
                phasingVotingModel((byte) 4).
                phasingLinkedTransaction(IGNIS.getId() + ":" + fullHash).
                phasingQuorum(1).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);

        generateBlock();
        // Transaction is not applied yet
        // Sender
        Assert.assertEquals(-fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(0, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));

        response = BroadcastTransactionCall.create().
                transactionBytes(approvalTransactionBytes).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Transaction is applied before finish height
        // Sender
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void sendMoneyPhasedByTransactionHash2of3() {
        JO response = getSignedBytes();
        Logger.logDebugMessage("signedSendMessage: " + response);
        String fullHash1 = (String)response.get("fullHash");
        Assert.assertEquals(64, fullHash1.length());
        String approvalTransactionBytes1 = (String)response.get("transactionBytes");
        response = getSignedBytes();
        Logger.logDebugMessage("signedSendMessage: " + response);
        String fullHash2 = (String)response.get("fullHash");
        Assert.assertEquals(64, fullHash2.length());
        response = getSignedBytes();
        Logger.logDebugMessage("signedSendMessage: " + response);
        String fullHash3 = (String)response.get("fullHash");
        Assert.assertEquals(64, fullHash3.length());
        String approvalTransactionBytes3 = (String)response.get("transactionBytes");

        String chainPrefix = IGNIS.getId() + ":";
        long fee = 5 * IGNIS.ONE_COIN;
        response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * IGNIS.ONE_COIN).
                feeNQT(fee).
                phased(true).
                phasingFinishHeight(baseHeight + 2).
                phasingVotingModel((byte) 4).
                phasingLinkedTransaction(new String[] { chainPrefix + fullHash1, chainPrefix + fullHash2,
                        chainPrefix + fullHash3 }).
                phasingQuorum(2).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);

        generateBlock();
        // Transaction is not applied yet
        // Sender
        Assert.assertEquals(-fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(0, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));

        response = BroadcastTransactionCall.create().
                transactionBytes(approvalTransactionBytes1).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        response = BroadcastTransactionCall.create().
                transactionBytes(approvalTransactionBytes3).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Transaction is applied since 2 out 3 hashes were provided
        // Sender
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void sendMoneyPhasedByTransactionHashNotApplied() {
        long fee = 3 * IGNIS.ONE_COIN;
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                amountNQT(100 * IGNIS.ONE_COIN).
                feeNQT(fee).
                phased(true).
                phasingFinishHeight(baseHeight + 2).
                phasingVotingModel((byte) 4).
                phasingLinkedTransaction(IGNIS.getId() + ":a13bbe67211fea8d59b2621f1e0118bb242dc5000d428a23a8bd47491a05d681"). // this hash does not match any transaction
                phasingQuorum(1).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);

        generateBlock();
        // Transaction is not applied yet
        // Sender
        Assert.assertEquals(-fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-100 * IGNIS.ONE_COIN - fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(0, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));

        generateBlock();
        // Transaction is rejected since full hash does not match
        // Sender
        Assert.assertEquals(-fee, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-fee, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
        // Recipient
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(0, BOB.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void setAliasPhasedByTransactionHashInvalid() {
        JO response = getSignedBytes();
        Logger.logDebugMessage("signedSendMessage: " + response);
        String fullHash = (String)response.get("fullHash");
        Assert.assertEquals(64, fullHash.length());
        String approvalTransactionBytes = (String)response.get("transactionBytes");

        long fee = 2 * IGNIS.ONE_COIN;
        String alias = "alias" + System.currentTimeMillis();
        response = SetAliasCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                aliasName(alias).
                feeNQT(fee).
                phased(true).
                phasingFinishHeight(baseHeight + 4).
                phasingVotingModel((byte) 4).
                phasingLinkedTransaction(fullHash).
                phasingQuorum(1).
                call();
        Logger.logDebugMessage("setAlias: " + response);

        generateBlock();
        response = GetAliasCall.create().
                aliasName(alias).
                call();
        Logger.logDebugMessage("getAlias: " + response);
        Assert.assertEquals((long)5, response.get("errorCode"));

        response = BroadcastTransactionCall.create().
                transactionBytes(approvalTransactionBytes).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // allocate the same alias immediately
        response = SetAliasCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                aliasName(alias).
                feeNQT(fee).
                callNoError();
        Logger.logDebugMessage("setSameAlias: " + response);
        generateBlock();
        // phased setAlias transaction is applied but invalid
        response = GetAliasCall.create().
                aliasName(alias).
                callNoError();
        Logger.logDebugMessage("getAlias: " + response);
        Assert.assertEquals(BOB.getStrId(), response.get("account"));
        generateBlock();
        // phased setAlias transaction is applied but invalid
        response = GetAliasCall.create().
                aliasName(alias).
                callNoError();
        Logger.logDebugMessage("getAlias: " + response);
        Assert.assertEquals(BOB.getStrId(), response.get("account"));
    }

    @Test
    public void testInvalidHash() {
        long amount = 100 * IGNIS.ONE_COIN;
        String secret = "abc";
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret.getBytes())))
                .phasingQuorum(1);

        String fullHash = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash, BOB, "wrong secret");

        JSONAssert jsonAssert = new JSONAssert(approveBuilder.call());
        Assert.assertEquals(
                String.format("Hashed secret(s) in phased transaction %s:%s do not match any of the revealed secrets", IGNIS.getId(), fullHash),
                jsonAssert.str("errorDescription"));
    }

    @Test
    public void testApproveTwoTransactionsWithOneSecret() {
        long amount = 100 * IGNIS.ONE_COIN;
        String secret = "abc";
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret.getBytes())))
                .phasingQuorum(1);

        String fullHash1 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        String fullHash2 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash1, BOB, secret);
        approveBuilder.phasedTransaction(IGNIS.getId() + ":" + fullHash1, IGNIS.getId() + ":" + fullHash2);

        new JSONAssert(approveBuilder.call()).str("fullHash");

        generateBlock();

        Assert.assertEquals(2 * amount - IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testApproveTwoTransactionsWithTwoSecrets() {
        long amount = 100 * IGNIS.ONE_COIN;
        String secret1 = "abc111";
        String secret2 = "abc222";
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret1.getBytes())))
                .phasingQuorum(1);

        String fullHash1 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        builder.phasingHashedSecretAlgorithm(HashFunction.SHA256.getId());
        builder.phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret2.getBytes())));

        String fullHash2 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash1, BOB, "");
        approveBuilder.phasedTransaction(IGNIS.getId() + ":" + fullHash1, IGNIS.getId() + ":" + fullHash2);
        approveBuilder.revealedSecretText(secret1, secret2);

        new JSONAssert(approveBuilder.call()).str("fullHash");

        generateBlock();

        Assert.assertEquals(2 * amount - IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testApproveTransactionsWithTwoSecretsWithoutFullhash() {
        long amount = 100 * IGNIS.ONE_COIN;
        String secret1 = "abc111";
        String secret2 = "abc222";
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret1.getBytes())))
                .phasingQuorum(1);
        new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        builder.phasingHashedSecretAlgorithm(HashFunction.SHA256.getId());
        builder.phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret2.getBytes())));
        new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        // Reveal the secrets but do not list the transactions
        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(null, BOB, "");
        approveBuilder.revealedSecretText(secret1, secret2);

        new JSONAssert(approveBuilder.call()).str("fullHash");

        generateBlock();
        Assert.assertEquals(2 * amount - IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testApproveUnusedSecret() {
        long amount = 100 * IGNIS.ONE_COIN;
        String secret1 = "abc111";
        String secretUnused = "abc222";
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret1.getBytes())))
                .phasingQuorum(1);

        String fullHash1 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash1, BOB, "");
        approveBuilder.revealedSecretText(secret1, secretUnused);

        Assert.assertEquals("Revealed secret with index 1 is not used",
                new JSONAssert(approveBuilder.call()).str("errorDescription"));
    }

    @Test
    public void testInvalidHashInOneOfTheTransactions() {
        long amount = 100 * IGNIS.ONE_COIN;
        String secret = "abc";
        String wrongSecret = "wrong secret";
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId()).amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(secret.getBytes())))
                .phasingQuorum(1);

        String fullHash1 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        builder.phasingHashedSecretAlgorithm(HashFunction.SHA256.getId());
        builder.phasingHashedSecret(Convert.toHexString(HashFunction.SHA256.hash(wrongSecret.getBytes())));

        String fullHash2 = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash1, BOB, secret);
        approveBuilder.phasedTransaction(IGNIS.getId() + ":" + fullHash1, IGNIS.getId() + ":" + fullHash2);

        JSONAssert jsonAssert = new JSONAssert(approveBuilder.call());
        Assert.assertEquals(
                String.format("Hashed secret(s) in phased transaction %s:%s do not match any of the revealed secrets", IGNIS.getId(), fullHash2),
                jsonAssert.str("errorDescription"));
    }

    static JO getSignedBytes() {
        JO response = SendMessageCall.create(IGNIS.getId()).
                publicKey(CHUCK.getPublicKeyStr()).
                recipient(ALICE.getStrId()).
                message("approval notice").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage not broadcasted: " + response);
        response = SignTransactionCall.create().
                secretPhrase(CHUCK.getSecretPhrase()).
                unsignedTransactionBytes((String)response.get("unsignedTransactionBytes")).
                callNoError();
        return response;
    }
}