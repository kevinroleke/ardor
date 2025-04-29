/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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

import nxt.Constants;
import nxt.addons.JO;
import nxt.blockchain.ChildTransaction;
import nxt.http.callers.SendMoneyCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.blockchain.FxtChain.FXT;

public class ReferencedTransactionDepositTest extends AbstractContractTest {
    @Test
    public void testMultiTransactionalResponse() {

        //leave enough ARDR for only 2 transactions
        SendMoneyCall.create(FXT.getId()).secretPhrase(ALICE.getSecretPhrase()).recipient(FORGY.getRsAccount())
                .amountNQT(ALICE.getFxtBalance() - 2 * Constants.UNCONFIRMED_POOL_DEPOSIT_FQT - FXT.ONE_COIN)
                .feeNQT(FXT.ONE_COIN).callNoError();

        generateBlock();

        Assert.assertEquals(2 * Constants.UNCONFIRMED_POOL_DEPOSIT_FQT, ALICE.getFxtBalance());

        String contractName = ContractTestHelper.deployContract(SplitPayment.class);

        Assert.assertEquals(2 * Constants.UNCONFIRMED_POOL_DEPOSIT_FQT, ALICE.getFxtBalance());
        Assert.assertEquals(ALICE.getFxtBalanceDiff(), ALICE.getFxtUnconfirmedBalanceDiff());

        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        JO params = new JO();
        params.put(BOB.getRsAccount(), "0.2");
        params.put(CHUCK.getRsAccount(), "0.3");
        params.put(DAVE.getRsAccount(), "0.5");
        messageJson.put("params", params);
        String message = messageJson.toJSONString();
        ContractTestHelper.bobPaysContract(message, IGNIS);

        generateBlock();

        List<? extends ChildTransaction> transactions = getLastBlockChildTransactions(IGNIS.getId());
        Assert.assertEquals(0, transactions.size());
    }
}
