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

package com.jelurida.ardor.contracts;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.callers.GetAccountCall;
import org.junit.Assert;
import org.junit.Test;

public class LeaseRenewalTest extends AbstractContractTest {

    @Test
    public void multiLease() {
        JO setupParams = new JO();
        setupParams.put("leasePeriod", 4);
        setupParams.put("leaseRenewalWarningPeriod", 2);
        ContractTestHelper.deployContract(LeaseRenewal.class, setupParams);

        generateBlockWithDescription("Contract should submit lease transactions now");
        generateBlocksWithDescription(3, "Wait the leasing delay for our stake to mature 2+1 blocks");

        JO response = GetAccountCall.create().
                account(ALICE.getRsAccount()).
                includeLessors(true).
                callNoError();
        JA lessorsInfo = response.getArray("lessorsInfo");
        Assert.assertEquals(3, lessorsInfo.size());
        for (JO lessor : lessorsInfo.objects()) {
            Assert.assertEquals(5, lessor.getInt("currentHeightFrom"));
            Assert.assertEquals(9, lessor.getInt("currentHeightTo"));
        }

        // Now wait for the lease to progress
        generateBlockWithDescription("Renew lease"); //
        generateBlocksWithDescription(3, "Process the new leasing transactions");
        response = GetAccountCall.create().
                account(ALICE.getRsAccount()).
                includeLessors(true).
                callNoError();
        lessorsInfo = response.getArray("lessorsInfo");
        Assert.assertEquals(3, lessorsInfo.size());
        for (JO lessor : lessorsInfo.objects()) {
            Assert.assertEquals(9, lessor.getInt("currentHeightFrom"));
            Assert.assertEquals(13, lessor.getInt("currentHeightTo"));
        }
    }

}
