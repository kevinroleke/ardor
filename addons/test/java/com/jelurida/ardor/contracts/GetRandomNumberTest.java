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

package com.jelurida.ardor.contracts;

import nxt.addons.JO;
import nxt.http.callers.TriggerContractByRequestCall;
import org.junit.Assert;
import org.junit.Test;

public class GetRandomNumberTest extends AbstractContractTest {

    @Test
    public void submitRequest() {
        String contractName = ContractTestHelper.deployContract(GetRandomNumber.class);
        JO response = TriggerContractByRequestCall.create().contractName(contractName).
                setParamValidation(false).param("seed", 1234).callNoError();
        int randomValue = response.getInt("random");
        Assert.assertTrue(randomValue >= 0 && randomValue < 1000);
    }

}
