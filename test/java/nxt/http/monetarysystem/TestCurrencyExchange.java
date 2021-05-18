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

package nxt.http.monetarysystem;

import nxt.AccountCurrencyBalance;
import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.CurrencyBuyCall;
import nxt.http.callers.CurrencySellCall;
import nxt.http.callers.GetAllExchangesCall;
import nxt.http.callers.GetBuyOffersCall;
import nxt.http.callers.GetSellOffersCall;
import nxt.http.callers.PublishExchangeOfferCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.ms.CurrencyType;
import nxt.util.Convert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCurrencyExchange extends BlockchainTest {

    @Test
    public void buyCurrency() {
        APICall apiCall1 = TestCurrencyIssuance.builder().type(CurrencyType.EXCHANGEABLE.getCode()).build();
        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall1);
        AccountCurrencyBalance initialSellerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        AccountCurrencyBalance initialBuyerBalance = new AccountCurrencyBalance(BOB.getPrivateKey(), currencyId, IGNIS);

        Assert.assertEquals(100000, initialSellerBalance.getCurrencyUnits());
        Assert.assertEquals(100000, initialSellerBalance.getUnconfirmedCurrencyUnits());

        JO publishExchangeOfferResponse = publishExchangeOffer(currencyId);

        generateBlock();

        JO getAllOffersResponse = GetBuyOffersCall.create().currency(currencyId).callNoError();
        Logger.logDebugMessage("getAllOffersResponse:" + getAllOffersResponse.toJSONString());
        JA offer = getAllOffersResponse.getArray("offers");
        Assert.assertEquals(Tester.responseToStringId(publishExchangeOfferResponse), offer.get(0).get("offer"));

        // The buy offer reduces the unconfirmed balance but does not change the confirmed balance
        // The sell offer reduces the unconfirmed currency units and confirmed units
        AccountCurrencyBalance afterOfferSellerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(-1000*95 - IGNIS.ONE_COIN, -IGNIS.ONE_COIN, -500, 0),
                afterOfferSellerBalance.diff(initialSellerBalance));

        // buy at rate higher than sell offer results in selling at sell offer
        JO currencyExchangeResponse = CurrencyBuyCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                rateNQTPerUnit(106).
                unitsQNT(200).
                callNoError();
        Logger.logDebugMessage("currencyExchangeResponse:" + currencyExchangeResponse);
        generateBlock();

        AccountCurrencyBalance afterBuySellerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(2000, 200 * 105, 0, -200),
                afterBuySellerBalance.diff(afterOfferSellerBalance));

        AccountCurrencyBalance afterBuyBuyerBalance = new AccountCurrencyBalance(BOB.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(-200*105 - IGNIS.ONE_COIN, -200*105 - IGNIS.ONE_COIN, 200, 200),
                afterBuyBuyerBalance.diff(initialBuyerBalance));

        JO getAllExchangesResponse = GetAllExchangesCall.create().callNoError();
        Logger.logDebugMessage("getAllExchangesResponse: " + getAllExchangesResponse);
        JA exchanges = getAllExchangesResponse.getArray("exchanges");
        JO exchange = exchanges.get(0);
        Assert.assertEquals("105", exchange.get("rateNQTPerUnit"));
        Assert.assertEquals("200", exchange.get("unitsQNT"));
        Assert.assertEquals(currencyId, exchange.get("currency"));
        Assert.assertEquals(initialSellerBalance.getAccountId(), Convert.parseUnsignedLong((String)exchange.get("seller")));
        Assert.assertEquals(initialBuyerBalance.getAccountId(), Convert.parseUnsignedLong((String)exchange.get("buyer")));
    }

    @Test
    public void sellCurrency() {
        APICall apiCall1 = TestCurrencyIssuance.builder().type(CurrencyType.EXCHANGEABLE.getCode()).build();
        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall1);
        AccountCurrencyBalance initialBuyerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        AccountCurrencyBalance initialSellerBalance = new AccountCurrencyBalance(BOB.getPrivateKey(), currencyId, IGNIS);

        Assert.assertEquals(100000, initialBuyerBalance.getCurrencyUnits());
        Assert.assertEquals(100000, initialBuyerBalance.getUnconfirmedCurrencyUnits());

        JO publishExchangeOfferResponse = publishExchangeOffer(currencyId);

        generateBlock();

        JO getAllOffersResponse = GetSellOffersCall.create().currency(currencyId).callNoError();
        Logger.logDebugMessage("getAllOffersResponse:" + getAllOffersResponse.toJSONString());
        JA offer = getAllOffersResponse.getArray("offers");
        Assert.assertEquals(Tester.responseToStringId(publishExchangeOfferResponse), offer.get(0).get("offer"));

        // The buy offer reduces the unconfirmed balance but does not change the confirmed balance
        // The sell offer reduces the unconfirmed currency units and confirmed units
        AccountCurrencyBalance afterOfferBuyerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(-1000 * 95 - IGNIS.ONE_COIN, -IGNIS.ONE_COIN, -500, 0),
                afterOfferBuyerBalance.diff(initialBuyerBalance));

        // We now transfer 2000 units to the 2nd account so that this account can sell them for NXT
        TransferCurrencyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                recipient(initialSellerBalance.getAccountId()).
                unitsQNT(2000).
                callNoError();
        generateBlock();

        AccountCurrencyBalance afterTransferBuyerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(-IGNIS.ONE_COIN, -IGNIS.ONE_COIN, -2000, -2000),
                afterTransferBuyerBalance.diff(afterOfferBuyerBalance));

        AccountCurrencyBalance afterTransferSellerBalance = new AccountCurrencyBalance(BOB.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(0, 0, 2000, 2000),
                afterTransferSellerBalance.diff(initialSellerBalance));

        // sell at rate lower than buy offer results in selling at buy offer rate (95)
        JO currencyExchangeResponse = CurrencySellCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                rateNQTPerUnit(90).
                unitsQNT(200).
                callNoError();
        Logger.logDebugMessage("currencyExchangeResponse:" + currencyExchangeResponse);
        generateBlock();

        // the seller receives 200*95=19000 for 200 units
        AccountCurrencyBalance afterBuyBuyerBalance = new AccountCurrencyBalance(ALICE.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(0, -19000, 0, 200),
                afterBuyBuyerBalance.diff(afterTransferBuyerBalance));

        AccountCurrencyBalance afterBuySellerBalance = new AccountCurrencyBalance(BOB.getPrivateKey(), currencyId, IGNIS);
        Assert.assertEquals(new AccountCurrencyBalance(19000- IGNIS.ONE_COIN, 19000- IGNIS.ONE_COIN, -200, -200),
                afterBuySellerBalance.diff(afterTransferSellerBalance));

        JO getAllExchangesResponse = GetAllExchangesCall.create().callNoError();
        Logger.logDebugMessage("getAllExchangesResponse: " + getAllExchangesResponse);
        JA exchanges = getAllExchangesResponse.getArray("exchanges");
        JO exchange = exchanges.get(0);
        Assert.assertEquals("95", exchange.get("rateNQTPerUnit"));
        Assert.assertEquals("200", exchange.get("unitsQNT"));
        Assert.assertEquals(currencyId, exchange.get("currency"));
        Assert.assertEquals(initialSellerBalance.getAccountId(), Convert.parseUnsignedLong((String) exchange.get("seller")));
        Assert.assertEquals(initialBuyerBalance.getAccountId(), Convert.parseUnsignedLong((String)exchange.get("buyer")));
    }

    private JO publishExchangeOffer(String currencyId) {
        JO publishExchangeOfferResponse = PublishExchangeOfferCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                deadline(1440).
                currency(currencyId).
                buyRateNQTPerUnit(95). // buy currency for NXT
                sellRateNQTPerUnit(105). // sell currency for NXT
                totalBuyLimitQNT(10000).
                totalSellLimitQNT(5000).
                initialBuySupplyQNT(1000).
                initialSellSupplyQNT(500).
                expirationHeight(Integer.MAX_VALUE).
                call();

        Logger.logDebugMessage("publishExchangeOfferResponse: " + publishExchangeOfferResponse.toJSONString());
        return publishExchangeOfferResponse;
    }
}
