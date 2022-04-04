/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

import nxt.BlockchainTest;
import nxt.Constants;
import nxt.addons.JO;
import nxt.blockchain.FxtChain;
import nxt.http.callers.GetAccountCall;
import nxt.http.callers.LeaseBalanceCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

public class LeaseTest extends BlockchainTest {

    @Test
    public void lease() {
        generateBlock(); // start from baseHeight + 1
        // #2 & #3 lease their balance to %1
        JO response = LeaseBalanceCall.create(FxtChain.FXT.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                recipient(ALICE.getStrId()).
                period(2).
                feeNQT(Constants.ONE_FXT * 2).
                callNoError();
        Logger.logDebugMessage("leaseBalance: " + response);
        response = LeaseBalanceCall.create(FxtChain.FXT.getId()).
                secretPhrase(CHUCK.getSecretPhrase()).
                recipient(ALICE.getStrId()).
                period(3).
                feeNQT(Constants.ONE_FXT * 2).
                callNoError();
        Logger.logDebugMessage("leaseBalance: " + response);
        generateBlock();

        // effective balance hasn't changed since lease is not in effect yet
        JO lesseeResponse = GetAccountCall.create().
                account(ALICE.getRsAccount()).
                includeEffectiveBalance(true).
                callNoError();
        Logger.logDebugMessage("getLesseeAccount: " + lesseeResponse);
        Assert.assertEquals(ALICE.getInitialFxtBalance() / Constants.ONE_FXT, lesseeResponse.get("effectiveBalanceFXT"));

        // lease is registered
        JO leasedResponse1 = GetAccountCall.create().
                account(BOB.getRsAccount()).
                callNoError();
        Logger.logDebugMessage("getLeasedAccount: " + leasedResponse1);
        Assert.assertEquals(ALICE.getRsAccount(), leasedResponse1.get("currentLesseeRS"));
        Assert.assertEquals(baseHeight + 1 + 1 + 1, leasedResponse1.getLong("currentLeasingHeightFrom"));
        Assert.assertEquals(baseHeight + 1 + 1 + 1 + 2, leasedResponse1.getLong("currentLeasingHeightTo"));
        JO leasedResponse2 = GetAccountCall.create().
                account(CHUCK.getRsAccount()).
                callNoError();
        Logger.logDebugMessage("getLeasedAccount: " + leasedResponse1);
        Assert.assertEquals(ALICE.getRsAccount(), leasedResponse2.get("currentLesseeRS"));
        Assert.assertEquals(baseHeight + 1 + 1 + 1, leasedResponse2.getLong("currentLeasingHeightFrom"));
        Assert.assertEquals(baseHeight + 1 + 1 + 1 + 3, leasedResponse2.getLong("currentLeasingHeightTo"));
        generateBlock();


        lesseeResponse = GetAccountCall.create().
                account(ALICE.getRsAccount()).
                includeEffectiveBalance(true).
                callNoError();
        Logger.logDebugMessage("getLesseeAccount: " + lesseeResponse);
        Assert.assertEquals((ALICE.getInitialFxtBalance() + BOB.getInitialFxtBalance() + CHUCK.getInitialFxtBalance()) / Constants.ONE_FXT - 4,
                lesseeResponse.get("effectiveBalanceFXT"));
        generateBlock();
        generateBlock();
        lesseeResponse = GetAccountCall.create().
                account(ALICE.getRsAccount()).
                includeEffectiveBalance(true).
                callNoError();
        Logger.logDebugMessage("getLesseeAccount: " + lesseeResponse);
        Assert.assertEquals((ALICE.getInitialFxtBalance() + CHUCK.getInitialFxtBalance()) / Constants.ONE_FXT - 2 /* fees */,
                lesseeResponse.get("effectiveBalanceFXT"));
        generateBlock();
        lesseeResponse = GetAccountCall.create().
                account(ALICE.getRsAccount()).
                includeEffectiveBalance(true).
                callNoError();
        Logger.logDebugMessage("getLesseeAccount: " + lesseeResponse);
        Assert.assertEquals((ALICE.getInitialFxtBalance()) / Constants.ONE_FXT,
                lesseeResponse.get("effectiveBalanceFXT"));
    }
}
