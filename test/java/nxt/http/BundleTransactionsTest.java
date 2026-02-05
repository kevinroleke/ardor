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

package nxt.http;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.account.PaymentTransactionType;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.blockchain.TransactionType;
import nxt.http.APICall.InvocationError;
import nxt.http.bundling.BundlerTest;
import nxt.http.callers.BundleTransactionsCall;
import nxt.http.callers.SendMessageCall;
import nxt.http.callers.SendMoneyCall;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static nxt.blockchain.ChildChain.AEUR;
import static nxt.blockchain.FxtChain.FXT;

public class BundleTransactionsTest extends BlockchainTest {
    ChildChain chain = AEUR;

    @Test
    public void bundleTransactionsExpiringSoon() {

        String hash1 = sendMessageCall()
                .deadline(5)
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .deadline(6)
                .callNoError()
                .getString("fullHash");


        JO actual = bundleTransactionsCall(hash1, hash2).callNoError();

        // deadline is less then minimal transaction expiration because it also adjusts for transaction timestamp difference
        Assert.assertEquals(4, getDeadline(actual));
    }

    @Test
    public void bundleTransactionsExpiringSoonWithTimestampSet() {

        String hash1 = sendMessageCall()
                .deadline(5)
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .deadline(6)
                .callNoError()
                .getString("fullHash");

        moveTimeForward(3 * 60);


        JO actual = bundleTransactionsCall(hash1, hash2)
                .timestamp(Nxt.getEpochTime() - 2 * 60)
                .callNoError();

        // deadline is less then minimal transaction expiration because it also adjusts for transaction timestamp difference
        // due to time move and timestamp applied, now bundling transaction is 1 minute after bundled transactions, so "-1" to deadline.
        Assert.assertEquals(4 - 1, getDeadline(actual));
    }

    @Test
    public void bundleTransactionsExpired() {
        String hash1 = sendMessageCall()
                .deadline(1)
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .deadline(4)
                .callNoError()
                .getString("fullHash");

        moveTimeForward(2 * 60);


        JO actual = bundleTransactionsCall(hash1, hash2).callNoError();


        // deadline is less then minimal transaction expiration because it also adjusts for transaction timestamp difference
        // 2 because of time forward, and -1 to what is left.
        Assert.assertEquals(4 - 2 - 1, getDeadline(actual));
        final JA submittedTransactions = new JO(actual).getJo("transactionJSON").getJo("attachment").getArray("childTransactionFullHashes");
        Assert.assertEquals(Collections.singletonList(hash2), submittedTransactions);
    }

    // don't know if I should fix this case. Looks rare. Unclear how to fix.
    @Ignore
    @Test
    public void bundleTransactionsExpiredDueToRounding() {
        String hash1 = sendMessageCall()
                .deadline(1)
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .deadline(3)
                .callNoError()
                .getString("fullHash");

        moveTimeForward(2 * 60);

        final InvocationError actual = bundleTransactionsCall(hash1, hash2)
                .build().invokeWithError();
        assertEmptyTransactionsListFailure(actual);
    }

    @Test
    public void bundleTransactionsAllExpired() {
        String hash1 = sendMessageCall()
                .deadline(1)
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .deadline(1)
                .callNoError()
                .getString("fullHash");

        moveTimeForward(60);

        final InvocationError actual = bundleTransactionsCall(hash1, hash2)
                .build().invokeWithError();

        assertEmptyTransactionsListFailure(actual);
    }

    /**
     * See nxt.util.Time.CounterTime
     */
    private void moveTimeForward(int seconds) {
        for (int i = 0; i < seconds; i++) {
            Nxt.getEpochTime();
        }
    }

    @Test
    public void bundleTransactionsExpiringInDistantFuture() {

        String hash1 = sendMessageCall()
                .deadline(14400)
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .callNoError()
                .getString("fullHash");


        JO actual = bundleTransactionsCall(hash1, hash2).callNoError();

        Assert.assertEquals(10, getDeadline(actual));
    }

    @Test
    public void testEnrichmentWithoutFilter() {
        List<String> hashes = new ArrayList<>();
        hashes.add(sendMessageCall()
                .callNoError()
                .getString("fullHash"));

        hashes.add(sendMessageCall()
                .callNoError()
                .getString("fullHash"));

        //we still filter by minRateNQTPerFXT. I.e. we don't bundle transactions
        // below this rate
        String hashSmallFee = sendMessageCall()
                .feeNQT(chain.ONE_COIN / 10)
                .callNoError()
                .getString("fullHash");

        BundleTransactionsCall builder = bundleTransactionsCall(hashes.get(0))
                .isChildrenListEnrichment(true)
                .enrichmentBundlingRulesJSON("[{\"minRateNQTPerFXT\":1000000}]");
        JO actual = builder.callNoError();

        assertChildren(hashes, actual);

        actual = builder.transactionFullHash(hashes.get(0), hashSmallFee).callNoError();
        hashes.add(hashSmallFee);
        assertChildren(hashes, actual);
    }

    @Test
    public void testEnrichmentOnly() {
        List<String> hashes = Arrays.asList(
                sendMessageCall()
                        .callNoError()
                        .getString("fullHash"),
                sendMessageCall()
                        .callNoError()
                        .getString("fullHash"));

        JO actual = bundleTransactionsCall()
                .isChildrenListEnrichment(true)
                .enrichmentChildChain(chain.getId())
                .enrichmentBundlingRulesJSON("[{\"minRateNQTPerFXT\":1000000}]")
                .callNoError();

        assertChildren(hashes, actual);
    }

    @Test
    public void testEnrichmentFeeCalculation() {
        BundlerTest.stopAllDefaultBundlers();
        List<String> hashes = new ArrayList<>();
        hashes.add(sendMessageCall()
                .callNoError()
                .getString("fullHash"));
        hashes.add(sendMessageCall()
                .callNoError()
                .getString("fullHash"));

        BundleTransactionsCall builder = bundleTransactionsCall(hashes.get(0))
                .feeNQT(-1)
                .isChildrenListEnrichment(true)
                .enrichmentTotalFeeLimitFQT(FXT.ONE_COIN)
                .enrichmentBundlingRulesJSON("[{\"minRateNQTPerFXT\":1000000}]");
        JO response = builder.callNoError();

        assertChildren(hashes, response);

        // 0.01 ARDR + 0.01 ARDR - the min fee
        Assert.assertEquals(2 * FXT.ONE_COIN / 100, response.getJo("transactionJSON").getLong("feeNQT"));

        hashes.add(sendMessageCall()
                .feeNQT(3 * chain.ONE_COIN / 10)
                .callNoError()
                .getString("fullHash"));

        response = builder.enrichmentBundlingRulesJSON(
                "[{\"minRateNQTPerFXT\":100000, " +
                "\"feeCalculatorName\":\"PROPORTIONAL_FEE\"}]").callNoError();

        assertChildren(hashes, response);

        // minRateNQTPerFXT is 10 AEUR. hashes[0] is specified as transactionFullHash,
        // so the minimum fee FQT is paid for it. The fee for the two other transactions
        // is calculated proportionally
        // 0.01 ARDR + 1 / 10 + 0.3 / 10
        Assert.assertEquals(14 * FXT.ONE_COIN / 100, response.getJo("transactionJSON").getLong("feeNQT"));
    }

    @Test
    public void testEnrichmentWithFilter() {
        List<String> expected = new ArrayList<>();
        expected.add(sendMessageCall()
                .callNoError()
                .getString("fullHash"));
        sendMessageCall()
                .callNoError()
                .getString("fullHash");

        expected.add(SendMoneyCall.create(chain.getId()).secretPhrase(BOB.getSecretPhrase())
                .recipient(CHUCK.getId()).amountNQT(4 * chain.ONE_COIN).feeNQT(0)
                .callNoError()
                .getString("fullHash"));

        //enrich only with send money transactions
        TransactionType type = PaymentTransactionType.ORDINARY;
        BundleTransactionsCall builder = bundleTransactionsCall(expected.get(0))
                .isChildrenListEnrichment(true)
                .enrichmentBundlingRulesJSON("[{\"minRateNQTPerFXT\":0, " +
                        "\"filters\":[{\"name\":\"TransactionTypeBundler\", " +
                            "\"parameter\":\"" + type.getType() + ":" + type.getSubtype() + "\"}]}]");

        JO response = builder.callNoError();

        assertChildren(expected, response);
    }



    private static void assertChildren(List<String> expected, JO response) {
        JA actualHashes = response.getJo("transactionJSON")
                .getJo("attachment")
                .getArray("childTransactionFullHashes");
        Assert.assertEquals(expected.size(), actualHashes.size());

        Assert.assertEquals(new HashSet<>(expected),
                new HashSet<>(actualHashes.values()));
    }

    private BundleTransactionsCall bundleTransactionsCall(String... hashes) {
        final BundleTransactionsCall builder = BundleTransactionsCall.create(FXT.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(FXT.ONE_COIN);
        if (hashes.length == 0) {
            return builder;
        }
        return builder.transactionFullHash(hashes);
    }

    private SendMessageCall sendMessageCall() {
        return SendMessageCall.create(chain.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getStrId())
                .feeNQT(chain.ONE_COIN);
    }

    private int getDeadline(JO actual) {
        return actual.getJo("transactionJSON").getInt("deadline");
    }

    @Test
    public void bundleTransactions() {

        String hash1 = sendMessageCall()
                .callNoError()
                .getString("fullHash");

        String hash2 = sendMessageCall()
                .callNoError()
                .getString("fullHash");


        JO actual = bundleTransactionsCall(hash1, hash2)
                .deadline(10)
                .callNoError();

        Assert.assertEquals(10, getDeadline(actual));
    }

    @Test
    public void bundleTransactionsEmptyList() {
        final InvocationError actual = bundleTransactionsCall()
                .deadline(10)
                .build()
                .invokeWithError();

        Assert.assertEquals("\"transactionFullHash\" not specified", actual.getErrorDescription());
        Assert.assertEquals(3, actual.getErrorCode());
    }

    private void assertEmptyTransactionsListFailure(InvocationError actual) {
        Assert.assertEquals("Empty ChildBlockAttachment not allowed", actual.getErrorDescription());
        Assert.assertEquals(4, actual.getErrorCode());
    }
}