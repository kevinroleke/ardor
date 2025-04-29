/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.GetAssetPhasedTransactionsCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.SearchAssetsCall;
import nxt.util.Logger;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestGetAssetPhasedTransactions extends BlockchainTest {

    static APICall phasedTransactionsApiCall(String asset) {
        return GetAssetPhasedTransactionsCall.create(IGNIS.getId())
                .asset(asset)
                .firstIndex(0)
                .lastIndex(10)
                .build();
    }

    private APICall byAssetApiCall(String asset) {
        return TestCreateTwoPhased.createSendMoneyBuilder()
                .phasingVotingModel(VoteWeighting.VotingModel.ASSET.getCode())
                .phasingHolding(asset)
                .phasingMinBalance(1)
                .phasingMinBalanceModel(VoteWeighting.MinBalanceModel.ASSET.getCode())
                .feeNQT(21 * IGNIS.ONE_COIN)
                .build();
    }


    @Test
    public void simpleTransactionLookup() {
        String asset = issueTestAsset();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(byAssetApiCall(asset), false);

        JO response = phasedTransactionsApiCall(asset).getJsonResponse();
        Logger.logMessage("getAssetPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");
        Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, (String) transactionJSON.get("fullHash")));
    }

    @Test
    public void sorting() {
        String asset = issueTestAsset();

        for (int i = 0; i < 15; i++) {
            TestCreateTwoPhased.issueCreateTwoPhased(byAssetApiCall(asset), false);
        }

        JO response = phasedTransactionsApiCall(asset).getJsonResponse();
        Logger.logMessage("getAssetPhasedTransactionsResponse:" + response.toJSONString());
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

    private String issueTestAsset() {
        String name = "lz1cdqGYD";
        IssueAssetCall.create(IGNIS.getId())
                .secretPhrase(RIKER.getSecretPhrase())
                .name(name)
                .description("asset testing")
                .quantityQNT(10000000)
                .decimals(4)
                .feeNQT(1000 * IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        BlockchainTest.generateBlock();

        JO response = SearchAssetsCall.create().query(name).callNoError();
        JA assets = response.getArray("assets");
        return assets.get(0).getString("asset");
    }
}
