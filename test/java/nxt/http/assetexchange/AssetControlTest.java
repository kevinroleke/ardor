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

package nxt.http.assetexchange;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.RequireNonePermissionPolicyTestsCategory;
import nxt.account.HoldingType;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.http.PhasingParamsBuilder;
import nxt.http.PhasingParamsHelper;
import nxt.http.accountControl.ACTestUtils;
import nxt.http.callers.CreateOneSideTransactionCallBuilder;
import nxt.http.callers.DividendPaymentCall;
import nxt.http.callers.GetPhasingAssetControlCall;
import nxt.http.callers.SetPhasingAssetControlCall;
import nxt.http.callers.SetPhasingOnlyControlCall;
import nxt.http.callers.ShufflingCreateCall;
import nxt.http.callers.TransferAssetCall;
import nxt.http.client.IssueAssetBuilder;
import nxt.http.client.TransferAssetBuilder;
import nxt.http.twophased.TestPropertyVoting;
import nxt.util.JSONAssert;
import nxt.voting.VoteWeighting;
import nxt.voting.VoteWeighting.VotingModel;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.math.BigDecimal;

import static nxt.blockchain.ChildChain.IGNIS;

public class AssetControlTest extends BlockchainTest {
    @Test
    public void testSetAndGet() {
        String assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();

        SetPhasingAssetControlCall builder = SetPhasingAssetControlCall.create(IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.ACCOUNT.getCode())
                .controlWhitelisted(CHUCK.getStrId())
                .controlQuorum(1)
                .asset(assetId);

        JSONAssert jsonAssert = new JSONAssert(builder.call());

        Assert.assertEquals("Asset control can only be set by the asset issuer", jsonAssert.str("errorDescription"));

        builder.secretPhrase(ALICE.getSecretPhrase());
        new JSONAssert(builder.call()).str("fullHash");

        generateBlock();

        JSONAssert controlParams = new JSONAssert(GetPhasingAssetControlCall.create().asset(assetId).call()).subObj("controlParams");
        Assert.assertEquals(VotingModel.ACCOUNT.getCode(), ((Long) controlParams.integer("phasingVotingModel")).byteValue());
    }

    @Test
    public void testSimpleTransfer() {
        String assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();

        SetPhasingAssetControlCall builder = SetPhasingAssetControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.ACCOUNT.getCode())
                .controlWhitelisted(CHUCK.getStrId())
                .controlQuorum(1)
                .asset(assetId);

        new JSONAssert(builder.call()).str("fullHash");

        generateBlock();

        int amount = 100 * 10000;
        String errorDescription = new TransferAssetBuilder(assetId, ALICE, BOB)
                .setQuantityQNT(amount)
                .transferWithError()
                .getErrorDescription();
        Assert.assertEquals("Non-phased transaction when phasing asset control is enabled", errorDescription);

        TransferAssetCall transferAssetCall = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingVotingModel(VotingModel.ACCOUNT.getCode())
                .phasingWhitelisted(CHUCK.getStrId())
                .phasingQuorum(1)
                .recipient(BOB.getRsAccount())
                .asset(assetId)
                .quantityQNT(amount);
        String fullHash = new JSONAssert(transferAssetCall.call()).str("fullHash");

        generateBlock();

        ACTestUtils.approve(fullHash, CHUCK, null);

        generateBlocks(4);

        Assert.assertEquals(amount, BOB.getAssetQuantityDiff(Long.parseUnsignedLong(assetId)));
    }

    @Test
    public void testSimpleAssetAndAccountControl() {
        String assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();

        SetPhasingAssetControlCall builder = SetPhasingAssetControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.ACCOUNT.getCode())
                .controlWhitelisted(CHUCK.getStrId())
                .controlQuorum(1)
                .asset(assetId);

        new JSONAssert(builder.call()).str("fullHash");

        generateBlock();

        //also set account control on alice
        SetPhasingOnlyControlCall setPhasingOnlyControlCall = SetPhasingOnlyControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                .controlWhitelisted(DAVE.getStrId())
                .controlQuorum(1);
        new JSONAssert(setPhasingOnlyControlCall.call()).str("fullHash");

        generateBlock();

        //now, in order to transfer the asset, alice needs to issue the transaction with composite phasing which implies
        //both the asset and the account controls
        int amount = 100 * 10000;

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VotingModel.COMPOSITE.getCode())
                .phasingExpression("ACC & ASC")
                .phasingQuorum(1)
                .setSubPoll("ACC", PhasingParamsHelper.accountSubpoll(DAVE))
                .setSubPoll("ASC", PhasingParamsHelper.accountSubpoll(CHUCK));

        JO response = TransferAssetCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingParams(phasingParamsBuilder.toJSONString())
                .feeNQT(ChildChain.IGNIS.ONE_COIN * 5)
                .recipient(BOB.getRsAccount())
                .asset(assetId)
                .quantityQNT(amount)
                .callNoError();

        String fullHash = new JSONAssert(response).str("fullHash");

        generateBlock();

        ACTestUtils.approve(fullHash, CHUCK, null);
        ACTestUtils.approve(fullHash, DAVE, null);

        generateBlocks(4);

        Assert.assertEquals(amount, BOB.getAssetQuantityDiff(Long.parseUnsignedLong(assetId)));
    }

    @Test
    public void testAssetControlByProperty() {
        String propertyName = "propac2";
        String propertyValue = "valX";

        String assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();

        SetPhasingAssetControlCall control = createByPropertyPhasingBuilder(propertyName, propertyValue, assetId);

        new JSONAssert(control.call()).str("fullHash");

        generateBlock();

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, BOB, propertyName, propertyValue).callNoError();

        generateBlock();

        int amount = 100 * 10000;
        TransferAssetCall transferAssetCall = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .phasingVotingModel(VotingModel.PROPERTY.getCode())
                .phasingQuorum(1)
                .phasingSenderPropertySetter(CHUCK.getStrId())
                .phasingSenderPropertyName(propertyName)
                .phasingSenderPropertyValue(propertyValue)
                .recipient(BOB.getRsAccount())
                .asset(assetId)
                .quantityQNT(amount);

        Assert.assertTrue( new JSONAssert(transferAssetCall.call()).str("errorDescription").
                startsWith("Phasing parameters do not match phasing asset control."));

        transferAssetCall.phasingRecipientPropertySetter(CHUCK.getStrId());
        transferAssetCall.phasingRecipientPropertyName(propertyName);
        transferAssetCall.phasingRecipientPropertyValue(propertyValue);

        new JSONAssert(transferAssetCall.call()).str("fullHash");

        generateBlock();

        Assert.assertEquals(amount, BOB.getAssetQuantityDiff(Long.parseUnsignedLong(assetId)));
    }

    @Category(RequireNonePermissionPolicyTestsCategory.class)
    @Test
    public void testDividendPaymentOnControlledAsset() {
        String propertyName = "propac2";
        String propertyValue = "valX";
        String propertyValue2 = "valY";

        String controlledAssetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();

        SetPhasingAssetControlCall control = createByPropertyPhasingBuilder(propertyName, propertyValue, controlledAssetId);

        new JSONAssert(control.call()).str("fullHash");

        generateBlock();

        String dividendAssetId = AssetExchangeTest.issueAsset(ALICE, "AssetD").getAssetIdString();

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();
        TestPropertyVoting.createSetPropertyBuilder(CHUCK, BOB, propertyName, propertyValue).callNoError();
        generateBlock();

        int amount = 100 * 10000;

        TransferAssetCall transferAssetCall = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getRsAccount())
                .asset(controlledAssetId)
                .quantityQNT(amount);
        setupByPropertyPhasing(propertyName, propertyValue, transferAssetCall);

        new JSONAssert(transferAssetCall.call()).str("fullHash");

        generateBlock();

        Assert.assertEquals(amount, BOB.getAssetQuantityDiff(Long.parseUnsignedLong(controlledAssetId)));

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue2).callNoError();
        TestPropertyVoting.createSetPropertyBuilder(CHUCK, BOB, propertyName, propertyValue2).callNoError();

        //asset control is not enforced on the asset for which a dividend is payed, but on the asset with which it is payed (if any)
        new JSONAssert(AssetExchangeTest.payDividend(controlledAssetId, ALICE, Nxt.getBlockchain().getHeight(), 1,
                ChildChain.IGNIS, HoldingType.ASSET.getCode(), dividendAssetId)).str("fullHash");

        //BOB owns all the distributed control assets, which is (amount / 10 ^ AssetExchangeTest.ASSET_DECIMALS)
        Assert.assertEquals(BigDecimal.valueOf(amount, IssueAssetBuilder.ASSET_DECIMALS).longValue(), BOB.getAssetQuantityDiff(Long.parseUnsignedLong(dividendAssetId)));

        dividendAssetId = AssetExchangeTest.issueAsset(ALICE, "AssetE").getAssetIdString();

        control = createByPropertyPhasingBuilder(propertyName, propertyValue, dividendAssetId);

        new JSONAssert(control.call()).str("fullHash");

        generateBlocks(10);

        APICall.InvocationError payment = AssetExchangeTest.failToPayDividend(controlledAssetId, ALICE, Nxt.getBlockchain().getHeight(), 1,
                ChildChain.IGNIS, HoldingType.ASSET.getCode(), dividendAssetId);

        Assert.assertEquals("Non-phased transaction when phasing asset control is enabled", payment.getErrorDescription());

        TestPropertyVoting.createSetPropertyBuilder(CHUCK, ALICE, propertyName, propertyValue).callNoError();

        DividendPaymentCall builder = DividendPaymentCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .asset(controlledAssetId)
                .height(Nxt.getBlockchain().getHeight())
                .holdingType(HoldingType.ASSET.getCode())
                .holding(dividendAssetId)
                .amountNQTPerShare(1);
        setupByPropertyPhasing(propertyName, propertyValue, builder);

        Assert.assertEquals("Dividend payment with asset under by-recipient property control is not supported",
                new JSONAssert(builder.call()).str("errorDescription"));
    }

    @Category(RequireNonePermissionPolicyTestsCategory.class)
    @Test
    public void testShufflingOfControlledAsset() {
        String propertyName = "propac2";
        String propertyValue = "valX";

        String controlledAssetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();

        SetPhasingAssetControlCall control = createByPropertyPhasingBuilder(propertyName, propertyValue, controlledAssetId);

        new JSONAssert(control.call()).str("fullHash");

        generateBlock();

        int amount = 100 * 10000;

        TransferAssetCall transferAssetCall = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getRsAccount())
                .asset(controlledAssetId)
                .quantityQNT(amount);
        setupByPropertyPhasing(propertyName, propertyValue, transferAssetCall);
        new JSONAssert(transferAssetCall.call()).str("fullHash");

        ShufflingCreateCall builder = ShufflingCreateCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .amount(10*1000)
                .holding(controlledAssetId)
                .holdingType(HoldingType.ASSET.getCode())
                .participantCount((byte) 3)
                .registrationPeriod(10);
        setupByPropertyPhasing(propertyName, propertyValue, builder);
        Assert.assertEquals("Shuffling of asset under asset control is not supported",
                new JSONAssert(builder.call()).str("errorDescription"));
    }

    @Test
    public void testSetOnDistributedAsset() {
        String propertyName = "propac2";
        String propertyValue = "valX";

        String controlledAssetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();
        AssetExchangeTest.transfer(controlledAssetId, ALICE, BOB, 10*10000);

        SetPhasingAssetControlCall control = createByPropertyPhasingBuilder(propertyName, propertyValue, controlledAssetId);

        Assert.assertEquals("Adding asset control requires the asset issuer to own all asset units",
                new JSONAssert(control.call()).str("errorDescription"));
    }

    @Test
    public void testRemoveControl() {
        String propertyName = "propac2";
        String propertyValue = "valX";
        SetPhasingAssetControlCall control;

        String controlledAssetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetIdString();
        control = SetPhasingAssetControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.NONE.getCode())
                .asset(controlledAssetId);

        Assert.assertEquals("Phasing asset control is not currently enabled",
                new JSONAssert(control.call()).str("errorDescription"));

        setupByPropertyPhasing(propertyName, propertyValue, control);
        new JSONAssert(control.call()).str("fullHash");
        generateBlock();

        //remove the asset control
        control = SetPhasingAssetControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .controlVotingModel(VotingModel.NONE.getCode())
                .asset(controlledAssetId);
        new JSONAssert(control.call()).str("fullHash");
        generateBlock();

        AssetExchangeTest.transfer(controlledAssetId, ALICE, BOB, 10*10000);

    }

    private SetPhasingAssetControlCall createByPropertyPhasingBuilder(String propertyName, String propertyValue, String assetId) {
        SetPhasingAssetControlCall control = SetPhasingAssetControlCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN);
        setupByPropertyPhasing(propertyName, propertyValue, control);
        control.asset(assetId);
        return control;
    }

    private void setupByPropertyPhasing(String propertyName, String propertyValue, CreateOneSideTransactionCallBuilder<?> builder) {
        builder.phasingVotingModel(VotingModel.PROPERTY.getCode());
        builder.phasingQuorum(1);
        builder.phasingSenderPropertySetter(CHUCK.getStrId());
        builder.phasingSenderPropertyName(propertyName);
        builder.phasingSenderPropertyValue(propertyValue);
        builder.phasingRecipientPropertySetter(CHUCK.getStrId());
        builder.phasingRecipientPropertyName(propertyName);
        builder.phasingRecipientPropertyValue(propertyValue);
    }

    private void setupByPropertyPhasing(String propertyName, String propertyValue, SetPhasingAssetControlCall builder) {
        builder.controlVotingModel(VotingModel.PROPERTY.getCode())
                .controlQuorum(1)
                .controlSenderPropertySetter(CHUCK.getStrId())
                .controlSenderPropertyName(propertyName)
                .controlSenderPropertyValue(propertyValue)
                .controlRecipientPropertySetter(CHUCK.getStrId())
                .controlRecipientPropertyName(propertyName)
                .controlRecipientPropertyValue(propertyValue);
    }

}
