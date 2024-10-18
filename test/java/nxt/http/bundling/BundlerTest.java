/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.Tester;
import nxt.account.PaymentTransactionType;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.http.callers.GetTransactionCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.StartBundlerCall;
import nxt.http.callers.StopBundlerCall;
import nxt.util.JSON;
import nxt.util.JSONAssert;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.util.Collections;

import static nxt.blockchain.ChildChain.IGNIS;

public class BundlerTest extends BlockchainTest {
    @BeforeClass
    public static void init() {
        initNxt(Collections.emptyMap());
        initBlockchainTest();
        stopAllDefaultBundlers();
    }

    public static void stopAllDefaultBundlers() {
        for (Chain chain : ChildChain.getAll()) {
            JO response = StopBundlerCall.create(chain.getId()).
                    secretPhrase(FORGY.getSecretPhrase()).
                    callNoError();
            Assert.assertTrue(response.getBoolean("stoppedBundler"));
        }
    }

    @After
    public void destroy() {
        super.destroy();
        StopBundlerCall.create(IGNIS.getId()).callNoError();
    }

    protected void startTwoRulesBundler(long publicRate, long personalBundlerOverpay) {
        JA rulesArray = new JA();
        JO rule = new JO();

        JO filterJson = new JO();
        filterJson.put("name", "PersonalBundler");
        JA filtersJson = new JA();
        filtersJson.add(filterJson);

        rule.put("filters", filtersJson);
        rule.put("feeCalculatorName", "MIN_FEE");
        rule.put("minRateNQTPerFXT", "0");
        rule.put("overpayFQTPerFXT", personalBundlerOverpay);
        rulesArray.add(rule);

        rule = new JO();
        rule.put("feeCalculatorName", "MIN_FEE");
        rule.put("minRateNQTPerFXT", Long.toUnsignedString(publicRate));
        rulesArray.add(rule);

        JSONAssert result = new JSONAssert(StartBundlerCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                bundlingRulesJSON(JSON.toString(rulesArray.toJSONArray())).call());
        result.str("totalFeesLimitFQT");
    }

    protected long getMinFeeNQT(long rate) {
        return BigInteger.valueOf(getMinFeeFQT()).multiply(BigInteger.valueOf(rate)).
                divide(BigInteger.valueOf(FxtChain.FXT.ONE_COIN)).longValueExact();
    }

    protected long getMinFeeFQT() {
        return PaymentTransactionType.ORDINARY.getBaselineFee(null).getFee(null, null);
    }

    protected boolean bundleTransaction(Tester sender, long fee) {
        String fullHash = createTransaction(sender, fee, null);
        generateBlock();
        return isBundled(fullHash);
    }

    public static boolean isBundled(String fullHash) {
        JSONAssert result = new JSONAssert(GetTransactionCall.create(IGNIS.getId()).fullHash(fullHash).call());
        Object errorDescription = result.getJson().get("errorDescription");
        if ("Unknown transaction".equals(errorDescription)) {
            return false;
        }
        return Nxt.getBlockchain().getHeight() == result.integer("height");
    }

    @SuppressWarnings("SameParameterValue")
    protected String createTransaction(Tester sender, long fee, String message) {
        SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId()).secretPhrase(sender.getSecretPhrase()).
                recipient(DAVE.getId()).amountNQT(10 * IGNIS.ONE_COIN).
                deadline(30).feeNQT(fee);
        if (message != null) {
            builder.message(message);
        }
        JSONAssert result = new JSONAssert(builder.call());
        return result.str("fullHash");
    }
}
