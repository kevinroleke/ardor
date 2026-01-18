/*
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

package nxt.http.bundling;

import nxt.Constants;
import nxt.Nxt;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtTransaction;
import nxt.http.JSONData;
import nxt.http.callers.AddBundlingRuleCall;
import nxt.http.callers.GetFxtTransactionCall;
import nxt.http.callers.GetTransactionCall;
import nxt.http.callers.StartBundlerCall;
import nxt.util.Convert;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import nxt.util.Time;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RulesTest extends BundlerTest {

    @Test
    public void testTwoRules() {
        long publicRate = ChildChain.IGNIS.ONE_COIN * 10;
        startTwoRulesBundler(publicRate, 0);

        //PersonalBundler filter allows this
        Assert.assertTrue(bundleTransaction(BOB, 0));

        //The public transaction is bundled only if it pays enough fee
        long minFeeNQT = getMinFeeNQT(publicRate);
        Assert.assertFalse(bundleTransaction(ALICE, minFeeNQT - 1));
        Assert.assertTrue(bundleTransaction(ALICE, minFeeNQT));

        Assert.assertTrue(bundleTransaction(ALICE, minFeeNQT * 2));
    }

    @Test
    public void testRulePriority() {
        long publicRate = ChildChain.IGNIS.ONE_COIN * 10;
        long minFeeNQT = getMinFeeNQT(publicRate);

        List<String> fullHashes = new ArrayList<>(Constants.MAX_NUMBER_OF_CHILD_TRANSACTIONS);
        int now = Nxt.getEpochTime();
        Nxt.setTime(new Time.ConstantTime(now));
        for (int i = 0; i < Constants.MAX_NUMBER_OF_CHILD_TRANSACTIONS; i++) {
            fullHashes.add(createTransaction(BOB, 0, null));
            if (i % 2 == 0) {
                //Don't create a max number of fee paying transactions - otherwise 2 full ChildBlock transactions will be
                //created and we cannot check the priority
                createTransaction(ALICE, minFeeNQT * 2, null);
            }
        }
        Nxt.setTime(new Time.CounterTime(now));

        //To speed up the test, the bundler start is moved after the transactions are created
        //TODO move it back if the bundler is not run for each unconfirmed transaction

        //add some minimal overpay to the personal bundler rule so that the full ChildBlock transaction is forged
        //with priority over any other full ChildBlock transactions with mixed content
        startTwoRulesBundler(publicRate, 100);

        generateBlock();

        //Bob's transactions should be processed with priority, even though their NQT fee per byte is 0 - because the
        // private rule comes first in the rules list
        for (String fullHash : fullHashes) {
            JSONAssert result = getTransaction(fullHash);
            if (!result.getJson().containsKey("height")) {
                Logger.logErrorMessage("missing height: " + result.getJson().toJSONString() + " " + fullHashes);
                FxtTransaction fxtTransaction = Nxt.getBlockchain().getBlockAtHeight(2).getFxtTransactions().get(0);
                Logger.logErrorMessage("Fxt tx: " + JSONData.transaction(fxtTransaction));
                Logger.logErrorMessage("" + fxtTransaction.getSortedChildTransactions().
                        stream().map(t -> Convert.toHexString(t.getFullHash())).collect(Collectors.toList()).toString());
            }
            Assert.assertEquals(Nxt.getBlockchain().getHeight(), result.integer("height"));
        }
    }

    private JSONAssert getTransaction(String fullHash) {
        return new JSONAssert(GetTransactionCall.create(ChildChain.IGNIS.getId()).fullHash(fullHash).call());
    }

    @Test
    public void testProportionalFee() {
        long minRate = ChildChain.IGNIS.ONE_COIN;
        JSONAssert result = new JSONAssert(StartBundlerCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                minRateNQTPerFXT(minRate).
                feeCalculatorName("PROPORTIONAL_FEE").call());
        result.str("totalFeesLimitFQT");

        long minFeeNQT = getMinFeeNQT(minRate);
        int FEE_MULTIPLIER = 2;
        String fullHash = createTransaction(ALICE, minFeeNQT * FEE_MULTIPLIER, null);
        generateBlock();

        result = getTransaction(fullHash);

        result = new JSONAssert(GetFxtTransactionCall.create().transaction(result.str("fxtTransaction")).call());

        long actualFee = Convert.parseUnsignedLong(result.str("feeNQT"));
        Assert.assertEquals(getMinFeeFQT() * FEE_MULTIPLIER, actualFee);
    }

    @Test
    public void testProportionalFeeZeroRate() {
        JSONAssert result = new JSONAssert(StartBundlerCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                minRateNQTPerFXT(0).
                feeCalculatorName("PROPORTIONAL_FEE").call());
        Assert.assertTrue(result.str("errorDescription").startsWith("Division by zero"));
    }

    @Test
    public void testAddBundlingRule() {
        long minRate = ChildChain.IGNIS.ONE_COIN;
        JSONAssert result = new JSONAssert(StartBundlerCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                minRateNQTPerFXT(minRate * 2).
                feeCalculatorName("PROPORTIONAL_FEE").call());
        result.str("totalFeesLimitFQT");

        long minFeeNQT = getMinFeeNQT(minRate);
        Assert.assertFalse(bundleTransaction(ALICE, minFeeNQT));

        result = new JSONAssert(AddBundlingRuleCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                minRateNQTPerFXT(minRate).
                feeCalculatorName("MIN_FEE").call());
        result.str("totalFeesLimitFQT");
        Assert.assertTrue(bundleTransaction(ALICE, minFeeNQT));
    }
}
