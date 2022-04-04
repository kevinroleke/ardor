/*
 * Copyright © 2021-2022 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */
package com.jelurida.ardor.contracts;

import nxt.addons.AbstractContract;
import nxt.addons.JO;
import nxt.addons.TransactionContext;
import nxt.addons.ValidateContractRunnerIsRecipient;
import nxt.addons.ValidateTransactionType;
import nxt.http.callers.SendMoneyCall;
import nxt.http.responses.TransactionResponse;

import static nxt.blockchain.TransactionTypeEnum.CHILD_PAYMENT;
import static nxt.blockchain.TransactionTypeEnum.PARENT_PAYMENT;

public class DeadlineOverridingContract extends AbstractContract<Void, Void> {

    public static final int OVERRIDDEN_DEADLINE = 35;

    @Override
    @ValidateContractRunnerIsRecipient
    @ValidateTransactionType(accept = { PARENT_PAYMENT, CHILD_PAYMENT })
    public JO processTransaction(TransactionContext context) {
        TransactionResponse triggerTransaction = context.getTransaction();
        SendMoneyCall call = SendMoneyCall.create(triggerTransaction.getChainId())
                .recipient(triggerTransaction.getSenderRs())
                .amountNQT(triggerTransaction.getAmount())
                .deadline(OVERRIDDEN_DEADLINE);
        return context.createTransaction(call, false);
    }
}
