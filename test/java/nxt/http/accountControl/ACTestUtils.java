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

package nxt.http.accountControl;

import nxt.Tester;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.GetBalanceCall;
import nxt.http.callers.GetPhasingOnlyControlCall;
import nxt.http.callers.GetPhasingPollCall;
import nxt.http.callers.GetTransactionCall;
import nxt.http.callers.IssueAssetCall;
import nxt.util.Logger;
import org.junit.Assert;

import static nxt.BlockchainTest.ALICE;
import static nxt.blockchain.ChildChain.IGNIS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ACTestUtils {

    public static IssueAssetCall issueAssetBuilder(String secretPhrase, String assetName) {
        return IssueAssetCall.create(IGNIS.getId())
                .name(assetName)
                .description("Unit tests asset")
                .quantityQNT(10000)
                .decimals(4)
                .secretPhrase(secretPhrase)
                .feeNQT(1000 * IGNIS.ONE_COIN);
    }

    public static void assertNoPhasingOnlyControl() {
        JO response = GetPhasingOnlyControlCall.create().account(ALICE.getId()).callNoError();
        Assert.assertTrue(response.isEmpty());
    }

    public static void assertPendingTransaction(String fullHash) {
        assertEquals(0, ACTestUtils.getConfirmationsNumber(fullHash));
    }

    public static void assertConfirmedTransaction(String fullHash) {
        int confirmationsNumber = ACTestUtils.getConfirmationsNumber(fullHash);
        assertTrue("At least one confirmation required, found: " + confirmationsNumber, 0 < confirmationsNumber);
    }

    private static int getConfirmationsNumber(String fullHash) {
        return GetTransactionCall.create(IGNIS.getId())
                .fullHash(fullHash)
                .callNoError()
                .getInt("confirmations");
    }

    public static JO assertTransactionSuccess(APICall.Builder<?> builder) {
        JO response = builder.callNoError();
        
        Logger.logMessage(builder.getParam("requestType") + " response: " + response.toJSONString());
        String result = response.getString("fullHash");
        Assert.assertNotNull(result);
        return response;
    }
    
    public static void assertTransactionBlocked(APICall.Builder<?> builder) {
        JO response = builder.call();
        
        Logger.logMessage(builder.getParam("requestType") + " response: " + response.toJSONString());
        
        //Assert.assertNotNull("Transaction wasn't even created", response.get("transaction"));
        
        String errorMsg = response.getString("error");
        Assert.assertNotNull("Transaction should fail, but didn't", errorMsg);
        Assert.assertTrue(errorMsg.contains("nxt.NxtException$AccountControlException"));
    }
    
    public static long getAccountBalance(long account, String balance) {
        JO response = GetBalanceCall.create(IGNIS.getId()).account(account).callNoError();
        
        Logger.logMessage("getBalance response: " + response.toJSONString());
        
        return Long.parseLong(((String)response.get(balance)));
    }

    public static JO approve(Object fullHash, Tester approver, String secret) {
        return approveBuilder(fullHash, approver, secret).callNoError();
    }

    public static ApproveTransactionCall approveBuilder(Object fullHash, Tester approver, String secret) {
        ApproveTransactionCall builder = ApproveTransactionCall.create(IGNIS.getId())
                .secretPhrase(approver.getSecretPhrase())
                .revealedSecretText(secret)
                .feeNQT(IGNIS.ONE_COIN);
        if (fullHash != null) {
            builder = builder.phasedTransaction(IGNIS.getId() + ":" + fullHash);
        }
        return builder;
    }

    public enum PhasingStatus {
        NONE, //no transaction
        PENDING,
        REJECTED,
        APPROVED
    }

    public static PhasingStatus getPhasingStatus(String fullHash) {
        JO resp = GetPhasingPollCall.create()
                .transactionFullHash(fullHash)
                .countVotes(true)
                .callNoError();

        Object approved = resp.get("approved");

        if (approved != null) {
            if (Boolean.TRUE.equals(approved)) {
                return PhasingStatus.APPROVED;
            } else {
                return PhasingStatus.REJECTED;
            }
        } else {
            Long finishHeight = (Long) resp.get("finishHeight");
            if (finishHeight != null) {
                return PhasingStatus.PENDING;
            } else {
                return PhasingStatus.NONE;
            }
        }

    }
}
