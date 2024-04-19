/*
 * Copyright © 2022-2023 Jelurida IP B.V.
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
package com.jelurida.ardor.contracts;

import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.callers.GetBalanceCall;
import nxt.http.callers.TriggerContractByRequestCall;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;
import static org.junit.Assert.*;

public class ManagedAccountsTest extends AbstractContractTest {
    @Test
    public void testManagedAccount() {
        JO runnerConfig = new JO();
        runnerConfig.put("managedAccountsMnemonic", "lamp style brief decorate excuse special receive any fever margin square penalty");
        setRunnerConfig(runnerConfig);

        String contractName = ContractTestHelper.deployContract(ManagedAccountsTestContract.class);

        TriggerContractByRequestCall contractRequest = TriggerContractByRequestCall.create().contractName(contractName)
                .setParamValidation(false);
        JO response = contractRequest.param("operation", "fund")
                .param("recipientIndex", 3)
                .param("amount", 20).callNoError();
        response.getString("fullHash");

        generateBlock();

        response = contractRequest.param("operation", "getId").param("index", 4).callNoError();

        String recipientId = response.getString("accountId");

        response = contractRequest.param("operation", "transfer")
                .param("senderIndex", 3)
                .param("recipientId", recipientId)
                .param("amount", 6).callNoError();
        response.getString("fullHash");

        generateBlock();

        Assert.assertEquals(6 * IGNIS.ONE_COIN, IGNIS.getBalanceHome().getBalance(Long.parseUnsignedLong(recipientId)).getBalance());
    }
}