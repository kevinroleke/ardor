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

package nxt.http.twophased;


import nxt.BlockchainTest;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.GetAccountPhasedTransactionsCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

public class TestGetAccountPhasedTransactions extends BlockchainTest {

    static APICall phasedTransactionsApiCall(long id) {
        return GetAccountPhasedTransactionsCall.create()
                .account(id)
                .firstIndex(0)
                .lastIndex(10)
                .build();
    }

    static APICall phasedTransactionsApiCall() {
        return phasedTransactionsApiCall(ALICE.getId());
    }

    @Test
    public void simpleOutgoingLookup() {
        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder().build();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        generateBlock();

        JO response = phasedTransactionsApiCall().getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON.get("fullHash")));

        response = phasedTransactionsApiCall(CHUCK.getId()).getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        transactionsJson = response.getArray("transactions");
        Assert.assertFalse(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON.get("fullHash")));
    }

    @Test
    public void simpleIngoingLookup() {
        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder().build();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        generateBlock();

        JO response = phasedTransactionsApiCall(BOB.getId()).getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON.get("fullHash")));

        response = phasedTransactionsApiCall(CHUCK.getId()).getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        transactionsJson = response.getArray("transactions");
        Assert.assertFalse(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON.get("fullHash")));
    }

    @Test
    public void multiple() {
        JO response = phasedTransactionsApiCall().getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        int transactionsSize0 = transactionsJson.size();

        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder().build();
        JO transactionJSON1 = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        JO transactionJSON2 = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        generateBlock();

        response = phasedTransactionsApiCall().getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        transactionsJson = response.getArray("transactions");

        int transactionsSize = transactionsJson.size();

        Assert.assertEquals(2, transactionsSize - transactionsSize0);
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON1.get("fullHash")));
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON2.get("fullHash")));
    }

    @Test
    public void sorting() {
        for (int i = 0; i < 15; i++) {
            APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder().build();
            TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        }

        JO response = phasedTransactionsApiCall().getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
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
}

