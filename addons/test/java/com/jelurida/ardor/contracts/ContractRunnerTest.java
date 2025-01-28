/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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

import nxt.addons.ContractLoader;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TriggerContractByRequestCall;
import nxt.http.responses.TransactionResponse;
import nxt.util.Convert;
import org.junit.Assert;
import org.junit.Test;

import java.util.stream.IntStream;

public class ContractRunnerTest extends AbstractContractTest {
    public static final String LONG_STRING = "11111111112222222222333333333344444444445555555555" +
            "66666666667777777777888888888899999999990000000000";

    @Test
    public void testTransferAssetQty0() {
        String contractName = ContractTestHelper.deployContract(AssetTransferQty0Contract.class);
        TransactionResponse issueAssetTransaction = IssueAssetCall.create(2).name("myAsset").description("...").
                quantityQNT(123456789).decimals(4).privateKey(ALICE.getPrivateKey()).feeNQT(100 * ChildChain.IGNIS.ONE_COIN).
                getCreatedTransaction();

        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        JO params = new JO();
        params.put("assetId", Convert.fullHashToId(issueAssetTransaction.getFullHash()));
        messageJson.put("params", params);

        String message = messageJson.toJSONString();
        ContractTestHelper.messageTriggerContract(message);
        generateBlock();

    }

    @Test
    public void testMaximumAllowedSize() {
        JO params = new JO();
        params.put("str", LONG_STRING + "11111111112222222222333333333344444444445555555555");
        ContractTestHelper.deployContract(ParametrizedTestContract.class, params);
    }

    @Test(expected = AssertionError.class)
    public void testOverMaximumAllowedSize() {
        JO params = new JO();
        params.put("str", LONG_STRING + "11111111112222222222333333333344444444445555555555" + "X");
        ContractTestHelper.deployContract(ParametrizedTestContract.class, params);
    }

    @Test
    public void testImportFromPrunable() {
        JO params = new JO();
        params.put("str", LONG_STRING);
        JO paramsInMessage = new JO();
        JO obj = new JO();
        IntStream.range(0, 10).forEach(i -> obj.put("" + i, LONG_STRING));
        paramsInMessage.put("obj", obj);
        paramsInMessage.put(ContractLoader.PARAMS_DETACHED_TO_PRUNABLE_MESSAGE, true);
        params.put(ContractLoader.PARAMS_DETACHED_TO_MESSAGE_FIELD, paramsInMessage);
        String contractName = ContractTestHelper.deployContract(ParametrizedTestContract.class, params);
        JO response = TriggerContractByRequestCall.create().contractName(contractName).callNoError();
        Assert.assertEquals(LONG_STRING, response.getString("str"));
        Assert.assertEquals(obj, response.getJo("obj"));
    }

    @Test
    public void testImportFromNonPrunable() {
        JO params = new JO();
        params.put("str", LONG_STRING);
        JO paramsInMessage = new JO();
        JO obj = new JO();
        obj.put("key", LONG_STRING + "111111111122222222223333333333444444444455");
        paramsInMessage.put("obj", obj);
        params.put(ContractLoader.PARAMS_DETACHED_TO_MESSAGE_FIELD, paramsInMessage);
        String contractName = ContractTestHelper.deployContract(ParametrizedTestContract.class, params);
        JO response = TriggerContractByRequestCall.create().contractName(contractName).callNoError();
        Assert.assertEquals(LONG_STRING, response.getString("str"));
        Assert.assertEquals(obj, response.getJo("obj"));
    }

    @Test(expected = AssertionError.class)
    public void testExceedNonPrunableSize() {
        JO params = new JO();
        params.put("str", LONG_STRING);
        JO paramsInMessage = new JO();
        JO obj = new JO();
        obj.put("key", LONG_STRING + "111111111122222222223333333333444444444455" + "X");
        paramsInMessage.put("obj", obj);
        params.put(ContractLoader.PARAMS_DETACHED_TO_MESSAGE_FIELD, paramsInMessage);
        ContractTestHelper.deployContract(ParametrizedTestContract.class, params);
    }

    @Test
    public void testImportFromCompressedNonPrunable() {
        JO params = new JO();
        params.put("str", LONG_STRING);
        JO paramsInMessage = new JO();
        JO obj = new JO();
        obj.put("key", LONG_STRING + LONG_STRING);
        paramsInMessage.put("obj", obj);
        paramsInMessage.put(ContractLoader.PARAMS_DETACHED_TO_COMPRESSED_MESSAGE, true);
        params.put(ContractLoader.PARAMS_DETACHED_TO_MESSAGE_FIELD, paramsInMessage);
        String contractName = ContractTestHelper.deployContract(ParametrizedTestContract.class, params);
        JO response = TriggerContractByRequestCall.create().contractName(contractName).callNoError();
        Assert.assertEquals(LONG_STRING, response.getString("str"));
        Assert.assertEquals(obj, response.getJo("obj"));
    }

    @Test
    public void testRunningWithoutArdr() {
        SendMoneyCall.create(FxtChain.FXT.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .amountNQT(ALICE.getFxtBalance() - FxtChain.FXT.ONE_COIN)
                .feeNQT(FxtChain.FXT.ONE_COIN)
                .recipient(BOB.getId()).callNoError();
        generateBlock();


        String contractName = ContractTestHelper.deployContract(HelloWorld.class);

        // Send message to trigger the contract execution
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        ContractTestHelper.messageTriggerContract(message);
        // Contract should submit transaction now
        generateBlock();

        // Verify that the contract send back a message
        testAndGetLastChildTransaction(2, 1, 0,
                a -> true, 2000000L,
                ALICE, BOB);

    }
}
