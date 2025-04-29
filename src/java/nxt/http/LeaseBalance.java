/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
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

package nxt.http;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.account.AccountControlFxtTransactionType;
import nxt.account.EffectiveBalanceLeasingAttachment;
import nxt.blockchain.Attachment;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class LeaseBalance extends CreateTransaction {

    static final LeaseBalance instance = new LeaseBalance();

    private LeaseBalance() {
        super(AccountControlFxtTransactionType.EFFECTIVE_BALANCE_LEASING,
                new APITag[] {APITag.FORGING, APITag.ACCOUNT_CONTROL, APITag.CREATE_TRANSACTION},
                "period", "recipient");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        int period = ParameterParser.getInt(req, "period", Constants.LEASING_DELAY,
                Constants.LONG_LEASE_PERIOD_LIMIT, true);
        Account account = ParameterParser.getSenderAccount(req);
        long recipient = ParameterParser.getAccountId(req, "recipient", true);
        Account recipientAccount = Account.getAccount(recipient);
        if (recipientAccount == null || Account.getPublicKey(recipientAccount.getId()) == null) {
            JSONObject response = new JSONObject();
            response.put("errorCode", 8);
            response.put("errorDescription", "recipient account does not have public key");
            return response;
        }
        boolean isShortPeriod = Constants.isAutomatedTest && Nxt.getBlockchain().getHeight() < Constants.LEASING_PERIOD_INCREASE;
        Attachment attachment = new EffectiveBalanceLeasingAttachment(period, isShortPeriod);
        return transactionParameters(req, account, attachment).setRecipientId(recipient).createTransaction();
    }
}
