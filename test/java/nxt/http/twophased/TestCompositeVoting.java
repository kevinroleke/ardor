/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
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
 
 package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.PhasingParamsBuilder;
import nxt.http.PhasingParamsHelper;
import nxt.http.accountControl.ACTestUtils;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.GetLinkedPhasedTransactionsCall;
import nxt.http.callers.GetPhasingPollCall;
import nxt.http.callers.GetVoterPhasedTransactionsCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.IssueCurrencyCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TransferAssetCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import nxt.voting.VoteWeighting;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCompositeVoting extends BlockchainTest {
    @Test
    public void testWhitelistAndByHash() {
        String secret = "test secret";
        JO response = createPhasedWhiteListAndByHash(secret, BOB);

        String fullHash = response.getString("fullHash");
        ACTestUtils.approve(fullHash, CHUCK, null);

        generateBlocks(4);

        //Not approved
        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        secret = "test secret 1";
        response = createPhasedWhiteListAndByHash(secret, BOB);
        fullHash = response.getString("fullHash");
        ACTestUtils.approve(fullHash, DAVE, secret);

        generateBlocks(4);

        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        response = createPhasedWhiteListAndByHash(secret, BOB);
        ACTestUtils.approve(response.get("fullHash"), DAVE, secret);
        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);

        generateBlock();

        //Approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testWhitelistedMoreThanOnce() {
        String secret = "test abcd";

        PhasingParamsBuilder subPollChuck = PhasingParamsHelper.accountSubpoll(CHUCK);
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B & C & D")
                .setSubPoll("A", PhasingParamsHelper.hashSubpoll(secret))
                .setSubPoll("B", subPollChuck)
                .setSubPoll("C", subPollChuck)
                .setSubPoll("D", subPollChuck);

        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();

        generateBlock();

        //single vote approves all sub-polls where CHUCK is whitelisted
        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);
        ACTestUtils.approve(response.get("fullHash"), DAVE, secret);

        generateBlock();

        //Approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testSameSecretInTwoSubPolls() {
        String secret = "test abcd";

        PhasingParamsBuilder subPollHash = PhasingParamsHelper.hashSubpoll(secret);
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B & C")
                .setSubPoll("A", subPollHash)
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(CHUCK))
                .setSubPoll("C", subPollHash);

        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();

        generateBlock();

        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);

        //single vote approves all sub-polls where the hashed secret is found
        ACTestUtils.approve(response.get("fullHash"), DAVE, secret);

        generateBlock();

        //Approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testTwoSecretsApproveAtOnce() {
        String secretA = "test A";
        String secretB = "test B";

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.hashSubpoll(secretA))
                .setSubPoll("B", PhasingParamsHelper.hashSubpoll(secretB));
        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        String fullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");

        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash, CHUCK, secretA);
        approveBuilder.revealedSecretText(secretA, secretB);
        new JSONAssert(approveBuilder.call()).str("fullHash");

        generateBlock();

        //Approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testAssetAndCurrency() {
        generateBlocks(4); //empty for the back fees feature

        String currencyId = distributeCurrency(100);
        String assetId = distributeAsset(100);

        JO response = createPhasedAssetAndCurrency(DAVE, assetId, currencyId);
        String fullHash = response.getString("fullHash");
        ACTestUtils.approve(fullHash, BOB, null);
        generateBlocks(4);

        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, DAVE.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        response = createPhasedAssetAndCurrency(DAVE, assetId, currencyId);
        fullHash = response.getString("fullHash");
        ACTestUtils.approve(fullHash, CHUCK, null);
        generateBlocks(4);

        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, DAVE.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        response = createPhasedAssetAndCurrency(DAVE, assetId, currencyId);
        fullHash = response.getString("fullHash");
        ACTestUtils.approve(fullHash, BOB, null);
        ACTestUtils.approve(fullHash, CHUCK, null);

        generateBlocks(1);
        //Not yet approved
        Assert.assertEquals(ACTestUtils.PhasingStatus.PENDING, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, DAVE.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlocks(3);
        //Approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, DAVE.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        response = createPhasedAssetAndCurrency(DAVE, assetId, currencyId);
        ACTestUtils.approve(response.get("fullHash"), ALICE, null);
        generateBlocks(4);
        //Since Alice has enough of both the asset and the currency, she can finish both polls with one transaction
        Assert.assertEquals(200 * ChildChain.IGNIS.ONE_COIN, DAVE.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        //test trimming
        //generateBlocks(Math.max(Constants.MAX_ROLLBACK, Nxt.getIntProperty("nxt.trimFrequency")));
    }

    @Test
    public void testNegatedWhitelist() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        JO response = createWhitelistNegated(BOB, fee);
        String fullHash = response.getString("fullHash");

        generateBlocks(3);
        //Not yet approved
        Assert.assertEquals(ACTestUtils.PhasingStatus.PENDING, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlock();
        //Approved by simply waiting for the finish height
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        response = createWhitelistNegated(DAVE, fee);

        fullHash = response.getString("fullHash");
        ACTestUtils.approve(fullHash, CHUCK, null);
        generateBlock();

        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(-fee, DAVE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void xorApprovalNoApproval() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        createXorWhitelist(BOB, fee);

        generateBlocks(3);
        // Not yet approved
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlock();
        // Not Approved, Bob only paid the fee
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void xorApprovalBothApprove() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        JO response = createXorWhitelist(BOB, fee);
        generateBlock();
        // Not yet approved
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));

        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);
        ACTestUtils.approve(response.get("fullHash"), DAVE, null);
        generateBlock();
        // Not apprvoed (no need to wait for finish height)
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void xorApprovalChuckApproves() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        JO response = createXorWhitelist(BOB, fee);
        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);
        generateBlocks(3);
        // Not yet approved
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlock();
        // Approved at finsh height
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void xorApprovalDaveApproves() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        JO response = createXorWhitelist(BOB, fee);
        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);
        generateBlocks(3);
        // Not yet approved
        Assert.assertEquals(0, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlock();
        // Approved at finsh height
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, BOB.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testNegatedAssetApproved() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        createPhasedByAssetNegated(BOB, CHUCK, fee);

        generateBlocks(4);

        //Approved by simply waiting for the finish height
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, CHUCK.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testNegatedAssetRejected() {
        long fee = 3 * ChildChain.IGNIS.ONE_COIN;
        JO response = createPhasedByAssetNegated(DAVE, CHUCK, fee);
        ACTestUtils.approve(response.get("fullHash"), BOB, null);
        generateBlock();

        // still not rejected
        Assert.assertEquals(0, CHUCK.getChainBalanceDiff(ChildChain.IGNIS.getId()));
        Assert.assertEquals(-100 * ChildChain.IGNIS.ONE_COIN - fee, DAVE.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlocks(3);

        // Rejected at finish height
        Assert.assertEquals(-fee, DAVE.getChainUnconfirmedBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testByPropertyAndNone() {
        String propertyName = "prop3";
        String propertyValue = "prop_val";

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        generateBlock();

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & N")
                .setSubPoll("A", PhasingParamsHelper.senderPropertySubpoll(CHUCK, propertyName, propertyValue))
                .setSubPoll("N", PhasingParamsBuilder.create().phasingVotingModel(VoteWeighting.VotingModel.NONE.getCode()));

        SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();
        generateBlock();

        //Not finished yet
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlocks(4);
        //Finished
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testNoEarlyFinish() {

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & N")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(CHUCK))
                .setSubPoll("N", PhasingParamsBuilder.create().phasingVotingModel(VoteWeighting.VotingModel.NONE.getCode()));

        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        sendMoneyCall.callNoError();
        generateBlock();

        //Not finished yet
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlocks(4);
        //Rejected
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        //Phase again
        sendMoneyCall.phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5);
        JO response = sendMoneyCall.callNoError();
        generateBlock();

        //approve
        ACTestUtils.approve(response.get("fullHash"), CHUCK, null);
        generateBlock();

        //still not finished
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlocks(3);

        //finished
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testByTwoProperties() {

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, "a", "b").callNoError();
        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, "c", "d").callNoError();

        generateBlock();

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.senderPropertySubpoll(CHUCK, "a", "b"))
                .setSubPoll("B", PhasingParamsHelper.senderPropertySubpoll(CHUCK, "c", "d"));

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * ChildChain.IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        String fullHash = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        //finished
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        GetPhasingPollCall call = GetPhasingPollCall.create().transactionFullHash(fullHash).countVotes(true);
        JSONAssert result = new JSONAssert(call.call());

        Assert.assertEquals("1", result.str("result"));
    }

    @Test
    public void testCompositeVotingNotAcceptingSecret() {
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(CHUCK))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(DAVE));

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * ChildChain.IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        String fullHash = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash, CHUCK, "some secret");

        JSONAssert jsonAssert = new JSONAssert(approveBuilder.call());
        Assert.assertEquals(
                String.format("Phased transaction %s:%s does not accept by-hash voting", ChildChain.IGNIS.getId(), fullHash),
                jsonAssert.str("errorDescription"));
    }

    @Test
    public void testEarlyFinishOfTransactionVoting() {
        JSONAssert response = new JSONAssert(TestApproveTransaction.getSignedBytes());
        String fullHash1 = response.str("fullHash");
        String approvalTransactionBytes1 = response.str("transactionBytes");

        response = new JSONAssert(TestApproveTransaction.getSignedBytes());
        String fullHash2 = response.str("fullHash");
        String approvalTransactionBytes2 = response.str("transactionBytes");

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("T1 & T2")
                .setSubPoll("T1", PhasingParamsHelper.linkedTransactionSubpoll(ChildChain.IGNIS.getId() + ":" + fullHash1))
                .setSubPoll("T2", PhasingParamsHelper.linkedTransactionSubpoll(ChildChain.IGNIS.getId() + ":" + fullHash2));

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        new JSONAssert(builder.call()).str("fullHash");

        generateBlock();

        BroadcastTransactionCall.create().transactionBytes(approvalTransactionBytes1).callNoError();
        BroadcastTransactionCall.create().transactionBytes(approvalTransactionBytes2).callNoError();
        generateBlock();

        //finished
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testLinkedTransactionsGrouping() {
        JSONAssert response = new JSONAssert(TestApproveTransaction.getSignedBytes());
        String fullHash = response.str("fullHash");
        String approvalTransactionBytes = response.str("transactionBytes");

        //two sub-polls with same linked transaction

        PhasingParamsBuilder subPoll = PhasingParamsHelper.linkedTransactionSubpoll(IGNIS.getId() + ":" + fullHash);
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("T1 & T2")
                .setSubPoll("T1", subPoll)
                .setSubPoll("T2", subPoll);

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        String phasedFullHash = new JSONAssert(builder.call()).str("fullHash");

        generateBlock();

        GetLinkedPhasedTransactionsCall queryBuilder = GetLinkedPhasedTransactionsCall.create().linkedFullHash(fullHash);
        List<JSONObject> transactions = new JSONAssert(queryBuilder.call()).array("transactions", JSONObject.class);
        Assert.assertEquals(1, transactions.size());
        Assert.assertEquals(phasedFullHash, new JSONAssert(transactions.get(0)).str("fullHash"));

        //both sub-polls are approved with one voting transaction
        BroadcastTransactionCall.create().transactionBytes(approvalTransactionBytes).callNoError();
        generateBlock();

        //finished
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testWhitelistedCoinVoting() {
        PhasingParamsBuilder subpollA2 = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COIN.getCode())
                .phasingHolding(ChildChain.IGNIS.getId())
                .phasingWhitelisted(DAVE.getStrId())
                .phasingQuorum(DAVE.getInitialChainBalance(ChildChain.IGNIS.getId()) + 1);
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A1 & A2")
                .setSubPoll("A1", PhasingParamsHelper.accountSubpoll(DAVE, CHUCK).phasingQuorum(2))
                .setSubPoll("A2", subpollA2);
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        String phasedFullHash = new JSONAssert(builder.call()).str("fullHash");

        generateBlock();

        GetVoterPhasedTransactionsCall queryBuilder = GetVoterPhasedTransactionsCall.create().account(DAVE.getStrId());
        List<JSONObject> transactions = new JSONAssert(queryBuilder.call()).array("transactions", JSONObject.class);
        Assert.assertEquals(1, transactions.size());
        Assert.assertEquals(phasedFullHash, new JSONAssert(transactions.get(0)).str("fullHash"));

        ACTestUtils.approve(phasedFullHash, CHUCK, null);
        ACTestUtils.approve(phasedFullHash, DAVE, null);

        generateBlocks(4);

        //rejected - chuck's vote should not be counted for sub-poll A2 (since he is not whitelisted there)
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        //add CHUCK to whitelist
        phasingParamsBuilder.setSubPoll("A2", subpollA2.phasingWhitelisted(DAVE.getStrId(), CHUCK.getStrId()));
        builder.phasingParams(phasingParamsBuilder.toJSONString());
        builder.phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5);
        phasedFullHash = new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        ACTestUtils.approve(phasedFullHash, CHUCK, null);
        ACTestUtils.approve(phasedFullHash, DAVE, null);

        generateBlock();

        //cannot finish early due to balance voting
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));

        generateBlocks(3);

        //approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testLongVariable() {
        String literal = "Variable001";

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & " + literal)
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(CHUCK))
                .setSubPoll(literal, PhasingParamsHelper.accountSubpoll(CHUCK));

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        JSONAssert response = new JSONAssert(builder.call());

        Assert.assertTrue(response.str("errorDescription").contains("Invalid variable name"));
    }

    @Test
    public void testLongExpression() {
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1);

        StringBuilder expression = new StringBuilder();
        PhasingParamsBuilder subPoll = PhasingParamsHelper.accountSubpoll(CHUCK);
        for (int i = 0; i < 10; i++) {
            String literal = "Variable0" + i;
            expression.append(literal).append(i < 9 ? " &" : "  ");
            for (int j = 12; j < 100; j++) {
                expression.append(' ');
            }
            phasingParamsBuilder.setSubPoll(literal, subPoll);
        }
        phasingParamsBuilder.phasingExpression(expression.toString() + " ");

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        JSONAssert response = new JSONAssert(builder.call());

        Assert.assertTrue(response.str("errorDescription").contains("Invalid boolean expression"));

        phasingParamsBuilder.phasingExpression(expression.toString());
        builder.phasingParams(phasingParamsBuilder.toJSONString());

        response = new JSONAssert(builder.call());

        generateBlock();

        //single vote approves all sub-polls where CHUCK is whitelisted
        ACTestUtils.approve(response.fullHash(), CHUCK, null);

        generateBlock();

        //Approved
        Assert.assertEquals(100 * ChildChain.IGNIS.ONE_COIN, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @Test
    public void testNegatedHash() {
        String secret = "test A";

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("!H")
                .setSubPoll("H", PhasingParamsHelper.hashSubpoll(secret));
        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(4 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        String fullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");

        generateBlocks(2);

        ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(fullHash, CHUCK, secret);
        approveBuilder.revealedSecretText(secret);
        new JSONAssert(approveBuilder.call()).str("fullHash");

        generateBlock();

        //Transaction canceled
        Assert.assertEquals(0, BOB.getChainBalanceDiff(ChildChain.IGNIS.getId()));
    }

    @SuppressWarnings("SameParameterValue")
    private String distributeCurrency(long chuckCurrencyAmount) {
        IssueCurrencyCall issueCurrencyCall = TestCurrencyIssuance.builder("CompositeV", "TCOMV", "Test Composite Voting");
        String currencyId = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(issueCurrencyCall));
        generateBlock();

        TransferCurrencyCall builder = TransferCurrencyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(CHUCK.getRsAccount())
                .currency(currencyId)
                .unitsQNT(chuckCurrencyAmount)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440);
        ACTestUtils.assertTransactionSuccess(builder);

        BlockchainTest.generateBlock();

        return currencyId;
    }

    private String distributeAsset(long bobAssetAmount) {
        IssueAssetCall issueAssetCall = ACTestUtils.issueAssetBuilder(ALICE.getSecretPhrase(), "CompositeV");
        String assetId = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(issueAssetCall));
        generateBlock();

        TransferAssetCall builder = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getRsAccount())
                .asset(assetId)
                .quantityQNT(bobAssetAmount)
                .feeNQT(IGNIS.ONE_COIN);
        ACTestUtils.assertTransactionSuccess(builder);

        BlockchainTest.generateBlock();

        return assetId;
    }

    private JO createPhasedWhiteListAndByHash(String secret, Tester recipient) {
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.hashSubpoll(secret))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(CHUCK));

        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .recipient(recipient.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        return response;
    }

    private JO createPhasedAssetAndCurrency(Tester recipient, String assetId, String currencyId) {
        PhasingParamsBuilder subpollA = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.ASSET.getCode())
                .phasingHolding(assetId)
                .phasingQuorum(100);
        PhasingParamsBuilder subpollC = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.CURRENCY.getCode())
                .phasingHolding(currencyId)
                .phasingQuorum(100);
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & C")
                .setSubPoll("A", subpollA)
                .setSubPoll("C", subpollC);

        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(6 * ChildChain.IGNIS.ONE_COIN)
                .recipient(recipient.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();
        Logger.logDebugMessage("sendMoney: " + response);

        generateBlock();

        return response;
    }

    private JO createWhitelistNegated(Tester sender, long fee) {
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("!A")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(CHUCK));

        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(sender.getSecretPhrase())
                .feeNQT(fee)
                .recipient(ALICE.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();

        generateBlock();

        return response;
    }

    private JO createXorWhitelist(Tester sender, long fee) {
        // XOR CHUCK and DAVE approval
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & !B | !A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(CHUCK))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(DAVE));
        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(sender.getSecretPhrase())
                .feeNQT(fee)
                .recipient(ALICE.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();

        generateBlock();
        return response;
    }

    private JO createPhasedByAssetNegated(Tester sender, Tester recipient, long fee) {

        String assetId = distributeAsset(150);

        PhasingParamsBuilder subpoll = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.ASSET.getCode())
                .phasingHolding(assetId)
                .phasingQuorum(150);
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("!A")
                .setSubPoll("A", subpoll);

        JO response = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(sender.getSecretPhrase())
                .feeNQT(fee)
                .recipient(recipient.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();

        generateBlock();

        return response;
    }
}
