/*
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

package nxt.http.bundling;

import nxt.Nxt;
import nxt.Tester;
import nxt.http.callers.DgsDeliveryCall;
import nxt.http.callers.DgsFeedbackCall;
import nxt.http.callers.DgsListingCall;
import nxt.http.callers.DgsPurchaseCall;
import nxt.http.callers.StartBundlerCall;
import nxt.util.JSONAssert;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static nxt.blockchain.ChildChain.IGNIS;

public class PurchaseBundlerTest extends BundlerTest {
    @Test
    public void testPurchaseAndFeedback() {
        startPurchaseBundler(ALICE);

        long price = IGNIS.ONE_COIN;
        JSONAssert result = new JSONAssert(DgsListingCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(0)
                .name("TestDGS")
                .quantity(10)
                .priceNQT(price).call());
        String goodsId = result.id();

        bundleTransactions(Collections.singletonList(result.fullHash()));

        generateBlock();

        result = new JSONAssert(DgsPurchaseCall.create(IGNIS.getId())
                .secretPhrase(CHUCK.getSecretPhrase())
                .feeNQT(0)
                .goods(goodsId)
                .priceNQT(price)
                .quantity(1)
                .deliveryDeadlineTimestamp(Nxt.getEpochTime() + 100).call());
        String purchaseId = result.id();
        generateBlock();
        Assert.assertTrue(isBundled(result.fullHash()));

        result = new JSONAssert(DgsDeliveryCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(0)
                .purchase(purchaseId)
                .goodsToEncrypt("a").call());
        bundleTransactions(Collections.singletonList(result.fullHash()));
        generateBlock();

        result = new JSONAssert(DgsFeedbackCall.create(IGNIS.getId())
                .secretPhrase(CHUCK.getSecretPhrase())
                .feeNQT(0)
                .purchase(purchaseId)
                .message("my feedback 123").call());
        generateBlock();
        Assert.assertTrue(isBundled(result.fullHash()));
    }

    private void startPurchaseBundler(Tester seller) {
        JSONAssert result = new JSONAssert(StartBundlerCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                filter("PurchaseBundler:" + seller.getStrId()).
                minRateNQTPerFXT(0).
                feeCalculatorName("MIN_FEE").call());
        result.str("totalFeesLimitFQT");
    }

}
