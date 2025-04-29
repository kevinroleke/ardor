/*
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

package nxt.http.bundling;

import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.chaincontrol.PermissionTestUtil;
import nxt.http.assetexchange.AssetExchangeTest;
import nxt.http.callers.CancelAskOrderCall;
import nxt.http.callers.CancelBidOrderCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.StartBundlerCall;
import nxt.http.client.PlaceAssetOrderBuilder;
import nxt.util.JSONAssert;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.blockchain.chaincontrol.PermissionType.CHAIN_USER;

public class AssetBundlerTest extends BundlerTest {

    @Test
    public void testAssetTransferBundler() {
        String assetId = issueAsset();
        String assetId1 = issueAsset();

        startAssetBundler(assetId);

        String fullHash = AssetExchangeTest.transfer(assetId, ALICE, CHUCK, 10, 0);
        Assert.assertTrue(isBundled(fullHash));

        fullHash = AssetExchangeTest.transfer(assetId1, ALICE, CHUCK, 10, 0);
        Assert.assertFalse(isBundled(fullHash));
    }

    @Test
    public void testAssetBidBundler() {
        String assetId = issueAsset();
        startAssetBundler(assetId);

        String fullHash = placeAssetOrder(ALICE, assetId, 10, IGNIS.ONE_COIN, false);
        Assert.assertTrue(isBundled(fullHash));

        fullHash = placeAssetOrder(CHUCK, assetId, 10, IGNIS.ONE_COIN, true);
        Assert.assertTrue(isBundled(fullHash));

        fullHash = placeAssetOrder(ALICE, assetId, 10, IGNIS.ONE_COIN * 2, false);
        Assert.assertTrue(isBundled(fullHash));
        String askId = Tester.hexFullHashToStringId(fullHash);

        //Won't match the ask order
        fullHash = placeAssetOrder(CHUCK, assetId, 10, IGNIS.ONE_COIN, true);
        Assert.assertTrue(isBundled(fullHash));
        String bidId = Tester.hexFullHashToStringId(fullHash);

        fullHash = cancelAssetOrder(ALICE, askId, false);
        Assert.assertTrue(isBundled(fullHash));

        fullHash = cancelAssetOrder(CHUCK, bidId, true);
        Assert.assertTrue(isBundled(fullHash));
    }

    @Test
    public void testAssetBundlerWithQuota() {
        String assetId = issueAsset();

        int quota = 4;
        JSONAssert result = new JSONAssert(StartBundlerCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                filter("AssetBundler:" + assetId, "QuotaBundler:" + quota).
                minRateNQTPerFXT(0).
                feeCalculatorName("MIN_FEE").call());
        result.str("totalFeesLimitFQT");

        String fullHash = AssetExchangeTest.transfer(assetId, ALICE, BOB, 100, 0);
        Assert.assertTrue(isBundled(fullHash));

        for (int i = 0; i < quota; i++) {
            fullHash = AssetExchangeTest.transfer(assetId, BOB, CHUCK, 10, 0);
            Assert.assertTrue(isBundled(fullHash));
        }
        //Bob's quota is over
        fullHash = AssetExchangeTest.transfer(assetId, BOB, CHUCK, 10, 0);
        Assert.assertFalse(isBundled(fullHash));

        //Chuck still has quota
        fullHash = AssetExchangeTest.transfer(assetId, CHUCK, BOB, 10, 0);
        Assert.assertTrue(isBundled(fullHash));

        //Transferring to unknown account is not allowed
        Tester unknownTester = new Tester("Unknown account secret " + System.currentTimeMillis());
        PermissionTestUtil.grantPermission(IGNIS, unknownTester, CHAIN_USER);
        fullHash = AssetExchangeTest.transfer(assetId, CHUCK, unknownTester, 10, 0);
        Assert.assertFalse(isBundled(fullHash));
    }

    @SuppressWarnings("SameParameterValue")
    private String placeAssetOrder(Tester sender, String assetId, long quantityQNT, long price, boolean isBid) {
        PlaceAssetOrderBuilder builder = new PlaceAssetOrderBuilder(sender, assetId, quantityQNT, price);
        JO result = isBid ? builder.placeBidOrder() : builder.placeAskOrder();
        generateBlock();
        return new JSONAssert(result).str("fullHash");
    }

    private String cancelAssetOrder(Tester sender, String orderId, boolean isBid) {
        JO response;
        if (isBid) {
            response = CancelBidOrderCall.create(IGNIS.getId())
                    .secretPhrase(sender.getSecretPhrase())
                    .order(orderId)
                    .feeNQT(0)
                    .callNoError();
        } else {
            response = CancelAskOrderCall.create(IGNIS.getId())
                    .secretPhrase(sender.getSecretPhrase())
                    .order(orderId)
                    .feeNQT(0)
                    .callNoError();
        }
        String result = new JSONAssert(response).str("fullHash");
        generateBlock();
        return result;
    }


    private void startAssetBundler(String assetId) {
        JSONAssert result = new JSONAssert(StartBundlerCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                filter("AssetBundler:" + assetId).
                minRateNQTPerFXT(0).
                feeCalculatorName("MIN_FEE").call());
        result.str("totalFeesLimitFQT");
    }

    private String issueAsset() {
        JSONAssert result = new JSONAssert(IssueAssetCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .name("Bundl")
                .description("asset bundle testing")
                .quantityQNT(10000000)
                .decimals(4)
                .feeNQT(1000 * IGNIS.ONE_COIN)
                .deadline(1440).call());
        String fullHash = result.str("fullHash");
        String assetId = Tester.hexFullHashToStringId(fullHash);

        bundleTransactions(Collections.singletonList(fullHash));

        generateBlock();
        return assetId;
    }
}
