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
package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.addons.JO;
import nxt.http.accountControl.ACTestUtils;
import nxt.http.callers.GetAccountCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SetAccountInfoCall;
import nxt.http.callers.SetAccountPropertyCall;
import nxt.util.JSONAssert;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestPropertyVoting extends BlockchainTest {
    @Test
    public void testFinishAtCreation() {
        String propertyName = "prop1";
        String propertyValue = "prop_val";

        createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        generateBlock();

        SendMoneyCall sendMoneyCall = createSendMoneyPhasedBuilder();
        sendMoneyCall.phasingSenderPropertySetter(CHUCK.getStrId());
        sendMoneyCall.phasingSenderPropertyName(propertyName);
        sendMoneyCall.phasingSenderPropertyValue(propertyValue);
        sendMoneyCall.callNoError();
        generateBlock();

        //Finished
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));

    }

    @Test
    public void testApproveAfterCreation() {
        String propertyName = "prop2";
        String propertyValue = "prop_val";

        SendMoneyCall sendMoneyCall = createSendMoneyPhasedBuilder();
        sendMoneyCall.phasingSenderPropertySetter(CHUCK.getStrId());
        sendMoneyCall.phasingSenderPropertyName(propertyName);
        sendMoneyCall.phasingSenderPropertyValue(propertyValue);
        sendMoneyCall.callNoError();
        generateBlock();

        createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        generateBlock();

        //Not finished yet
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));

        generateBlocks(5);
        //Finished
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testRevokeApproval() {
        String propertyName = "prop2";
        String propertyValue = "prop_val";

        SendMoneyCall sendMoneyCall = createSendMoneyPhasedBuilder();
        sendMoneyCall.phasingSenderPropertySetter(CHUCK.getStrId());
        sendMoneyCall.phasingSenderPropertyName(propertyName);
        sendMoneyCall.phasingSenderPropertyValue(propertyValue);

        String fullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");
        generateBlock();

        createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        generateBlock();

        Assert.assertEquals(ACTestUtils.PhasingStatus.PENDING, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));

        createSetPropertyBuilder(CHUCK, ALICE, propertyName, "").callNoError();
        generateBlock();

        generateBlocks(5);

        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testRecipientProperty() {
        String propertyName = "prop1";
        String propertyValue = "prop_val";

        //set the property on the sender
        createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        generateBlock();

        SendMoneyCall sendMoneyCall = createSendMoneyPhasedBuilder();
        sendMoneyCall.phasingRecipientPropertySetter(CHUCK.getStrId());
        sendMoneyCall.phasingRecipientPropertyName(propertyName);
        sendMoneyCall.phasingRecipientPropertyValue(propertyValue);

        String fullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");
        generateBlocks(7);

        Assert.assertEquals(ACTestUtils.PhasingStatus.REJECTED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(0, BOB.getChainBalanceDiff(IGNIS.getId()));

        //set the property on the recipient
        createSetPropertyBuilder(CHUCK, BOB, propertyName, propertyValue).callNoError();
        generateBlock();

        sendMoneyCall.phasingFinishHeight(Nxt.getBlockchain().getHeight() + 7);
        fullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");

        generateBlock();

        Assert.assertEquals(ACTestUtils.PhasingStatus.APPROVED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));

        //transaction without recipient must be executed immediately when phased by recipient property only
        propertyName = "prop2";
        String accountName = "AliceName";
        JO response = SetAccountInfoCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingVotingModel(VoteWeighting.VotingModel.PROPERTY.getCode())
                .phasingQuorum(1)
                .feeNQT(3 * IGNIS.ONE_COIN).phasingFinishHeight(Nxt.getBlockchain().getHeight() + 7)
                .name(accountName)
                .phasingRecipientPropertySetter(CHUCK.getStrId())
                .phasingRecipientPropertyName(propertyName)
                .phasingRecipientPropertyValue(propertyValue)
                .callNoError();

        new JSONAssert(response).str("fullHash");

        generateBlock();

        JSONAssert aliceInfo = new JSONAssert(GetAccountCall.create().account(ALICE.getStrId()).call());

        Assert.assertEquals(accountName, aliceInfo.str("name"));
    }

    @Test
    public void testEmptyPropertyValue() {
        String propertyName = "prop1";

        //set the property on the sender
        createSetPropertyBuilder(CHUCK, ALICE, propertyName, "doesn't matter").callNoError();
        generateBlock();

        SendMoneyCall phasingBuilder = createSendMoneyPhasedBuilder()
                .phasingSenderPropertySetter(CHUCK.getStrId())
                .phasingSenderPropertyName(propertyName)
                .phasingSenderPropertyValue("");

        String fullHash = new JSONAssert(phasingBuilder.call()).str("fullHash");

        generateBlock();

        Assert.assertEquals(ACTestUtils.PhasingStatus.APPROVED, ACTestUtils.getPhasingStatus(fullHash));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testPropertyValueLength() {
        String specialChar = "€";
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < 42; i++) {
            sb.append(specialChar);
        }

        String propertyName = "prop1";
        String propertyValue = sb.toString();

        createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        generateBlock();

        SendMoneyCall phasingBuilder = createSendMoneyPhasedBuilder()
                .phasingSenderPropertySetter(CHUCK.getStrId())
                .phasingSenderPropertyName(propertyName)
                .phasingSenderPropertyValue(propertyValue + specialChar);

        JSONAssert result = new JSONAssert(phasingBuilder.call());
        Assert.assertTrue(result.str("errorDescription").contains("Invalid SenderPropertyValue"));

        phasingBuilder.phasingSenderPropertyValue(propertyValue);
        result = new JSONAssert(phasingBuilder.call());
        result.fullHash();
    }

    public static SetAccountPropertyCall createSetPropertyBuilder(Tester setter, Tester recipient, String name, String value) {
        return SetAccountPropertyCall.create(IGNIS.getId()).
                secretPhrase(setter.getSecretPhrase()).
                recipient(recipient.getStrId()).
                feeNQT(3 * IGNIS.ONE_COIN).
                property(name).
                value(value);
    }

    public static SendMoneyCall createSendMoneyPhasedBuilder() {
        return SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .phased(true)
                .phasingVotingModel(VoteWeighting.VotingModel.PROPERTY.getCode())
                .phasingQuorum(1)
                .feeNQT(3 * IGNIS.ONE_COIN)
                .recipient(BOB.getStrId())
                .amountNQT(100 * IGNIS.ONE_COIN)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 7);
    }
}
