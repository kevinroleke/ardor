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

package nxt.http.votingsystem;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.http.callers.CastVoteCall;
import nxt.http.callers.GetPollResultCall;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCastVote extends BlockchainTest {
    private String getResult(JSONArray results, int index) {
        return (String) ((JSONObject) results.get(index)).get("result");
    }
    @Test
    public void validVoteCasting() {
        String poll = TestCreatePoll.issueCreatePoll(TestCreatePoll.createPollBuilder(), false);
        generateBlock();

        JO response = CastVoteCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .poll(poll)
                .param("vote00", 1)
                .param("vote01", 0)
                .feeNQT(IGNIS.ONE_COIN)
                .callNoError();

        Logger.logMessage("voteCasting:" + response.toJSONString());
        generateBlock();

        JO getPollResponse = GetPollResultCall.create(IGNIS.getId()).poll(poll).callNoError();
        Logger.logMessage("getPollResultResponse:" + getPollResponse.toJSONString());
        JSONArray results = (JSONArray)getPollResponse.get("results");

        long ringoResult = Long.parseLong(getResult(results, 0));
        Assert.assertEquals(1, ringoResult);

        long paulResult = Long.parseLong(getResult(results, 1));
        Assert.assertEquals(0, paulResult);

        //John's result is empty by spec
        Assert.assertEquals("", getResult(results, 2));
    }

    @Test
    public void invalidVoteCasting() {
        String poll = TestCreatePoll.issueCreatePoll(TestCreatePoll.createPollBuilder(), false);
        generateBlock();

        JO response = CastVoteCall.create(IGNIS.getId())
                .setParamValidation(false)
                .secretPhrase(ALICE.getSecretPhrase())
                .poll(poll)
                .param("vote1", 1)
                .param("vote2", 1)
                .param("vote3", 1)
                .feeNQT(IGNIS.ONE_COIN)
                .call();

        Logger.logMessage("voteCasting:" + response.toJSONString());
    }


}