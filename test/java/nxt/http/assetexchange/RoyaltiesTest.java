/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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
package nxt.http.assetexchange;

import nxt.BlockchainTest;
import nxt.Constants;
import nxt.http.callers.SetAssetTradeRoyaltiesCall;
import nxt.http.client.IssueAssetBuilder;
import nxt.http.client.PlaceAssetOrderBuilder;
import nxt.http.client.TransferAssetBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.util.Convert.unitRateToAmount;

public class RoyaltiesTest extends BlockchainTest {
    @Test
    public void testSettingRoyalties() {
        generateBlocks(Constants.TRANSACTION_TYPE_SPECIFIC_ASSET_CONTROL + 1);
        long fee = IGNIS.ONE_COIN;
        long price = IGNIS.ONE_COIN * 7;
        String royaltiesPercentage = "11.2";

        String assetId = AssetExchangeTest.issueAsset(ALICE, "royalties").getAssetIdString();

        SetAssetTradeRoyaltiesCall setAssetTradeRoyaltiesCall = SetAssetTradeRoyaltiesCall.create(IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .asset(assetId)
                .royaltiesPercentage("0")
                .feeNQT(fee);

        String errorDesc;
        errorDesc = setAssetTradeRoyaltiesCall.build().invokeWithError().getErrorDescription();
        Assert.assertEquals("Royalties can only be set by the asset issuer", errorDesc);

        setAssetTradeRoyaltiesCall.secretPhrase(ALICE.getSecretPhrase());
        errorDesc = setAssetTradeRoyaltiesCall.build().invokeWithError().getErrorDescription();
        Assert.assertEquals("Royalties percentage are already unset", errorDesc);

        setAssetTradeRoyaltiesCall.royaltiesPercentage("3").callNoError();

        generateBlock();

        setAssetTradeRoyaltiesCall.royaltiesPercentage(royaltiesPercentage).callNoError();

        generateBlock();

        new TransferAssetBuilder(assetId, ALICE, BOB)
                .setQuantityQNT(IssueAssetBuilder.ASSET_QNT)
                .feeNQT(fee)
                .transfer();

        generateBlock();

        errorDesc = setAssetTradeRoyaltiesCall.royaltiesPercentage("6").build().invokeWithError().getErrorDescription();
        Assert.assertEquals("Setting royalties requires the asset issuer to own all asset units", errorDesc);

        bobSellToChuck(fee, price, assetId);

        Assert.assertEquals(10, CHUCK.getAssetQuantityDiff(Long.parseUnsignedLong(assetId)));
        Assert.assertEquals(IssueAssetBuilder.ASSET_QNT - 10, BOB.getAssetQuantityDiff(Long.parseUnsignedLong(assetId)));

        long sellVolume = unitRateToAmount(10, IssueAssetBuilder.ASSET_DECIMALS, price, IGNIS.getDecimals());
        Assert.assertEquals(- sellVolume - fee, CHUCK.getChainBalanceDiff(IGNIS.getId()));
        long royalties = BigDecimal.valueOf(sellVolume).multiply(new BigDecimal(royaltiesPercentage)).movePointLeft(2).longValueExact();
        Assert.assertEquals(sellVolume - royalties - fee, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(royalties - fee * 3 - IssueAssetBuilder.ASSET_ISSUE_FEE_NQT, ALICE.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testRemovingRoyalties() {
        generateBlocks(Constants.TRANSACTION_TYPE_SPECIFIC_ASSET_CONTROL + 1);
        String assetId = AssetExchangeTest.issueAsset(ALICE, "royalties").getAssetIdString();
        long fee = IGNIS.ONE_COIN;
        long price = IGNIS.ONE_COIN * 7;

        SetAssetTradeRoyaltiesCall setAssetTradeRoyaltiesCall = SetAssetTradeRoyaltiesCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .asset(assetId)
                .royaltiesPercentage("5.67")
                .feeNQT(fee);
        setAssetTradeRoyaltiesCall.callNoError();

        generateBlock();

        new TransferAssetBuilder(assetId, ALICE, BOB)
                .setQuantityQNT(IssueAssetBuilder.ASSET_QNT)
                .feeNQT(fee)
                .transfer();

        generateBlock();

        setAssetTradeRoyaltiesCall.royaltiesPercentage("0").callNoError();
        generateBlock();

        bobSellToChuck(fee, price, assetId);

        //Alice should not be getting royalties
        Assert.assertEquals(- fee * 3 - IssueAssetBuilder.ASSET_ISSUE_FEE_NQT, ALICE.getChainBalanceDiff(IGNIS.getId()));
    }

    private void bobSellToChuck(long fee, long price, String assetId) {
        new PlaceAssetOrderBuilder(BOB, assetId, 10, price)
                .setFeeNQT(fee)
                .placeAskOrder();

        generateBlock();

        new PlaceAssetOrderBuilder(CHUCK, assetId, 10, price)
                .setFeeNQT(fee)
                .placeBidOrder();

        generateBlock();
    }
}
