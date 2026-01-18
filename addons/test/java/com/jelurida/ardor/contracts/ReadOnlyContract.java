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

import nxt.addons.AbstractContract;
import nxt.addons.BlockContext;
import nxt.addons.JO;
import nxt.addons.RequestContext;
import nxt.addons.TransactionContext;
import nxt.addons.ValidateContractRunnerIsRecipient;
import nxt.addons.ValidateTransactionType;
import nxt.http.callers.SendMoneyCall;
import nxt.http.responses.TransactionResponse;

import static nxt.blockchain.TransactionTypeEnum.CHILD_PAYMENT;
import static nxt.blockchain.TransactionTypeEnum.PARENT_PAYMENT;

public class ReadOnlyContract extends AbstractContract<Void, Void> {
    private int numberOfBlocksProcessed = 0;
    @Override
    public JO processBlock(BlockContext context) {
        numberOfBlocksProcessed++;

        return null;
    }

    @Override
    @ValidateContractRunnerIsRecipient
    @ValidateTransactionType(accept = { PARENT_PAYMENT, CHILD_PAYMENT })
    public JO processTransaction(TransactionContext context) {
        TransactionResponse triggerTransaction = context.getTransaction();
        SendMoneyCall call = SendMoneyCall.create(triggerTransaction.getChainId())
                .recipient(triggerTransaction.getSenderRs())
                .amountNQT(triggerTransaction.getAmount());
        return context.createTransaction(call, false);
    }

    @Override
    public JO processRequest(RequestContext context) {
        JO result = new JO();
        result.put("numberOfBlocksProcessed", numberOfBlocksProcessed);
        return result;
    }

}
