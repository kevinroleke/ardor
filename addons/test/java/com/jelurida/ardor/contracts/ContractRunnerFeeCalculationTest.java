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

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.http.callers.SendMoneyCall;
import nxt.util.Logger;
import org.junit.Test;

public class ContractRunnerFeeCalculationTest extends AbstractContractTest {

    @Test
    public void testIgnisFixedRateFee() {
        String contractName = ContractTestHelper.deployContract(ReturnMoney.class);
        String triggerFullHash = triggerContract(contractName, ChildChain.IGNIS);
        generateBlockWithDescription("Contract should submit transaction now");

        // Verify that the contract send back a message
        testAndGetLastChildTransaction(ChildChain.IGNIS.getId(), 0, 0,
                a -> true, 4000000L,
                ALICE, BOB);
        /*
         Fee calculation: 0.01 child chain general fee (sendMessage) + 0.01 referenced tx fee
         Total fee: 0.02 ARDR (FXT)
         Contract runner configured with feeRateNQTPerFXT.IGNIS=200000000
         Expected fee: 0.02 (FXT) * 200000000 (NQT/FXT) = 4000000 NQT
         */
    }

    @Test
    public void testArdorFixedRateFee() {
        String contractName = ContractTestHelper.deployContract(ReturnMoney.class);
        triggerContract(contractName, FxtChain.FXT);
        generateBlockWithDescription("Contract should submit transaction now");

        // Verify that the contract send back a message
        testAndGetLastParentTransaction(FxtChain.FXT.getId(), -2, 0,
                a -> true, FxtChain.FXT.ONE_COIN,
                ALICE, BOB);
        /*
         Expected fee: 1 ARDR (default Ardor fee)
         */
    }

    @Test
    public void testAeurFixedRateFee() {
        String contractName = ContractTestHelper.deployContract(ReturnMoney.class);
        String triggerFullHash = triggerContract(contractName, ChildChain.AEUR);
        generateBlockWithDescription("Contract should submit transaction now");

        // Verify that the contract send back a message
        testAndGetLastChildTransaction(ChildChain.AEUR.getId(), 0, 0,
                a -> true, 400,
                ALICE, BOB);
        /*
         Fee calculation: 0.01 child chain general fee (sendMessage) + 0.01 referenced tx fee
         Total fee: 0.02 ARDR (FXT)
         Contract runner configured with feeRateNQTPerFXT.AEUR=20000
         Expected fee: 0.02 (FXT) * 20000 (NQT/FXT) = 400 NQT
         */
    }

    private String triggerContract(String contractName, Chain chain) {
        // Send message to trigger the contract execution
        JO messageJson = new JO();
        messageJson.put("contract", contractName);

        JO response = SendMoneyCall.create(chain.getId())
                .secretPhrase(BlockchainTest.BOB.getSecretPhrase())
                .recipient(BlockchainTest.ALICE.getId())
                .amountNQT(chain.ONE_COIN)
                .messageIsPrunable(true)
                .message(messageJson.toJSONString())
                .feeNQT(chain.ONE_COIN)
                .callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockWithDescription("Trigger contract");
        return (String)response.get("fullHash");
    }
}
