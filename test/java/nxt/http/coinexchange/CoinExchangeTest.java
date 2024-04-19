/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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

package nxt.http.coinexchange;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.FxtChain;
import nxt.http.APICall.InvocationError;
import nxt.http.callers.ExchangeCoinsCall;
import nxt.http.callers.GetCoinExchangeOrderCall;
import nxt.http.callers.GetCoinExchangeTradesCall;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.AEUR;
import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.blockchain.FxtChain.FXT;

public class CoinExchangeTest extends BlockchainTest {

    @Test
    public void simpleExchange() {
        // Want to buy 25 AEUR with a maximum price of 4 IGNIS per AEUR
        // Convert the amount to IGNIS
        long displayAEURAmount = 25;
        long quantityQNT = displayAEURAmount * AEUR.ONE_COIN;
        long displayIgnisPerAEURPrice = 4;
        long priceNQT = displayIgnisPerAEURPrice * IGNIS.ONE_COIN;

        // Submit request to buy 25 AEUR with a maximum price of 4 IGNIS per AEUR
        // Quantity is denominated in AEUR and price is denominated in IGNIS per whole AEUR
        JO orderCreatedAlice = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                exchange(AEUR.getId()).
                quantityQNT(quantityQNT).
                priceNQTPerCoin(priceNQT).
                callNoError();
        Logger.logDebugMessage("exchangeCoins: " + orderCreatedAlice);
        generateBlock();

        JO orderInfoAlice = GetCoinExchangeOrderCall.create().
                order(getOrderId(orderCreatedAlice)).
                callNoError();
        Assert.assertEquals(Long.toString(25 * AEUR.ONE_COIN), orderInfoAlice.get("quantityQNT"));
        Assert.assertEquals(Long.toString(4 * IGNIS.ONE_COIN), orderInfoAlice.get("bidNQTPerCoin"));
        Assert.assertEquals(Long.toString((long) (1.0 / 4 * AEUR.ONE_COIN)), orderInfoAlice.get("askNQTPerCoin"));

        // Want to buy 110 IGNIS with a maximum price of 1/4 AEUR per IGNIS
        // Quantity is denominated in IGNIS price is denominated in AEUR per whole IGNIS
        JO orderCreatedBob = ExchangeCoinsCall.create(AEUR.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeRateNQTPerFXT(AEUR.ONE_COIN).
                exchange(IGNIS.getId()).
                quantityQNT(100 * IGNIS.ONE_COIN + 10 * IGNIS.ONE_COIN).
                priceNQTPerCoin(AEUR.ONE_COIN / 4).
                callNoError();
        Logger.logDebugMessage("exchangeCoins: " + orderCreatedBob);
        generateBlock();

        JO orderInfoBob = GetCoinExchangeOrderCall.create().
                order(getOrderId(orderCreatedBob)).
                callNoError();
        Assert.assertEquals(Long.toString(10 * IGNIS.ONE_COIN), orderInfoBob.get("quantityQNT")); // leftover after the exchange of 100
        Assert.assertEquals(Long.toString((long) (0.25 * AEUR.ONE_COIN)), orderInfoBob.get("bidNQTPerCoin"));
        Assert.assertEquals(Long.toString(4 * IGNIS.ONE_COIN), orderInfoBob.get("askNQTPerCoin"));

        // Now look at the resulting trades
        JO tradesBobAeur = GetCoinExchangeTradesCall.create(AEUR.getId()).
                account(BOB.getRsAccount()).
                callNoError();
        Logger.logDebugMessage("GetCoinExchangeTrades: " + tradesBobAeur);

        // Bob received 100 IGNIS and paid 0.25 AEUR per IGNIS
        JSONArray trades = (JSONArray) tradesBobAeur.get("trades");
        JSONObject trade = (JSONObject) trades.get(0);
        Assert.assertEquals(AEUR.getId(), (int) (long) trade.get("chain"));
        Assert.assertEquals(IGNIS.getId(), (int) (long) trade.get("exchange"));
        Assert.assertEquals("" + (100 * IGNIS.ONE_COIN), trade.get("quantityQNT")); // IGNIS bought
        Assert.assertEquals("" + (long) (0.25 * AEUR.ONE_COIN), trade.get("priceNQTPerCoin")); // AEUR per IGNIS price

        JO tradesAliceIgnis = GetCoinExchangeTradesCall.create(IGNIS.getId()).
                account(ALICE.getRsAccount()).
                callNoError();
        Logger.logDebugMessage("GetCoinExchangeTrades: " + tradesAliceIgnis);

        // Alice received 25 AEUR and paid 4 IGNIS per AEUR
        trades = (JSONArray) tradesAliceIgnis.get("trades");
        trade = (JSONObject) trades.get(0);
        Assert.assertEquals(IGNIS.getId(), (int) (long) trade.get("chain"));
        Assert.assertEquals(AEUR.getId(), (int) (long) trade.get("exchange"));
        Assert.assertEquals("" + (25 * AEUR.ONE_COIN), trade.get("quantityQNT")); // AEUR bought
        Assert.assertEquals("" + (4 * IGNIS.ONE_COIN), trade.get("priceNQTPerCoin")); // IGNIS per AEUR price

        Assert.assertEquals(-100 * IGNIS.ONE_COIN - IGNIS.ONE_COIN / 100, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(25 * AEUR.ONE_COIN, ALICE.getChainBalanceDiff(AEUR.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-25 * AEUR.ONE_COIN - AEUR.ONE_COIN / 100, BOB.getChainBalanceDiff(AEUR.getId()));
    }

    @Test
    public void multiOrderExchange() {
        // Want to buy 25 AEUR with a maximum price of 4 IGNIS per AEUR
        // Convert the amount to IGNIS
        long displayAEURAmount = 25;
        long quantityQNT = displayAEURAmount * AEUR.ONE_COIN;
        long displayIgnisPerAEURPrice = 4;
        long priceNQT = displayIgnisPerAEURPrice * IGNIS.ONE_COIN;

        // Submit request to buy 25 AEUR with a maximum price of 4 IGNIS per AEUR
        // Quantity is denominated in AEUR and price is denominated in IGNIS per whole AEUR
        JO orderCreatedAlice = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                exchange(AEUR.getId()).
                quantityQNT(quantityQNT).
                priceNQTPerCoin(priceNQT).
                callNoError();
        Logger.logDebugMessage("exchangeCoins: " + orderCreatedAlice);
        generateBlock();


        // Want to buy 110 IGNIS with a maximum price of 1/4 AEUR per IGNIS
        // Quantity is denominated in IGNIS price is denominated in AEUR per whole IGNIS
        // Two orders to have two operations
        JO orderCreatedBob = ExchangeCoinsCall.create(AEUR.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeRateNQTPerFXT(AEUR.ONE_COIN).
                exchange(IGNIS.getId()).
                quantityQNT(50 * IGNIS.ONE_COIN + 10 * IGNIS.ONE_COIN).
                priceNQTPerCoin(AEUR.ONE_COIN / 4).
                callNoError();
        Logger.logDebugMessage("exchangeCoins: " + orderCreatedBob);
        generateBlock();

        JO orderCreatedBob2 = ExchangeCoinsCall.create(AEUR.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeRateNQTPerFXT(AEUR.ONE_COIN).
                exchange(IGNIS.getId()).
                quantityQNT(50 * IGNIS.ONE_COIN + 10 * IGNIS.ONE_COIN).
                priceNQTPerCoin(AEUR.ONE_COIN / 4).
                callNoError();
        Logger.logDebugMessage("exchangeCoins: " + orderCreatedBob2);
        generateBlock();


        // Now look at the resulting trades
        JO tradesBobAeur = GetCoinExchangeTradesCall.create(AEUR.getId()).
                account(BOB.getRsAccount()).
                callNoError();
        Logger.logDebugMessage("GetCoinExchangeTrades Bob AEUR: " + tradesBobAeur);

        // Bob received 100 IGNIS and paid 0.25 AEUR per IGNIS
        final JSONArray tradesArrayBob = (JSONArray) tradesBobAeur.get("trades");
        final JSONObject tradeBob1 = (JSONObject) tradesArrayBob.get(0);
        Assert.assertEquals(AEUR.getId(), (int) (long) tradeBob1.get("chain"));
        Assert.assertEquals(IGNIS.getId(), (int) (long) tradeBob1.get("exchange"));
        Assert.assertEquals("" + (40 * IGNIS.ONE_COIN), tradeBob1.get("quantityQNT")); // IGNIS bought
        Assert.assertEquals("" + (long) (0.25 * AEUR.ONE_COIN), tradeBob1.get("priceNQTPerCoin")); // AEUR per IGNIS price

        final JSONObject tradeBob2 = (JSONObject) tradesArrayBob.get(1);
        Assert.assertEquals(AEUR.getId(), (int) (long) tradeBob2.get("chain"));
        Assert.assertEquals(IGNIS.getId(), (int) (long) tradeBob2.get("exchange"));
        Assert.assertEquals("" + (60 * IGNIS.ONE_COIN), tradeBob2.get("quantityQNT")); // IGNIS bought
        Assert.assertEquals("" + (long) (0.25 * AEUR.ONE_COIN), tradeBob2.get("priceNQTPerCoin")); // AEUR per IGNIS price

        JO tradesAliceIgnis = GetCoinExchangeTradesCall.create(IGNIS.getId()).
                account(ALICE.getRsAccount()).
                callNoError();
        Logger.logDebugMessage("GetCoinExchangeTrades Alice IGNIS: " + tradesAliceIgnis);

        // Alice received 25 AEUR and paid 4 IGNIS per AEUR
        JSONArray tradesArrayAlice = (JSONArray) tradesAliceIgnis.get("trades");
        final JSONObject tradeAlice1 = (JSONObject) tradesArrayAlice.get(0);
        Assert.assertEquals(IGNIS.getId(), (int) (long) tradeAlice1.get("chain"));
        Assert.assertEquals(AEUR.getId(), (int) (long) tradeAlice1.get("exchange"));
        Assert.assertEquals("" + (10 * AEUR.ONE_COIN), tradeAlice1.get("quantityQNT")); // AEUR bought
        Assert.assertEquals("" + (4 * IGNIS.ONE_COIN), tradeAlice1.get("priceNQTPerCoin")); // IGNIS per AEUR price

        final JSONObject tradeAlice2 = (JSONObject) tradesArrayAlice.get(1);
        Assert.assertEquals(IGNIS.getId(), (int) (long) tradeAlice2.get("chain"));
        Assert.assertEquals(AEUR.getId(), (int) (long) tradeAlice2.get("exchange"));
        Assert.assertEquals("" + (15 * AEUR.ONE_COIN), tradeAlice2.get("quantityQNT")); // AEUR bought
        Assert.assertEquals("" + (4 * IGNIS.ONE_COIN), tradeAlice2.get("priceNQTPerCoin")); // IGNIS per AEUR price

        Assert.assertEquals(-100 * IGNIS.ONE_COIN - IGNIS.ONE_COIN / 100, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(25 * AEUR.ONE_COIN, ALICE.getChainBalanceDiff(AEUR.getId()));
        Assert.assertEquals(100 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-25 * AEUR.ONE_COIN - 2 * AEUR.ONE_COIN / 100, BOB.getChainBalanceDiff(AEUR.getId()));
    }

    private String getOrderId(JO orderCreatedBob) {
        return Tester.responseToStringId(orderCreatedBob.getJo("transactionJSON"));
    }

    @Test
    public void ronsSample() {
        long AEURToBuy = 5 * AEUR.ONE_COIN;
        long ignisPerWholeAEUR = (long) (0.75 * IGNIS.ONE_COIN);

        JO response = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                exchange(AEUR.getId()).
                quantityQNT(AEURToBuy).
                priceNQTPerCoin(ignisPerWholeAEUR).
                callNoError();
        String aliceOrder = Tester.responseToStringId(response);
        generateBlock();

        long ignisToBuy = 5 * IGNIS.ONE_COIN;
        long AEURPerWholeIgnis = (long) (1.35 * AEUR.ONE_COIN);

        response = ExchangeCoinsCall.create(AEUR.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeRateNQTPerFXT(AEUR.ONE_COIN).
                exchange(IGNIS.getId()).
                quantityQNT(ignisToBuy).
                priceNQTPerCoin(AEURPerWholeIgnis).
                callNoError();
        String bobOrder = Tester.responseToStringId(response);
        generateBlock();

        Assert.assertEquals((long) (-3.75 * IGNIS.ONE_COIN) - IGNIS.ONE_COIN / 100, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(5 * AEUR.ONE_COIN, ALICE.getChainBalanceDiff(AEUR.getId()));
        Assert.assertEquals((long) (3.75 * IGNIS.ONE_COIN), BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-5 * AEUR.ONE_COIN - AEUR.ONE_COIN / 100, BOB.getChainBalanceDiff(AEUR.getId()));

        assertOrderIsMissing(aliceOrder);

        response = GetCoinExchangeOrderCall.create().
                order(bobOrder).
                callNoError();
        Assert.assertEquals((long) (1.25 * IGNIS.ONE_COIN), Long.parseLong((String) response.get("quantityQNT")));
        Assert.assertEquals((long) (1.35 * AEUR.ONE_COIN), Long.parseLong((String) response.get("bidNQTPerCoin")));
        Assert.assertEquals((long) (0.74074074 * IGNIS.ONE_COIN), Long.parseLong((String) response.get("askNQTPerCoin")));
    }


    @Test
    public void testResidualOrderAmount() {
        JO result;

        result = ExchangeCoinsCall.create(FXT.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(FXT.ONE_COIN).
                exchange(IGNIS.getId()).
                quantityQNT(IGNIS.ONE_COIN).
                priceNQTPerCoin(1000 * FXT.ONE_COIN + 1).
                callNoError();
        String aliceOrder = Tester.responseToStringId(result);
        generateBlock();

        result = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                exchange(FXT.getId()).
                quantityQNT(1000 * FXT.ONE_COIN + 3).
                priceNQTPerCoin(IGNIS.ONE_COIN / 1000).
                callNoError();
        String bobOrder = Tester.responseToStringId(result);
        generateBlock();

        assertOrderIsMissing(aliceOrder);

        result = GetCoinExchangeOrderCall.create().
                order(bobOrder).callNoError();
        Logger.logDebugMessage(result.toJSONString());
    }

    @Test
    public void testHugePrice() {
        JO response;

        long price = 1_000_000;
        long feeFXT = FXT.ONE_COIN;

        //Alice is buying 0.00000100 IGNIS (100 NQT) at price 1000000.00000001 ARDR per IGNIS
        //So she is supposed to pay 1.00000000000001 ARDR

        response = ExchangeCoinsCall.create(FXT.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(feeFXT).
                exchange(IGNIS.getId()).
                quantityQNT(IGNIS.ONE_COIN / price).
                priceNQTPerCoin(price * FXT.ONE_COIN + 1).callNoError();
        String aliceOrder = Tester.responseToStringId(response);
        generateBlock();

        //Bob is buying 1 ARDR at price 0.000001 IGNIS per ARDR
        //So he is supposed to pay 0.000001 IGNIS

        response = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(feeFXT).
                exchange(FXT.getId()).
                quantityQNT(FXT.ONE_COIN).
                priceNQTPerCoin(IGNIS.ONE_COIN / price).callNoError();
        String bobOrder = Tester.responseToStringId(response);
        generateBlock();

        Assert.assertEquals(-FXT.ONE_COIN - feeFXT, ALICE.getFxtBalanceDiff());

        //Alice is supposed to get 100 IGNIS NQT but gets only 99 due to rounding if the askOrder
        Assert.assertEquals(99, ALICE.getChainBalanceDiff(IGNIS.getId()));

        Assert.assertEquals(FXT.ONE_COIN - feeFXT, BOB.getFxtBalanceDiff());
        Assert.assertEquals(-99, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testOrderFilling() {
        JO response;
        long priceEurNqtPerIgnis = 15 * AEUR.ONE_COIN / 10;
        long priceIgnisNqtPerEur = 10 * IGNIS.ONE_COIN / 15;

        response = ExchangeCoinsCall.create(AEUR.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(AEUR.ONE_COIN / 50).
                exchange(IGNIS.getId()).
                quantityQNT(12 * IGNIS.ONE_COIN).
                priceNQTPerCoin(priceEurNqtPerIgnis).callNoError();
        String aliceOrder = Tester.responseToStringId(response);
        generateBlock();

        response = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                exchange(AEUR.getId()).
                quantityQNT(2 * AEUR.ONE_COIN).
                priceNQTPerCoin(priceIgnisNqtPerEur).callNoError();
        String bobOrder = Tester.responseToStringId(response);
        generateBlock();
        assertOrderIsMissing(bobOrder);

        response = GetCoinExchangeOrderCall.create().order(aliceOrder).callNoError();

        long remainingEurAmount = Long.parseLong(response.getString("exchangeQNT"));

        response = ExchangeCoinsCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                exchange(AEUR.getId()).
                quantityQNT(remainingEurAmount).
                priceNQTPerCoin(priceIgnisNqtPerEur).callNoError();
        bobOrder = Tester.responseToStringId(response);
        generateBlock();

        assertOrderIsMissing(aliceOrder);
        assertOrderIsMissing(bobOrder);
    }

    @Test
    public void testCloseToZeroAmount() {
        JO response;

        long feeFXT = FXT.ONE_COIN;

        ExchangeCoinsCall exchangeCoinsCall = ExchangeCoinsCall.create(FXT.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(feeFXT).
                exchange(IGNIS.getId()).
                quantityQNT(9).
                priceNQTPerCoin(FXT.ONE_COIN / 10);
        InvocationError error = exchangeCoinsCall.build().invokeWithError();

        Assert.assertEquals("Order value is zero", error.getErrorDescription());

        exchangeCoinsCall.quantityQNT(10).callNoError();
        generateBlock();
    }

    private void assertOrderIsMissing(String order) {
        InvocationError aliceOrderInfo = GetCoinExchangeOrderCall.create().
                order(order).
                build().invokeWithError();
        Assert.assertEquals("Unknown order", aliceOrderInfo.getErrorDescription());
    }
}
