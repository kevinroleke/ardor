/*
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

package nxt.http.bundling;

import nxt.BlockchainTest;
import nxt.RequireNonePermissionPolicyTestsCategory;
import nxt.Tester;
import nxt.addons.JO;
import nxt.http.callers.ShufflingProcessCall;
import nxt.http.callers.ShufflingRegisterCall;
import nxt.http.callers.ShufflingVerifyCall;
import nxt.http.callers.StartBundlerCall;
import nxt.http.shuffling.ShufflingUtil;
import nxt.shuffling.ShufflingStage;
import nxt.util.JSONAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.http.shuffling.ShufflingUtil.ALICE_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.BOB_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.CHUCK_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.DAVE_RECIPIENT;
import static nxt.http.shuffling.ShufflingUtil.getShuffling;

// since all tests use Shuffling.
@Category(RequireNonePermissionPolicyTestsCategory.class)
public class TransactionTypeBundlerTest extends BlockchainTest {
    @Test
    public void testSuccessfulShuffling() {
        startShufflingBundler("7:1,7:2,7:3,7:4,7:5");
        String shufflingFullHash = new JSONAssert(ShufflingUtil.create(ALICE, 4)).fullHash();
        generateBlock();

        registration(shufflingFullHash);

        processing(shufflingFullHash);

        JO shufflingResponse = getShuffling(shufflingFullHash);
        Assert.assertEquals((long) ShufflingStage.VERIFICATION.getCode(), shufflingResponse.get("stage"));
        String shufflingStateHash = (String)shufflingResponse.get("shufflingStateHash");

        verification(shufflingFullHash, shufflingStateHash);
        shufflingResponse = getShuffling(shufflingFullHash);
        Assert.assertEquals((long) ShufflingStage.DONE.getCode(), shufflingResponse.get("stage"));
    }

    @Test
    public void testProcessingNotBundled() {
        startShufflingBundler("7:1,7:3,7:4,7:5");
        String shufflingFullHash = new JSONAssert(ShufflingUtil.create(ALICE, 4)).fullHash();
        generateBlock();

        registration(shufflingFullHash);

        process(shufflingFullHash, ALICE, ALICE_RECIPIENT);
        generateBlock();

        JO shufflingResponse = getShuffling(shufflingFullHash);
        Assert.assertEquals(ALICE.getStrId(), shufflingResponse.get("assignee"));
    }

    private void registration(String shufflingFullHash) {
        register(BOB, shufflingFullHash);
        generateBlock();
        register(CHUCK, shufflingFullHash);
        generateBlock();
        register(DAVE, shufflingFullHash);
        generateBlock();
    }

    private void processing(String shufflingFullHash) {
        process(shufflingFullHash, ALICE, ALICE_RECIPIENT);
        generateBlock();
        process(shufflingFullHash, BOB, BOB_RECIPIENT);
        generateBlock();
        process(shufflingFullHash, CHUCK, CHUCK_RECIPIENT);
        generateBlock();
        process(shufflingFullHash, DAVE, DAVE_RECIPIENT);
        generateBlock();
    }

    private void verification(String shufflingFullHash, String shufflingStateHash) {
        verify(shufflingFullHash, ALICE, shufflingStateHash);
        verify(shufflingFullHash, BOB, shufflingStateHash);
        verify(shufflingFullHash, CHUCK, shufflingStateHash);
        generateBlock();
    }

    private static void register(Tester tester, String shufflingFullHash) {
        new JSONAssert(ShufflingRegisterCall.create(IGNIS.getId()).
                secretPhrase(tester.getSecretPhrase()).
                shufflingFullHash(shufflingFullHash).
                feeNQT(0).call()).fullHash();
    }


    private static void process(String shufflingFullHash, Tester tester, Tester recipient) {
        new JSONAssert(ShufflingProcessCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                secretPhrase(tester.getSecretPhrase()).
                recipientSecretPhrase(recipient.getSecretPhrase()).
                feeNQT(0).call()).fullHash();
    }

    private static void verify(String shufflingFullHash, Tester tester, String shufflingStateHash) {
        new JSONAssert(ShufflingVerifyCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                secretPhrase(tester.getSecretPhrase()).
                shufflingStateHash(shufflingStateHash).
                feeNQT(0).call());
    }

    private void startShufflingBundler(String types) {
        JSONAssert result = new JSONAssert(StartBundlerCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                filter("TransactionTypeBundler:" + types).
                minRateNQTPerFXT(0).
                feeCalculatorName("MIN_FEE").call());
        result.str("totalFeesLimitFQT");
    }
}
