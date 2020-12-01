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
import nxt.Nxt;
import nxt.Tester;
import nxt.addons.JO;
import nxt.http.MessageListenerRule;
import nxt.http.PhasingParamsBuilder;
import nxt.http.callers.SendMessageCall;
import nxt.messaging.MessageAppendix;
import nxt.messaging.MessagingTransactionType.MessageEvent;
import nxt.util.Convert;
import nxt.voting.VoteWeighting;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.http.accountControl.ACTestUtils.approve;
import static nxt.voting.VoteWeighting.VotingModel.ACCOUNT;
import static org.junit.Assert.assertEquals;

public class TestPhasedMessaging extends BlockchainTest {
    @Rule
    public final MessageListenerRule messageListenerRule = new MessageListenerRule();

    @Test
    public void testMessageAppliedOnlyAfterApproval() {
        Tester sender = ALICE;

        PhasingParamsBuilder phasingA = PhasingParamsBuilder.create()
                .phasingVotingModel(ACCOUNT.getCode())
                .phasingWhitelisted(CHUCK.getStrId())
                .phasingQuorum(1);

        PhasingParamsBuilder phasingB = PhasingParamsBuilder.create()
                .phasingVotingModel(ACCOUNT.getCode())
                .phasingWhitelisted(DAVE.getStrId())
                .phasingQuorum(1);

        PhasingParamsBuilder phasingParamsBuilder = PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression("A & B")
                .setSubPoll("A", phasingA)
                .setSubPoll("B", phasingB);

        JO response = SendMessageCall.create(IGNIS.getId())
                .secretPhrase(sender.getSecretPhrase())
                .message("some message")
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .feeNQT(3 * IGNIS.ONE_COIN).phasingParams(phasingParamsBuilder.toJSONString())
                .callNoError();

        generateBlock();

        assertEquals(Collections.emptyList(), getAppliedMessages());

        //single vote approves all sub-polls where CHUCK is whitelisted
        String fullHash = response.get("fullHash").toString();
        approve(fullHash, CHUCK, null);
        approve(fullHash, DAVE, null);

        generateBlock();

        assertEquals(Collections.singletonList("some message"), getAppliedMessages());
    }

    private List<String> getAppliedMessages() {
        return messageListenerRule.getEvents().stream()
                .map(MessageEvent::getMessage)
                .map(MessageAppendix::getMessage)
                .map(Convert::toString)
                .collect(toList());
    }
}
