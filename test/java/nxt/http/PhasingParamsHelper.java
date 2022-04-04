/*
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

package nxt.http;

import nxt.Tester;
import nxt.crypto.HashFunction;
import nxt.voting.VoteWeighting;

import java.util.stream.Stream;

public class PhasingParamsHelper {
    private PhasingParamsHelper() {}

    public static PhasingParamsBuilder accountSubpoll(Tester... account) {
        return PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                .phasingWhitelisted(Stream.of(account).map(Tester::getStrId).toArray(String[]::new))
                .phasingQuorum(1);
    }

    public static PhasingParamsBuilder compositeSingleAccountSubpoll(String variableName, Tester tester) {
        return PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.COMPOSITE.getCode())
                .phasingQuorum(1)
                .phasingExpression(variableName)
                .setSubPoll(variableName, accountSubpoll(tester));
    }

    public static PhasingParamsBuilder hashSubpoll(String secret) {
        return PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.HASH.getCode())
                .phasingHashedSecret(HashFunction.SHA256.hash(secret.getBytes()))
                .phasingHashedSecretAlgorithm(HashFunction.SHA256.getId())
                .phasingQuorum(1);
    }

    public static PhasingParamsBuilder senderPropertySubpoll(Tester account, String propertyName, String propertyValue) {
        return PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.PROPERTY.getCode())
                .phasingQuorum(1)
                .phasingSenderProperty(account.getId(), propertyName, propertyValue);
    }

    public static PhasingParamsBuilder linkedTransactionSubpoll(String linkedTransaction) {
        return PhasingParamsBuilder.create()
                .phasingVotingModel(VoteWeighting.VotingModel.TRANSACTION.getCode())
                .phasingLinkedTransactions(linkedTransaction)
                .phasingQuorum(1);
    }
}
