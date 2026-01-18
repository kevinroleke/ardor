/*
 * Copyright © 2022-2023 Jelurida IP B.V.
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
import nxt.blockchain.ChildChain;
import nxt.http.bundling.BundlerTest;
import nxt.http.callers.GetBalanceCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.StartBundlerCall;
import nxt.http.callers.TriggerContractByRequestCall;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;
import static org.junit.Assert.*;

public class ManagedAccountsTest extends AbstractContractTest {

    @Before
    public void setUp() {
        JO runnerConfig = new JO();
        runnerConfig.put("managedAccountsMnemonic", "lamp style brief decorate excuse special receive any fever margin square penalty");
        setRunnerConfig(runnerConfig);
    }

    @Test
    public void testManagedAccount() {
        String contractName = ContractTestHelper.deployContract(ManagedAccountsTestContract.class);

        TriggerContractByRequestCall contractRequest = TriggerContractByRequestCall.create().contractName(contractName)
                .setParamValidation(false);
        contractRequest.param("operation", "fund")
                .param("recipientIndex", 3)
                .param("amount", 20).callNoError();

        generateBlock();

        String recipientId = getManagedAccountId(contractRequest, 4);

        contractRequest.param("operation", "transfer")
                .param("senderIndex", 3)
                .param("recipientId", recipientId)
                .param("amount", 6).callNoError();

        generateBlock();

        Assert.assertEquals(6 * IGNIS.ONE_COIN, IGNIS.getBalanceHome().getBalance(Long.parseUnsignedLong(recipientId)).getBalance());
    }

    private static String getManagedAccountId(TriggerContractByRequestCall contractRequest, int index) {
        JO response;
        response = contractRequest.param("operation", "getId").param("index", index).callNoError();
        return response.getString("accountId");
    }

    @Test
    public void testManagedAccountsBundler() {
        String contractName = ContractTestHelper.deployContract(ManagedAccountsTestContract.class);

        TriggerContractByRequestCall contractRequest = TriggerContractByRequestCall.create().contractName(contractName)
                .setParamValidation(false);

        String masterPublicKey = getMasterPublicKey(contractName);

        contractRequest.param("operation", "fund")
                .param("recipientIndex", 3)
                .param("amount", 20).callNoError();
        generateBlock();

        String recipientId = getManagedAccountId(contractRequest, 4);

        BundlerTest.stopAllDefaultBundlers();
        try {
            JO response = contractRequest.param("operation", "transfer")
                    .param("senderIndex", 3)
                    .param("recipientId", recipientId)
                    .param("amount", 6).callNoError();
            String fullHash = getFullHash(response);
            generateBlock();

            Assert.assertFalse(BundlerTest.isBundled(fullHash));

            StartBundlerCall.create(IGNIS.getId())
                    .secretPhrase(BOB.getSecretPhrase())
                    .filter("ManagedAccountsBundler:" + masterPublicKey)
                    .minRateNQTPerFXT(0)
                    .feeCalculatorName("MIN_FEE").callNoError();

            generateBlock();
            Assert.assertTrue(BundlerTest.isBundled(fullHash));

        } finally {
            startBundlers();
        }
    }

    @Test
    public void testNoIndexHint() {
        String contractName = ContractTestHelper.deployContract(ManagedAccountsTestContract.class);
        String masterPublicKey = getMasterPublicKey(contractName);

        BundlerTest.stopAllDefaultBundlers();

        //the bundler must not return error when a message is not a json or doesn't contain maIdx
        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(CHUCK.getSecretPhrase()).recipient(BOB.getId())
                .amountNQT(IGNIS.ONE_COIN)
                .message("{}")
                .feeNQT(0);
        String fullHash = sendMoneyCall.callNoError().getString("fullHash");

        String fullHash2 = sendMoneyCall.message("abasd").callNoError().getString("fullHash");

        Assert.assertFalse(BundlerTest.isBundled(fullHash));

        Assert.assertFalse(BundlerTest.isBundled(fullHash2));

        StartBundlerCall.create(IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .filter("ManagedAccountsBundler:" + masterPublicKey)
                .minRateNQTPerFXT(0)
                .feeCalculatorName("MIN_FEE").callNoError();

        Assert.assertFalse(BundlerTest.isBundled(fullHash));

    }

    private static String getMasterPublicKey(String contractName) {
        JO response;
        TriggerContractByRequestCall contractRequest1 = TriggerContractByRequestCall.create().contractName(contractName)
                .setParamValidation(false);
        response = contractRequest1.param("operation", "getMasterPublicKey").callNoError();

        return response.getString("masterPublicKey");
    }


    private static String getFullHash(JO response) {
        return response.getJo("submitContractTransactionsResponse")
                .getArray("broadcastFullHashes").getString(0);
    }
}