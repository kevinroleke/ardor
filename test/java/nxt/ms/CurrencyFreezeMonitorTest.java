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

package nxt.ms;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.dbschema.Db;
import nxt.http.APICall;
import nxt.http.APICall.InvocationError;
import nxt.http.callers.GetBuyOffersCall;
import nxt.http.callers.GetSellOffersCall;
import nxt.http.callers.PublishExchangeOfferCall;
import nxt.http.callers.SetAccountPropertyCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import nxt.util.JSONAssert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CurrencyFreezeMonitorTest extends BlockchainTest {
    @Ignore("corrupts database even on the first successful run")
    @Test
    public void testOnBlockFreezeCurrency() {
        Currency expectedToFreeze = createCurrencyWithOffers();

        setCurrencyFreezeHeight(expectedToFreeze, getHeight() + 1);

        generateBlock();

        assertFrozen(expectedToFreeze);
    }

    @Test
    public void testOnBlockFreezeCurrencyOnFreezeAlias() {
        Currency expectedToFreeze = createCurrencyWithOffers();

        setCurrencyFreezeHeight(expectedToFreeze, 0);

        setCurrencyFreezeAccountProperty(expectedToFreeze, getHeight() + 2);
        generateBlock();

        generateBlock();

        assertFrozen(expectedToFreeze);
    }

    private void setCurrencyFreezeAccountProperty(Currency currency, int height) {
        SetAccountPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(ALICE.getId())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property(Currency.CURRENCY_FREEZE_HEIGHT_PROPERTY_PREFIX + Long.toUnsignedString(currency.getId()))
                .value(Integer.toString(height))
                .feeNQT(ChildChain.IGNIS.ONE_COIN)
                .callNoError();
    }


    private void assertFrozen(Currency currency) {
        assertNoOffers(currency);
        assertTransactionsFail(currency);
    }

    private void assertTransactionsFail(Currency currency) {
        InvocationError actual = createTransferCurrencyCall(BOB, currency).invokeWithError();
        assertEquals(
                "Currency " + Long.toUnsignedString(currency.getId()) + " is frozen, no transaction is possible.",
                actual.getErrorDescription());
    }

    private static APICall createTransferCurrencyCall(Tester recipient, Currency currency) {
        return TransferCurrencyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(TestCurrencyIssuance.CREATOR.getSecretPhrase())
                .currency(currency.getId())
                .recipient(recipient.getStrId())
                .unitsQNT(3)
                .feeNQT(ChildChain.IGNIS.ONE_COIN)
                .build();
    }

    private void assertNoOffers(Currency currency) {
        assertEquals(new JA(), getGetSellOffers(currency).getArray("offers"));
        assertEquals(new JA(), getGetBuyOffers(currency).getArray("offers"));
    }

    private JO getGetBuyOffers(Currency currency) {
        return GetBuyOffersCall.create().currency(currency.getId()).callNoError();
    }

    private JO getGetSellOffers(Currency currency) {
        return GetSellOffersCall.create().currency(currency.getId()).callNoError();
    }

    public static void setCurrencyFreezeHeight(Currency currency, int height) {
        Db.db.runInDbTransaction(() -> CurrencyFreezeMonitor.enableFreeze(currency.getId(), 1, height));
    }

    private Currency createCurrencyWithOffers() {
        Currency currency = createCurrency();
        createOffers(currency);
        return currency;
    }

    private void createOffers(Currency currency) {
        createTransferCurrencyCall(BOB, currency).invokeNoError();
        generateBlock();

        PublishExchangeOfferCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .feeNQT(ChildChain.IGNIS.ONE_COIN)
                .currency(Long.toUnsignedString(currency.getId()))
                .buyRateNQTPerUnit(1)
                .sellRateNQTPerUnit(1)
                .totalBuyLimitQNT(1)
                .totalSellLimitQNT(1)
                .initialBuySupplyQNT(1)
                .initialSellSupplyQNT(1)
                .expirationHeight(getHeight() + 100)
                .callNoError();

        generateBlock();
    }

    public static Currency createCurrency() {
        JO jsonObject = TestCurrencyIssuance.builder().callNoError();
        String currencyId = Tester.hexFullHashToStringId(new JSONAssert(jsonObject).str("fullHash"));
        generateBlock();
        return Currency.getCurrency(Long.parseUnsignedLong(currencyId));
    }
}