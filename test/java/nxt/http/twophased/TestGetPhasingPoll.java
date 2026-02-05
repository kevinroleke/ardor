/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
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
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.GetPhasingPollCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestGetPhasingPoll extends BlockchainTest {

    @Test
    public void transactionVotes() {

        APICall apiCall = TestCreateTwoPhased.createSendMoneyBuilder()
                .phasingQuorum(1)
                .build();
        JO transactionJSON = TestCreateTwoPhased.issueCreateTwoPhased(apiCall, false);
        String fullHash = (String) transactionJSON.get("fullHash");

        generateBlock();

        long fee = IGNIS.ONE_COIN;
        JO response = ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(CHUCK.getSecretPhrase())
                .phasedTransaction(IGNIS.getId() + ":" + fullHash)
                .feeNQT(fee)
                .callNoError();
        Logger.logMessage("approveTransactionResponse:" + response.toJSONString());

        generateBlock();

        response = GetPhasingPollCall.create(IGNIS.getId())
                .transactionFullHash(fullHash)
                .countVotes(true)
                .callNoError();
        Logger.logMessage("getPhasingPollResponse:" + response.toJSONString());

        Assert.assertEquals(1, Integer.parseInt((String) response.get("result")));
    }

}