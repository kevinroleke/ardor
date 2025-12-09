/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
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

package nxt.http;

import nxt.account.Account;
import nxt.NxtException;
import nxt.account.BalanceHome;
import nxt.db.DbIterator;
import nxt.util.Convert;
import nxt.blockchain.Chain;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetRichAccounts extends APIServlet.APIRequestHandler {

    static final GetRichAccounts instance = new GetRichAccounts();

    private GetRichAccounts() {
        super(new APITag[] {APITag.ACCOUNTS}, "chain", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Chain chain = ParameterParser.getChain(req);
        if (chain == null) {
            return JSONResponses.MISSING_CHAIN;
        }
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray accountsJSONArray = new JSONArray();
        try (DbIterator<BalanceHome.Balance> balances = chain.getBalanceHome().getTopBalances(firstIndex, lastIndex)) {
            for (BalanceHome.Balance balance : balances) {
                JSONObject accountJSON = new JSONObject();
                JSONData.putAccount(accountJSON, "account", balance.getAccountId());
                accountJSON.put("balance", balance.getBalance());
                accountsJSONArray.add(accountJSON);
            }
        }
        response.put("accounts", accountsJSONArray);
        return response;
    }
}
