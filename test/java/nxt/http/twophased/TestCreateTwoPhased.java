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

package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.SendMoneyCall;
import nxt.util.Logger;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static nxt.blockchain.ChildChain.IGNIS;


public class TestCreateTwoPhased extends BlockchainTest {

    static JO issueCreateTwoPhased(APICall apiCall, boolean shouldFail) {
        JO twoPhased = apiCall.getJsonResponse();
        Logger.logMessage("two-phased sendMoney: " + twoPhased.toJSONString());

        generateBlock();
        String transactionId = twoPhased.getString("fullHash");
        if (!shouldFail && transactionId == null || shouldFail && transactionId != null) {
            Assert.fail();
        }
        return twoPhased;
    }

    public static SendMoneyCall createSendMoneyBuilder() {
        return SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(2 * IGNIS.ONE_COIN)
                .recipient(BOB.getId())
                .amountNQT(50 * IGNIS.ONE_COIN)
                .phased(true)
                .phasingVotingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                .phasingQuorum(1)
                .phasingWhitelisted(CHUCK.getStrId())
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 50);
    }

    @Test
    public void validMoneyTransfer() {
        APICall apiCall = createSendMoneyBuilder().build();
        issueCreateTwoPhased(apiCall, false);
    }

    @Test
    public void invalidMoneyTransfer() {
        int height = Nxt.getBlockchain().getHeight();

        APICall apiCall = createSendMoneyBuilder().phasingFinishHeight(height).build();
        issueCreateTwoPhased(apiCall, true);

        apiCall = createSendMoneyBuilder().phasingFinishHeight(height + 100000).build();
        issueCreateTwoPhased(apiCall, true);

        apiCall = createSendMoneyBuilder().phasingQuorum(0).build();
        issueCreateTwoPhased(apiCall, true);

        apiCall = createSendMoneyBuilder().phasingWhitelisted("").build();
        issueCreateTwoPhased(apiCall, true);

        apiCall = createSendMoneyBuilder().phasingWhitelisted("0").build();
        issueCreateTwoPhased(apiCall, true);

        apiCall = createSendMoneyBuilder().phasingVotingModel(VoteWeighting.VotingModel.ASSET.getCode()).build();
        issueCreateTwoPhased(apiCall, true);

        apiCall = createSendMoneyBuilder().phasingVotingModel(VoteWeighting.VotingModel.ASSET.getCode())
                .phasingMinBalance(50)
                .phasingMinBalanceModel(VoteWeighting.MinBalanceModel.ASSET.getCode())
                .build();
        issueCreateTwoPhased(apiCall, true);
    }

    @Test
    public void unconfirmed() {
        List<String> transactionIds = new ArrayList<>(10);

        for(int i=0; i < 10; i++){
            APICall apiCall = createSendMoneyBuilder().build();
            JO transactionJSON = issueCreateTwoPhased(apiCall, false);
            String idString = transactionJSON.getString("fullHash");
            transactionIds.add(idString);
        }

        createSendMoneyBuilder().callNoError();

        JO response = TestGetAccountPhasedTransactions.phasedTransactionsApiCall().getJsonResponse();
        Logger.logMessage("getAccountPhasedTransactionsResponse:" + response.toJSONString());
        JA transactionsJson = response.getArray("transactions");

        for(String idString:transactionIds){
            Assert.assertTrue(TwoPhasedSuite.searchForTransactionId(transactionsJson, idString));
        }
    }
}