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

import nxt.account.Account;
import nxt.addons.AbstractContract;
import nxt.addons.JO;
import nxt.addons.RequestContext;
import nxt.http.callers.SendMoneyCall;
import nxt.util.Convert;

import static nxt.blockchain.ChildChain.IGNIS;

public class ManagedAccountsTestContract extends AbstractContract<Void,Void> {
    @Override
    public JO processRequest(RequestContext context) {
        String op = context.getParameter("operation");
        switch (op) {
            case "fund":
                return fund(context);
            case "transfer":
                return transfer(context);
            case "getId":
                return getId(context);
            case "getMasterPublicKey":
                return getMasterPublicKey(context);
        }
        return context.generateErrorResponse(10001, "Please specify operation parameter");
    }


    /**
     * Fund a managed (user's) account from contract account, which acts as treasury
     *
     * @param context Context with parameters recipientIndex and amount
     * @return transaction
     */
    private static JO fund(RequestContext context) {
        int recipientIndex = Integer.parseInt(context.getParameter("recipientIndex"));
        long amount = Long.parseLong(context.getParameter("amount"));
        byte[] recipientPubKey = context.getConfig().getManagedAccountPublicKey(recipientIndex);
        return context.createTransaction(SendMoneyCall.create(IGNIS.getId())
                .amountNQT(amount * IGNIS.ONE_COIN)
                .recipient(Account.getId(recipientPubKey))
                .recipientPublicKey(recipientPubKey));
    }

    /**
     * Transfer from managed account
     *
     * @param context Context with parameters senderIndex, amount and recipientId
     * @return transaction
     */
    private static JO transfer(RequestContext context) {
        int senderIndex = Integer.parseInt(context.getParameter("senderIndex"));
        long amount = Long.parseLong(context.getParameter("amount"));
        String recipientId = context.getParameter("recipientId");
        return context.createTransaction(SendMoneyCall.create(IGNIS.getId())
                .amountNQT(amount * IGNIS.ONE_COIN)
                .recipient(recipientId), false, senderIndex);
    }

    /**
     * Get managed account ID
     *
     * @param context Context with parameter index
     * @return JSON with accountId entry
     */
    private static JO getId(RequestContext context) {
        int index = Integer.parseInt(context.getParameter("index"));
        long id = context.getConfig().getManagedAccountId(index);
        JO response = new JO();
        response.put("accountId", Long.toUnsignedString(id));
        response.put("accountIdRS", Convert.rsAccount(id));
        return context.generateResponse(response);
    }

    private JO getMasterPublicKey(RequestContext context) {
        JO response = new JO();
        response.put("masterPublicKey", Convert.toHexString(context.getConfig().getManagedAccountsMasterPublicKey()));
        return context.generateResponse(response);
    }
}
