/*
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

package nxt.http;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.util.Convert;

import java.util.Arrays;

public class PhasingParamsBuilder {
    private final JO json = new JO();

    private PhasingParamsBuilder() {
        // default values
        phasingMinBalance(0);
        phasingMinBalanceModel((byte) 0);
        phasingHolding(0);
    }

    public static PhasingParamsBuilder create() {
        return new PhasingParamsBuilder();
    }

    public String toJSONString() {
        return json.toJSONString();
    }

    public JO toJO() {
        return JO.parse(toJSONString());
    }

    public PhasingParamsBuilder phasingVotingModel(byte phasingVotingModel) {
        return param("phasingVotingModel", phasingVotingModel);
    }

    public PhasingParamsBuilder phasingQuorum(long phasingQuorum) {
        return param("phasingQuorum", "" + phasingQuorum);
    }

    public PhasingParamsBuilder phasingMinBalance(long phasingMinBalance) {
        return param("phasingMinBalance", "" + phasingMinBalance);
    }

    public PhasingParamsBuilder phasingMinBalanceModel(byte phasingMinBalanceModel) {
        return param("phasingMinBalanceModel", phasingMinBalanceModel);
    }

    public PhasingParamsBuilder phasingHolding(long phasingHolding) {
        return phasingHolding(Long.toUnsignedString(phasingHolding));
    }

    public PhasingParamsBuilder phasingHolding(String phasingHolding) {
        return param("phasingHolding", phasingHolding);
    }

    public PhasingParamsBuilder phasingWhitelisted(String... phasingWhitelist) {
        return param("phasingWhitelist", Arrays.asList(phasingWhitelist));
    }

    public PhasingParamsBuilder phasingLinkedTransactions(String... phasingLinkedTransactions) {
        JA ja = new JA();
        for (String linkedTransaction : phasingLinkedTransactions) {
            String[] parts = linkedTransaction.split(":");
            JO jo = new JO();
            jo.put("chain", Integer.parseInt(parts[0]));
            jo.put("transactionFullHash", parts[1]);
            ja.add(jo);
        }
        return param("phasingLinkedTransactions", ja);
    }

    public PhasingParamsBuilder phasingHashedSecret(String phasingHashedSecret) {
        return param("phasingHashedSecret", phasingHashedSecret);
    }

    public PhasingParamsBuilder phasingHashedSecret(byte[] phasingHashedSecret) {
        return param("phasingHashedSecret", Convert.toHexString(phasingHashedSecret));
    }

    public PhasingParamsBuilder phasingHashedSecretAlgorithm(byte phasingHashedSecretAlgorithm) {
        return param("phasingHashedSecretAlgorithm", phasingHashedSecretAlgorithm);
    }

    public PhasingParamsBuilder phasingSenderProperty(long setter, String propertyName, String value) {
        return paramVoteProperty("phasingSenderProperty", setter, propertyName, value);
    }

    public PhasingParamsBuilder phasingRecipientProperty(long setter, String propertyName, String value) {
        return paramVoteProperty("phasingRecipientProperty", setter, propertyName, value);
    }

    public PhasingParamsBuilder phasingExpression(String phasingExpression) {
        return param("phasingExpression", phasingExpression);
    }

    public PhasingParamsBuilder setSubPoll(String variable, PhasingParamsBuilder subPollParams) {
        return setSubPoll(variable, subPollParams.toJO());
    }

    public PhasingParamsBuilder setSubPoll(String variable, JO subPollParams) {
        if (!json.isExist("phasingSubPolls")) {
            json.put("phasingSubPolls", new JO());
        }
        JO phasingSubPolls = json.getJo("phasingSubPolls");
        phasingSubPolls.put(variable, subPollParams);
        return this;
    }

    private PhasingParamsBuilder param(String name, Object value) {
        json.put(name, value);
        return this;
    }

    private PhasingParamsBuilder paramVoteProperty(String voteProperty, long setter, String propertyName, String value) {
        JO jo = new JO();
        jo.put("setter", Long.toUnsignedString(setter));
        jo.put("name", propertyName);
        jo.put("value", value);
        json.put(voteProperty, jo);
        return this;
    }
}
