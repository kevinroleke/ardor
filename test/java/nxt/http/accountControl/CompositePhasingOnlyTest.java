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
 
 package nxt.http.accountControl;

import nxt.BlockchainTest;
import nxt.Helper;
import nxt.Nxt;
import nxt.Tester;
import nxt.account.AccountRestrictions;
import nxt.addons.JO;
import nxt.crypto.HashFunction;
import nxt.http.PhasingParamsBuilder;
import nxt.http.PhasingParamsHelper;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.GetPhasingOnlyControlCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SetPhasingOnlyControlCall;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import nxt.voting.VoteWeighting.VotingModel;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class CompositePhasingOnlyTest extends BlockchainTest {

    @Test
    public void testSetAndGet() {
        ACTestUtils.assertNoPhasingOnlyControl();

        setSimpleCompositeControl("A", BOB);
        JSONAssert jsonAssert;

        GetPhasingOnlyControlCall queryBuilder = GetPhasingOnlyControlCall.create().account(ALICE.getId());

        jsonAssert = new JSONAssert(queryBuilder.call());
        jsonAssert.subObj("controlParams").subObj("phasingSubPolls").subObj("A");
    }

    @Test
    public void testUpdateCompositeControl() {

        ACTestUtils.assertNoPhasingOnlyControl();

        setSimpleCompositeControl("A", BOB);

        GetPhasingOnlyControlCall queryBuilder = GetPhasingOnlyControlCall.create().account(ALICE.getId());

        JSONAssert jsonAssert = new JSONAssert(queryBuilder.call());
        JSONAssert subPollsJSON = jsonAssert.subObj("controlParams").subObj("phasingSubPolls");
        subPollsJSON.subObj("A");
        Assert.assertEquals(1, subPollsJSON.getJson().size());

        //set the phasing to satisfy the previous account control
        SetPhasingOnlyControlCall builder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440)
                .feeNQT(3 * IGNIS.ONE_COIN)
                .controlParams(PhasingParamsHelper.compositeSingleAccountSubpoll("B", CHUCK).toJSONString())

                //set the phasing to satisfy the previous account control
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(PhasingParamsHelper.compositeSingleAccountSubpoll("A", BOB).toJSONString());

        jsonAssert = new JSONAssert(builder.call());
        jsonAssert.str("fullHash");
        jsonAssert.subObj("transactionJSON").subObj("attachment").subObj("phasingControlParams").subObj("phasingSubPolls").subObj("B");

        generateBlock();

        approveUpdate(jsonAssert);

        //check
        jsonAssert = new JSONAssert(queryBuilder.call());
        subPollsJSON = jsonAssert.subObj("controlParams").subObj("phasingSubPolls");
        subPollsJSON.subObj("B");
        Assert.assertEquals(1, subPollsJSON.getJson().size());
    }

    @Test
    public void testRemoveCompositeControl() {
        final String varName = "RemoveTest";
        setSimpleCompositeControl(varName, BOB);

        //set the phasing to satisfy the previous account control
        SetPhasingOnlyControlCall builder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(3 * IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.NONE.getCode())

                //set the phasing to satisfy the previous account control
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(PhasingParamsHelper.compositeSingleAccountSubpoll(varName, BOB).toJSONString());

        JSONAssert jsonAssert = new JSONAssert(builder.call());
        generateBlock();

        approveUpdate(jsonAssert);

        int heightAfterRemoval = Nxt.getBlockchain().getHeight();

        GetPhasingOnlyControlCall queryBuilder = GetPhasingOnlyControlCall.create().account(ALICE.getId());

        JO response = queryBuilder.callNoError();
        Assert.assertEquals(0, response.size());

        String varName2 = "RmTest2";
        setSimpleCompositeControl(varName2, BOB);

        jsonAssert = new JSONAssert(queryBuilder.call());
        JSONAssert subPolls = jsonAssert.subObj("controlParams").subObj("phasingSubPolls");
        subPolls.subObj(varName2);
        Assert.assertEquals(1, subPolls.getJson().size());

        Nxt.getBlockchainProcessor().popOffTo(heightAfterRemoval);

        response = queryBuilder.callNoError();
        Assert.assertEquals(0, response.size());

        setSimpleCompositeControl(varName2, BOB);
        jsonAssert = new JSONAssert(queryBuilder.call());
        subPolls = jsonAssert.subObj("controlParams").subObj("phasingSubPolls");
        subPolls.subObj(varName2);
        Assert.assertEquals(1, subPolls.getJson().size());
    }

    @Test
    public void testSubParamsEqualCheck() {
        PhasingParamsBuilder controlParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(BOB))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(DAVE, CHUCK));
        SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN).controlParams(controlParamsBuilder.toJSONString())
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440)
                .callNoError();

        generateBlock();

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(BOB))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(CHUCK));

        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        JSONAssert jsonAssert = new JSONAssert(builder.call());
        Logger.logDebugMessage("response = " + jsonAssert.getJson().toJSONString());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Sub-poll for variable \"B\" does not match"));

        PhasingParamsBuilder subpollB = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.ACCOUNT.getCode())
                .phasingWhitelisted(DAVE.getStrId(), CHUCK.getStrId())
                .phasingQuorum(2);
        phasingParamsBuilder.setSubPoll("B", subpollB);
        builder.phasingParams(phasingParamsBuilder.toJSONString());
        jsonAssert = new JSONAssert(builder.call());
        Logger.logDebugMessage("response = " + jsonAssert.getJson().toJSONString());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Sub-poll for variable \"B\" does not match"));

        phasingParamsBuilder.setSubPoll("B", subpollB.phasingQuorum(1));
        builder.phasingParams(phasingParamsBuilder.toJSONString());
        jsonAssert = new JSONAssert(builder.call());
        Logger.logDebugMessage("response = " + jsonAssert.getJson().toJSONString());
        jsonAssert.str("fullHash");
        generateBlock();
    }

    @Test
    public void testCompositeControlImplication() {
        PhasingParamsBuilder controlParams = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(BOB))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(DAVE, CHUCK));

        SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440).controlParams(controlParams.toJSONString())
                .callNoError();

        generateBlock();

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsHelper.compositeSingleAccountSubpoll("A", BOB);
        SendMoneyCall txBuilder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        JSONAssert jsonAssert = new JSONAssert(txBuilder.call());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Phasing expression does not imply the account control expression"));

        PhasingParamsBuilder subpollC = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.HASH.getCode())
                .phasingHashedSecret(HashFunction.SHA256.hash("somesecret".getBytes()))
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingQuorum(1);
        phasingParamsBuilder.phasingExpression("A & B | C")
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(DAVE, CHUCK))
                .setSubPoll("C", subpollC);
        txBuilder.phasingParams(phasingParamsBuilder.toJSONString());

        jsonAssert = new JSONAssert(txBuilder.call());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Phasing expression does not imply the account control expression"));

        txBuilder.phasingParams(phasingParamsBuilder.phasingExpression("A & B & C").toJSONString());
        jsonAssert = new JSONAssert(txBuilder.call());
        jsonAssert.str("fullHash");
    }

    @Test
    public void testSimpleControl() {
        SetPhasingOnlyControlCall builder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN).controlParams(PhasingParamsHelper.accountSubpoll(BOB, CHUCK).toJSONString());
        new JSONAssert(builder.call()).str("fullHash");
        generateBlock();

        final String AC_VAR = AccountRestrictions.PhasingOnly.DEFAULT_ACCOUNT_CONTROL_VARIABLE;
        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingExpression(AC_VAR)
                .phasingQuorum(1)
                .setSubPoll(AC_VAR, PhasingParamsHelper.accountSubpoll(BOB, CHUCK).phasingQuorum(2));
        SendMoneyCall txBuilder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .recipient(DAVE.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString());

        JSONAssert jsonAssert = new JSONAssert(txBuilder.call());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Sub-poll for variable \"" + AC_VAR + "\" does not match"));

        phasingParamsBuilder.phasingExpression(AC_VAR + " | B")
                .setSubPoll(AC_VAR, PhasingParamsHelper.accountSubpoll(BOB, CHUCK))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(DAVE));
        txBuilder.phasingParams(phasingParamsBuilder.toJSONString());

        jsonAssert = new JSONAssert(txBuilder.call());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Phasing expression does not imply the account control expression"));

        txBuilder.phasingParams(phasingParamsBuilder.phasingExpression(AC_VAR + " & B").toJSONString());

        jsonAssert = new JSONAssert(txBuilder.call());
        jsonAssert.str("fullHash");
    }

    @Test
    public void testPropertyVoting() {
        String propertyName = "propac2";
        String propertyValue = "valX";

        PhasingParamsBuilder subpollB = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.PROPERTY.getCode())
                .phasingSenderProperty(CHUCK.getId(), propertyName, propertyValue)
                .phasingQuorum(1);

        PhasingParamsBuilder controlParams = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(BOB))
                .setSubPoll("B", subpollB);

        SetPhasingOnlyControlCall control = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440).controlParams(controlParams.toJSONString());

        JSONAssert jsonAssert = new JSONAssert(control.call());
        jsonAssert.str("fullHash");

        generateBlock();

        PhasingParamsBuilder phasingParams = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(BOB))
                .setSubPoll("B", subpollB.phasingSenderProperty(CHUCK.getId(), propertyName, propertyValue + "a"));

        SendMoneyCall txBuilder = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .recipient(DAVE.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5).phasingParams(phasingParams.toJSONString());

        jsonAssert = new JSONAssert(txBuilder.call());
        Assert.assertTrue(jsonAssert.str("errorDescription").startsWith("Sub-poll for variable \"B\" does not match"));

        txBuilder.phasingParams(phasingParams.setSubPoll("B", subpollB.phasingSenderProperty(CHUCK.getId(), propertyName, propertyValue))
                .toJSONString());
        jsonAssert = new JSONAssert(txBuilder.call());
        jsonAssert.str("fullHash");
    }

    @Test
    public void testSubPollsDeletion() {
        PhasingParamsBuilder controlParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A | B")
                .setSubPoll("A", PhasingParamsHelper.accountSubpoll(BOB))
                .setSubPoll("B", PhasingParamsHelper.accountSubpoll(CHUCK));
        SetPhasingOnlyControlCall setPhasingOnlyControlCall = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN).controlParams(controlParamsBuilder.toJSONString())
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440);
        setPhasingOnlyControlCall.callNoError();

        generateBlock();

        Assert.assertEquals(2, Helper.getCount("account_control_phasing_sub_poll WHERE account_id = " + ALICE.getId()));

        JSONAssert updateResult = new JSONAssert(SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN).controlVotingModel(VotingModel.NONE.getCode())
                .phased(true).phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(controlParamsBuilder.toJSONString()).callNoError());
        generateBlock();

        approveUpdate(updateResult);
        generateBlock();

        Assert.assertTrue(new JSONAssert(GetPhasingOnlyControlCall.create().account(ALICE.getId()).callNoError())
                .getJson().isEmpty());

        Assert.assertEquals(4, Helper.getCount("account_control_phasing_sub_poll " +
                " WHERE account_id = " + ALICE.getId() + " AND latest = FALSE"));

        Assert.assertEquals(0, Helper.getCount("account_control_phasing_sub_poll " +
                " WHERE account_id = " + ALICE.getId() + " AND latest = TRUE"));

        setPhasingOnlyControlCall.callNoError();
        generateBlock();

        Assert.assertEquals(4, Helper.getCount("account_control_phasing_sub_poll " +
                " WHERE account_id = " + ALICE.getId() + " AND latest = FALSE"));

        Assert.assertEquals(2, Helper.getCount("account_control_phasing_sub_poll " +
                " WHERE account_id = " + ALICE.getId() + " AND latest = TRUE"));
    }

    private void setSimpleCompositeControl(String variableName, Tester whitelisted) {
        SetPhasingOnlyControlCall builder = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlMaxFees(IGNIS.getId() + ":" + 10 * IGNIS.ONE_COIN)
                .controlMinDuration(5)
                .controlMaxDuration(1440)
                .controlParams(PhasingParamsHelper.compositeSingleAccountSubpoll(variableName, whitelisted)
                        .toJSONString());

        JSONAssert jsonAssert = new JSONAssert(builder.call());
        jsonAssert.str("fullHash");
        jsonAssert.subObj("transactionJSON").subObj("attachment").subObj("phasingControlParams").subObj("phasingSubPolls").subObj(variableName);

        generateBlock();
    }

    private void approveUpdate(JSONAssert updateResponse) {
        //approve the update
        ApproveTransactionCall builder = ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phasedTransaction("" + updateResponse.subObj("transactionJSON").integer("chain") + ":" + updateResponse.str("fullHash"));
        updateResponse = new JSONAssert(builder.call());
        updateResponse.str("fullHash");
        generateBlock();
    }
}
