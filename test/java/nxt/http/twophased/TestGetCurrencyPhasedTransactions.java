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

package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.APICall;
import nxt.http.callers.GetCurrencyCall;
import nxt.http.callers.GetCurrencyPhasedTransactionsCall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import nxt.util.Logger;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

public class TestGetCurrencyPhasedTransactions extends BlockchainTest {

    static APICall phasedTransactionsApiCall(String currency) {
        return GetCurrencyPhasedTransactionsCall.create()
                .currency(currency)
                .firstIndex(0)
                .lastIndex(20)
                .build();
    }

    private APICall byCurrencyApiCall(String currency){
        return TestCreateTwoPhased.createSendMoneyBuilder()
                .phasingVotingModel(VoteWeighting.VotingModel.CURRENCY.getCode())
                .phasingHolding(currency)
                .phasingMinBalance(1)
                .phasingMinBalanceModel(VoteWeighting.MinBalanceModel.CURRENCY.getCode())
                .feeNQT(21 * ChildChain.IGNIS.ONE_COIN)
                .build();
    }

    @Test
    public void simpleTransactionLookup() {
        String currency = issueTestCurrency();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(byCurrencyApiCall(currency), false);
        JO response = phasedTransactionsApiCall(currency).getJsonResponse();
        Logger.logMessage("getCurrencyPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON.get("fullHash")));
    }

    @Test
    public void sorting() {
        String currency = issueTestCurrency();
        for (int i = 0; i < 15; i++) {
            TestCreateTwoPhased.issueCreateTwoPhased(byCurrencyApiCall(currency), false);
        }

        JO response = phasedTransactionsApiCall(currency).getJsonResponse();
        Logger.logMessage("getCurrencyPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");

        //sorting check
        int prevHeight = Integer.MAX_VALUE;
        for (Object transactionsJsonObj : transactionsJson) {
            JO transactionObject = (JO) transactionsJsonObj;
            int height = transactionObject.getInt("height");
            Assert.assertTrue(height <= prevHeight);
            prevHeight = height;
        }
    }

    private String issueTestCurrency() {
        String code = "JUTST";
        TestCurrencyIssuance.builder("JUnitTest", code, "tests currency").callNoError();
        BlockchainTest.generateBlock();

        JO result = GetCurrencyCall.create().code(code).callNoError();
        return result.getString("currency");
    }

}
