/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2021 Jelurida IP B.V.
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

package nxt.http.accountControl;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.PhasingParamsBuilder;
import nxt.http.PhasingParamsHelper;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.CreateOneSideTransactionCallBuilder;
import nxt.http.callers.GetPhasingOnlyControlCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.IssueCurrencyCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SetPhasingOnlyControlCall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import nxt.http.twophased.TestPropertyVoting;
import nxt.util.Convert;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import nxt.voting.PhasingParams;
import nxt.voting.VoteWeighting;
import nxt.voting.VoteWeighting.VotingModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static nxt.blockchain.ChildChain.IGNIS;
import static org.junit.Assert.assertEquals;

public class PhasingOnlyTest extends BlockchainTest {
    @Test
    public void testSetAndGet() {
        
        ACTestUtils.assertNoPhasingOnlyControl();

        JO response = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.accountSubpoll(BOB).toJSONString())
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440)
                .callNoError();

        Logger.logMessage("setPhasingOnlyControl response: " + response.toJSONString());

        Assert.assertNotNull(response.getString("fullHash"));

        generateBlock();

        assertPhasingOnly(new PhasingParams(new VoteWeighting(VotingModel.ACCOUNT.getCode(), 0L, 0L, (byte)0),
                        1L,  new long[] {BOB.getId()}, Collections.emptyList(), null, null, null, null),
                buildMaxFeesJSON(IGNIS.getId(), 10 * IGNIS.ONE_COIN), 5, 1440);
    }

    @Test
    public void testAccountVoting() {
        //all transactions must be approved either by BOB or CHUCK
        JO response = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.accountSubpoll(BOB, CHUCK).toJSONString())
                .callNoError();
        Logger.logMessage("setPhasingOnlyControl response: " + response.toJSONString());

        Assert.assertNotNull(response.getString("fullHash"));

        generateBlock();

        SendMoneyCall sendMoneyCall = sendMoneyBuilder();

        //no phasing - block
        ACTestUtils.assertTransactionBlocked(sendMoneyCall);

      //correct phasing
        setTransactionPhasingParams(sendMoneyCall, 20, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionSuccess(sendMoneyCall);
        
      //subset of the voters should also be blocked
        setTransactionPhasingParams(sendMoneyCall, 20, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId()});
        ACTestUtils.assertTransactionBlocked(sendMoneyCall);
        
      //incorrect quorum - even if more restrictive, should also be blocked
        setTransactionPhasingParams(sendMoneyCall, 20, VotingModel.ACCOUNT, null, 2L, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionBlocked(sendMoneyCall);
        
        //remove the phasing control
        SetPhasingOnlyControlCall builder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.NONE.getCode());

        setTransactionPhasingParams(builder, 3, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId(), CHUCK.getId()});
        
        JO removePhasingOnlyJSON = ACTestUtils.assertTransactionSuccess(builder);
        generateBlock();
        
        assertPhasingOnly(new PhasingParams(new VoteWeighting(VotingModel.ACCOUNT.getCode(), 0L, 0L, (byte)0),
                1L, new long[] {BOB.getId(), CHUCK.getId()}, Collections.emptyList(),
                null, null, null, null), new JO(), 0, 0);

        //approve the remove
        ACTestUtils.assertTransactionSuccess(approveTransactionBuilder(removePhasingOnlyJSON));
        
        generateBlock();

        ACTestUtils.assertNoPhasingOnlyControl();
    }

    @Test
    public void testExtraRestrictions() {
        //all transactions must be approved either by BOB or CHUCK, total fees 5 NXT, min duration 4, max duration 100
        JO response1 = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.accountSubpoll(BOB, CHUCK).toJSONString())
                .controlMaxFees(IGNIS.getId() + ":" + 5 * IGNIS.ONE_COIN)
                .controlMinDuration(4)
                .controlMaxDuration(100)
                .callNoError();
        Logger.logMessage("setPhasingOnlyControl response: " + response1.toJSONString());

        Assert.assertNotNull(response1.getString("fullHash"));

        generateBlock();

        SendMoneyCall builder = sendMoneyBuilder().feeNQT(7 * IGNIS.ONE_COIN);
        // fee too high
        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        // fee at the limit
        builder.feeNQT(5 * IGNIS.ONE_COIN);
        JO response = ACTestUtils.assertTransactionSuccess(builder);

        generateBlock();

        // not yet approved, another transaction at max fee should fail
        ACTestUtils.assertTransactionBlocked(builder);

        //approve
        ApproveTransactionCall approveBuilder = approveTransactionBuilder(response);
        ACTestUtils.assertTransactionSuccess(approveBuilder);
        generateBlock();

        //now can submit next transaction
        response = ACTestUtils.assertTransactionSuccess(builder);
        String fullHash = getPhasedTransaction(response);
        generateBlock();

        //approve
        approveBuilder.phasedTransaction(fullHash);
        ACTestUtils.assertTransactionSuccess(approveBuilder);
        generateBlock();

        //too long or too short periods should fail
        builder.phasingFinishHeight(Nxt.getBlockchain().getHeight() + 200);
        ACTestUtils.assertTransactionBlocked(builder);
        builder.phasingFinishHeight(Nxt.getBlockchain().getHeight() + 3);
        ACTestUtils.assertTransactionBlocked(builder);
        builder.phasingFinishHeight(Nxt.getBlockchain().getHeight() + 4);
        ACTestUtils.assertTransactionSuccess(builder);

    }

    @Test
    public void testRejectingPendingTransaction() {

        SendMoneyCall sendMoneyCall = sendMoneyBuilder();

        setTransactionPhasingParams(sendMoneyCall, 4, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId(), CHUCK.getId()});
        JO sendMoneyJSON = ACTestUtils.assertTransactionSuccess(sendMoneyCall);
        generateBlock();

        SetPhasingOnlyControlCall setPhasingOnlyControlCall = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.accountSubpoll(DAVE).toJSONString());

        ACTestUtils.assertTransactionSuccess(setPhasingOnlyControlCall);

        generateBlock();

        long balanceBeforeTransactionApproval = ACTestUtils.getAccountBalance(BOB.getId(), "unconfirmedBalanceNQT");

        //approve the pending transaction
        ApproveTransactionCall builder = approveTransactionBuilder(sendMoneyJSON).secretPhrase(CHUCK.getSecretPhrase());
        ACTestUtils.assertTransactionSuccess(builder);

        generateBlock();

        //the sendMoney finish height
        generateBlock();

        //Transaction is approved - since commit 8b44767 account control is not checked at finish height
        assertEquals(balanceBeforeTransactionApproval + IGNIS.ONE_COIN,
                ACTestUtils.getAccountBalance(BOB.getId(), "unconfirmedBalanceNQT"));
    }

    @Test
    public void testRejectingPendingTransaction2() {

        SendMoneyCall sendMoneyCall = sendMoneyBuilder();

        setTransactionPhasingParams(sendMoneyCall, 4, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionSuccess(sendMoneyCall);
        generateBlock();

        SetPhasingOnlyControlCall builder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.accountSubpoll(DAVE).toJSONString());

        ACTestUtils.assertTransactionSuccess(builder);

        generateBlock();

        long balanceBeforeTransactionApproval = ACTestUtils.getAccountBalance(BOB.getId(), "unconfirmedBalanceNQT");

        generateBlock();

        //the sendMoney finish height
        generateBlock();

        //Transaction is approved - since commit 8b44767 account control is not checked at finish height
        assertEquals(balanceBeforeTransactionApproval,
                ACTestUtils.getAccountBalance(BOB.getId(), "unconfirmedBalanceNQT"));
    }

    @Test
    public void testBalanceVoting() {

        PhasingParamsBuilder controlParams = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COIN.getCode())
                .phasingHolding(IGNIS.getId())
                .phasingQuorum(100 * IGNIS.ONE_COIN);
        JO response = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(controlParams.toJSONString())
                .callNoError();
        Logger.logMessage("setPhasingOnlyControl response: " + response.toJSONString());

        Assert.assertNotNull(response.getString("fullHash"));

        generateBlock();

        SendMoneyCall builder = sendMoneyBuilder();

        //no phasing - block
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.COIN, "2", 100 * IGNIS.ONE_COIN, new long[] {DAVE.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId(), CHUCK.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.COIN, "2", 100 * IGNIS.ONE_COIN + 1, null);
        ACTestUtils.assertTransactionBlocked(builder);

        builder = sendMoneyBuilder();

        setTransactionPhasingParams(builder, 20, VotingModel.COIN, "2", 100 * IGNIS.ONE_COIN, null);
        ACTestUtils.assertTransactionSuccess(builder);
    }

    @Test
    public void testAssetVoting() {
        IssueAssetCall issueAssetCall = ACTestUtils.issueAssetBuilder(BOB.getSecretPhrase(), "TestAsset");
        String assetId = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(issueAssetCall));
        generateBlock();

        issueAssetCall = ACTestUtils.issueAssetBuilder(BOB.getSecretPhrase(), "TestAsset2");
        String asset2Id = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(issueAssetCall));
        generateBlock();

        PhasingParamsBuilder controlParams = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.ASSET.getCode())
                .phasingHolding(assetId)
                .phasingQuorum(1);
        JO response = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(controlParams.toJSONString())
                .callNoError();
        Logger.logMessage("setPhasingOnlyControl response: " + response.toJSONString());

        Assert.assertNotNull(response.getString("fullHash"));

        generateBlock();

        SendMoneyCall builder = sendMoneyBuilder();
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.ASSET, asset2Id, 1L, null);
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.ASSET, assetId, 1L, null);
        JO jsonObject = ACTestUtils.assertTransactionSuccess(builder);

        String fullHash = jsonObject.getString("fullHash");
        generateBlock();
        ACTestUtils.assertPendingTransaction(fullHash);

        APICall.Builder<?> approveBuilder = approveTransactionBuilder(jsonObject);

        ACTestUtils.assertTransactionSuccess(approveBuilder);

        generateBlock();
        ACTestUtils.assertConfirmedTransaction(fullHash);
    }

    @Test
    public void testCurrencyVoting() {
        IssueCurrencyCall issueCurrencyCall = TestCurrencyIssuance.builder("fgsha", "FGSHA", "Test AC");
        String currencyId = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(issueCurrencyCall));
        generateBlock();

        issueCurrencyCall = TestCurrencyIssuance.builder("fgshb", "FGSHB", "Test AC");
        String currency2Id = Tester.responseToStringId(ACTestUtils.assertTransactionSuccess(issueCurrencyCall));
        generateBlock();

        PhasingParamsBuilder controlParams = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.CURRENCY.getCode())
                .phasingHolding(currencyId)
                .phasingQuorum(100);
        JO response = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(controlParams.toJSONString())
                .callNoError();
        Logger.logMessage("setPhasingOnlyControl response: " + response.toJSONString());

        Assert.assertNotNull(response.getString("fullHash"));

        generateBlock();

        SendMoneyCall builder = sendMoneyBuilder();
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.CURRENCY, currency2Id, 100L, null);
        ACTestUtils.assertTransactionBlocked(builder);

        setTransactionPhasingParams(builder, 20, VotingModel.CURRENCY, currencyId, 100L, null);
        ACTestUtils.assertTransactionSuccess(builder);
    }

    @Test
    public void testPropertyVoting() {
        String propertyName = "propac1";
        String propertyValue = "prop_val";

        SetPhasingOnlyControlCall phasingOnlyControlBuilder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.senderPropertySubpoll(CHUCK, propertyName, propertyValue).toJSONString());

        new JSONAssert(phasingOnlyControlBuilder.call()).str("fullHash");
        generateBlock();

        SendMoneyCall sendMoneyCall = TestPropertyVoting.createSendMoneyPhasedBuilder();
        sendMoneyCall.phasingSenderPropertySetter(CHUCK.getStrId());
        sendMoneyCall.phasingSenderPropertyName(propertyName);
        sendMoneyCall.phasingSenderPropertyValue(propertyValue + "a");

        new JSONAssert(sendMoneyCall.call()).str("errorDescription");

        sendMoneyCall.phasingSenderPropertyValue(propertyValue);
        sendMoneyCall.phasingSenderPropertySetter(BOB.getStrId());
        new JSONAssert(sendMoneyCall.call()).str("errorDescription");

        sendMoneyCall.phasingSenderPropertySetter(CHUCK.getStrId());
        new JSONAssert(sendMoneyCall.call()).str("fullHash");

        generateBlock();
    }

    @Test
    public void testZeroMaxFees() {
        SetPhasingOnlyControlCall setPhasingOnlyControlCall = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.accountSubpoll(BOB).toJSONString())
                .controlMaxFees(IGNIS.getId() + ":0");

        new JSONAssert(setPhasingOnlyControlCall.call()).str("fullHash");
        generateBlock();

        SendMoneyCall builder = sendMoneyBuilder().recipient(CHUCK.getId()).feeNQT(1);

        setTransactionPhasingParams(builder, 20, VotingModel.ACCOUNT, null, 1L, new long[] {BOB.getId()});
        ACTestUtils.assertTransactionBlocked(builder);

        builder.feeNQT(0);
        String fullHash = new JSONAssert(ACTestUtils.assertTransactionSuccess(builder)).fullHash();
        bundleTransactions(Collections.singletonList(fullHash));

        generateBlock();

        ACTestUtils.approve(fullHash, BOB, null);

        generateBlock();

        assertEquals(IGNIS.ONE_COIN, CHUCK.getChainBalanceDiff(IGNIS.getId()));
    }

    private void assertPhasingOnly(PhasingParams expected, JO maxFees, int minDuration, int maxDuration) {
        GetPhasingOnlyControlCall getPhasingOnlyControlCall = GetPhasingOnlyControlCall.create().account(ALICE.getId());
        JSONAssert response = new JSONAssert(getPhasingOnlyControlCall.call());
        JSONAssert params = response.subObj("controlParams");
        assertEquals(expected.getVoteWeighting().getVotingModel().getCode(), ((Long) params.integer("phasingVotingModel")).byteValue());
        assertEquals(expected.getQuorum(), Convert.parseLong(params.str("phasingQuorum")));
        assertEquals(expected.getWhitelist().length, params.array("phasingWhitelist", String.class).size());
        assertEquals(expected.getVoteWeighting().getHoldingId(), Convert.parseUnsignedLong(params.str("phasingHolding")));
        assertEquals(expected.getVoteWeighting().getMinBalance(), Convert.parseLong(params.str("phasingMinBalance")));
        assertEquals(expected.getVoteWeighting().getMinBalanceModel().getCode(), ((Long) params.integer("phasingVotingModel")).byteValue());
        assertEquals(maxFees.toJSONObject(), response.subObj("maxFees").getJson());
        assertEquals(minDuration, response.integer("minDuration"));
        assertEquals(maxDuration, response.integer("maxDuration"));
    }

    private SendMoneyCall sendMoneyBuilder() {
        return SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .recipient(BOB.getId())
                .amountNQT(IGNIS.ONE_COIN);
    }

    private ApproveTransactionCall approveTransactionBuilder(JO responseJSON) {
        return ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phasedTransaction(getPhasedTransaction(responseJSON));
    }

    private void setTransactionPhasingParams(CreateOneSideTransactionCallBuilder<?> builder, int finishAfter,
                                             VotingModel votingModel, String holdingId, Long quorum, long[] whitelist) {

        builder.phased(true);
        builder.phasingVotingModel(votingModel.getCode());
        builder.phasingFinishHeight(Nxt.getBlockchain().getHeight() + finishAfter);
        builder.phasingHolding(holdingId);

        if (quorum != null) {
            builder.phasingQuorum(quorum);
        }

        if (whitelist != null) {
            builder.phasingWhitelisted(Arrays.stream(whitelist).mapToObj(Long::toUnsignedString).toArray(String[]::new));
        }
    }

    private String getPhasedTransaction(JO sendMoneyJSON) {
        return sendMoneyJSON.getJo("transactionJSON").get("chain") + ":" + sendMoneyJSON.get("fullHash");
    }

    private JO buildMaxFeesJSON(int chainId, long fees) {
        JO result = new JO();
        result.put("" + chainId, Long.toUnsignedString(fees));
        return result;
    }
}
