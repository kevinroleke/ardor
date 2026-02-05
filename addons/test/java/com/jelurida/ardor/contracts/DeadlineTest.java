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

import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static nxt.blockchain.ChildChain.IGNIS;
import static org.junit.Assert.assertTrue;

public class DeadlineTest extends AbstractContractTest {
    @Test
    public void testDefaultDeadline() {
        String contractName = ContractTestHelper.deployContract(HelloWorld.class);

        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        ContractTestHelper.messageTriggerContract(message);
        generateBlock();

        List<? extends ChildTransaction> transactions = getLastBlockChildTransactions(IGNIS.getId());
        assertTrue(transactions.size() > 0);
        ChildTransaction childTransaction = transactions.get(transactions.size() - 1);
        Assert.assertEquals(CONTRACT_RUNNER_DEFAULT_DEADLINE, childTransaction.getDeadline());
    }

    @Test
    public void testDefaultDeadlineOverride() {
        String contractName = ContractTestHelper.deployContract(DeadlineOverridingContract.class);
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        String message = messageJson.toJSONString();
        ContractTestHelper.bobPaysContract(message, ChildChain.IGNIS);

        generateBlock();

        List<? extends ChildTransaction> transactions = getLastBlockChildTransactions(IGNIS.getId());
        assertTrue(transactions.size() > 0);
        ChildTransaction childTransaction = transactions.get(transactions.size() - 1);
        Assert.assertEquals(ALICE.getId(), childTransaction.getSenderId());
        Assert.assertEquals(DeadlineOverridingContract.OVERRIDDEN_DEADLINE, childTransaction.getDeadline());
    }
}
