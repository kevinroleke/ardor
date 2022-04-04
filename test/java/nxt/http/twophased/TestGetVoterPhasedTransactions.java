/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2022 Jelurida IP B.V.
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
import nxt.http.APICall;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.GetVoterPhasedTransactionsCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestGetVoterPhasedTransactions extends BlockchainTest {

    static APICall getVoterPhasedTransactions() {
        return GetVoterPhasedTransactionsCall.create(IGNIS.getId())
                .account(CHUCK.getId())
                .firstIndex(0)
                .lastIndex(10)
                .build();
    }

    @Test
    public void simpleTransactionLookup() {
        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder().build();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        String transactionId = (String) transactionJSON.get("fullHash");

        generateBlock();

        JO response = getVoterPhasedTransactions().getJsonResponse();
        Logger.logMessage("getVoterPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, transactionId));
    }

    @Test
    public void transactionLookupAfterVote() {

        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder()
                .build();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        String transactionFullHash = (String) transactionJSON.get("fullHash");

        generateBlock();

        long fee = IGNIS.ONE_COIN;
        JO response = ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(CHUCK.getSecretPhrase())
                .phasedTransaction(IGNIS.getId() + ":" + transactionFullHash)
                .feeNQT(fee)
                .callNoError();
        Logger.logMessage("approvePhasedTransactionResponse:" + response.toJSONString());

        generateBlock();

        response = getVoterPhasedTransactions().getJsonResponse();
        Logger.logMessage("getVoterPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        Assert.assertFalse(TwoPhasedSuite.searchForTransactionId(transactionsJson, transactionFullHash));
    }

    @Test
    public void sorting() {
        for (int i = 0; i < 15; i++) {
            APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder().build();
            TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        }

        JO response = getVoterPhasedTransactions().getJsonResponse();
        Logger.logMessage("getVoterPhasedTransactionsResponse:" + response.toJSONString());
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
