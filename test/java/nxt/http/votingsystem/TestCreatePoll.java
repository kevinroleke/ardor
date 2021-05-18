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

package nxt.http.votingsystem;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.addons.JO;
import nxt.http.callers.CreatePollCall;
import nxt.http.callers.GetPollCall;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCreatePoll extends BlockchainTest {

    static String issueCreatePoll(CreatePollCall createPollCall, boolean shouldFail) {
        JO createPollResponse = shouldFail ? createPollCall.call() : createPollCall.callNoError();
        Logger.logMessage("createPollResponse: " + createPollResponse.toJSONString());

        generateBlock();

        try {
            byte[] fullHash = Convert.parseHexString((String) createPollResponse.get("fullHash"));

            if(!shouldFail && fullHash == null) Assert.fail();
            String pollId = Long.toUnsignedString(Convert.fullHashToId(fullHash));
            JO getPollResponse = GetPollCall.create(IGNIS.getId()).poll(pollId).callNoError();
            Logger.logMessage("getPollResponse:" + getPollResponse.toJSONString());
            Assert.assertEquals(pollId, getPollResponse.get("poll"));
            return pollId;
        }catch(Throwable t){
            if(!shouldFail) Assert.fail(t.getMessage());
            return null;
        }
    }

    public static CreatePollCall createPollBuilder() {
        return CreatePollCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(10 * IGNIS.ONE_COIN)
                .name("Test1")
                .description("The most cool Beatles guy?")
                .finishHeight(Nxt.getBlockchain().getHeight() + 100)
                .votingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                .minNumberOfOptions((byte)1)
                .maxNumberOfOptions((byte)2)
                .minRangeValue((byte)0)
                .maxRangeValue((byte)1)
                .minBalance(10 * IGNIS.ONE_COIN)
                .minBalanceModel(VoteWeighting.MinBalanceModel.COIN.getCode())
                .param("option00", "Ringo")
                .param("option01", "Paul")
                .param("option02", "John");
    }

    @Test
    public void createValidPoll() {
        issueCreatePoll(createPollBuilder(), false);
        generateBlock();

        issueCreatePoll(createPollBuilder().votingModel(VoteWeighting.VotingModel.COIN.getCode()), false);
        generateBlock();
    }

    @Test
    public void createInvalidPoll() {
        issueCreatePoll(createPollBuilder().minBalance(-IGNIS.ONE_COIN), true);
        generateBlock();

        issueCreatePoll(createPollBuilder().minBalance(0), true);
        generateBlock();
    }
}
