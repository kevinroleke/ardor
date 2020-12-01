/*
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

package nxt.http.bundling;

import nxt.Nxt;
import nxt.Tester;
import nxt.http.callers.CurrencyBuyCall;
import nxt.http.callers.CurrencyReserveClaimCall;
import nxt.http.callers.CurrencyReserveIncreaseCall;
import nxt.http.callers.CurrencySellCall;
import nxt.http.callers.PublishExchangeOfferCall;
import nxt.http.callers.StartBundlerCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import nxt.ms.CurrencyType;
import nxt.util.JSONAssert;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static nxt.blockchain.ChildChain.IGNIS;

public class CurrencyBundlerTest extends BundlerTest {

    @Test
    public void testTransfer() {
        JSONAssert result = new JSONAssert(TestCurrencyIssuance.builder().
                type(CurrencyType.EXCHANGEABLE.getCode()).
                initialSupplyQNT(100000).call());
        String fullHash = result.str("fullHash");
        String currencyId = bundleIssueCurrency(fullHash);

        startCurrencyBundler(currencyId);

        result = new JSONAssert(TransferCurrencyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                currency(currencyId).
                recipient(BOB.getStrId()).
                unitsQNT(2000).
                feeNQT(0).call());
        fullHash = result.str("fullHash");
        generateBlock();
        Assert.assertTrue(isBundled(fullHash));
    }

    @Test
    public void testExchange() {
        JSONAssert result = new JSONAssert(TestCurrencyIssuance.builder().
                type(CurrencyType.EXCHANGEABLE.getCode()).
                initialSupplyQNT(100000).call());
        String fullHash = result.str("fullHash");
        String currencyId = bundleIssueCurrency(fullHash);

        startCurrencyBundler(currencyId);

        result = new JSONAssert(PublishExchangeOfferCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(0).
                deadline(1440).
                currency(currencyId).
                buyRateNQTPerUnit(95). // buy currency for NXT
                sellRateNQTPerUnit(105). // sell currency for NXT
                totalBuyLimitQNT(10000).
                totalSellLimitQNT(5000).
                initialBuySupplyQNT(1000).
                initialSellSupplyQNT(500).
                expirationHeight(Integer.MAX_VALUE).call());
        fullHash = result.str("fullHash");
        generateBlock();
        Assert.assertTrue(isBundled(fullHash));

        result = new JSONAssert(CurrencyBuyCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).feeNQT(0).
                currency(currencyId).
                rateNQTPerUnit(106).
                unitsQNT(200).call());
        fullHash = result.str("fullHash");
        generateBlock();
        Assert.assertTrue(isBundled(fullHash));

        result = new JSONAssert(CurrencySellCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).feeNQT(0).
                currency(currencyId).
                rateNQTPerUnit(94).
                unitsQNT(200).call());
        fullHash = result.str("fullHash");
        generateBlock();
        Assert.assertTrue(isBundled(fullHash));
    }

    @Test
    public void testReservable() {
        JSONAssert result = new JSONAssert(TestCurrencyIssuance.builder().
                type(CurrencyType.RESERVABLE.getCode() | CurrencyType.CLAIMABLE.getCode()).
                issuanceHeight(Nxt.getBlockchain().getHeight() + 5).
                minReservePerUnitNQT(1).
                initialSupplyQNT(0).
                reserveSupplyQNT(100000).call());
        String fullHash = result.str("fullHash");
        String currencyId = bundleIssueCurrency(fullHash);

        startCurrencyBundler(currencyId);

        result = new JSONAssert(CurrencyReserveIncreaseCall.create(IGNIS.getId()).
                secretPhrase(CHUCK.getSecretPhrase()).
                feeNQT(0).
                currency(currencyId).
                amountPerUnitNQT(2).call());
        fullHash = result.str("fullHash");
        generateBlock();
        Assert.assertTrue(isBundled(fullHash));

        generateBlocks(5);

        result = new JSONAssert(CurrencyReserveClaimCall.create(IGNIS.getId()).
                secretPhrase(CHUCK.getSecretPhrase()).
                feeNQT(0).
                currency(currencyId).
                unitsQNT(2000).call());
        fullHash = result.str("fullHash");
        generateBlock();
        Assert.assertTrue(isBundled(fullHash));
    }

    private String bundleIssueCurrency(String fullHash) {
        String currencyId = Tester.hexFullHashToStringId(fullHash);

        bundleTransactions(Collections.singletonList(fullHash));

        generateBlock();
        return currencyId;
    }

    private void startCurrencyBundler(String currencyId) {
        JSONAssert result = new JSONAssert(StartBundlerCall.create().
                secretPhrase(BOB.getSecretPhrase()).
                chain(IGNIS.getId()).
                filter("CurrencyBundler:" + currencyId).
                minRateNQTPerFXT(0).
                feeCalculatorName("MIN_FEE").call());
        result.str("totalFeesLimitFQT");
    }
}
