/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TriggerContractByRequestCall;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.blockchain.FxtChain.FXT;

public class ReadOnlyContractTest  extends AbstractContractTest {
    @Test
    public void testNoSecretRun() {
        //test that the contract runner doesn't need ARDR
        SendMoneyCall.create(FXT.getId()).
                privateKey(ALICE.getPrivateKey()).recipient(BOB.getId()).
                feeNQT(FXT.ONE_COIN).amountNQT(ALICE.getFxtBalance() - FXT.ONE_COIN).callNoError();
        generateBlock();

        JO runnerConfig = new JO();
        runnerConfig.put("secretPhrase", "");
        runnerConfig.put("accountRS", ALICE.getRsAccount());
        runnerConfig.put("mode", "READ_ONLY");
        setRunnerConfig(runnerConfig);

        String contractName = ContractTestHelper.deployContract(ReadOnlyContract.class, null, false);

        generateBlock();
        generateBlock();

        JO response = TriggerContractByRequestCall.create().contractName(contractName).callNoError();
        Assert.assertEquals(2, response.getInt("numberOfBlocksProcessed"));


        // contract should not be able to submit transaction
        JO messageJson = new JO();
        messageJson.put("contract", contractName);

        SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(BlockchainTest.BOB.getSecretPhrase())
                .recipient(BlockchainTest.ALICE.getId())
                .amountNQT(IGNIS.ONE_COIN)
                .messageIsPrunable(true)
                .message(messageJson.toJSONString())
                .feeNQT(IGNIS.ONE_COIN)
                .callNoError();
        generateBlock();

        Assert.assertEquals(-2 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }
}
