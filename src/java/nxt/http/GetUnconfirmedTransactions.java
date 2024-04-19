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

import nxt.Nxt;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.blockchain.Transaction;
import nxt.db.DbIterator;
import nxt.db.FilteringIterator;
import nxt.util.Convert;
import nxt.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class GetUnconfirmedTransactions extends APIServlet.APIRequestHandler {

    static final GetUnconfirmedTransactions instance = new GetUnconfirmedTransactions();

    private GetUnconfirmedTransactions() {
        super(new APITag[] {APITag.TRANSACTIONS, APITag.ACCOUNTS}, "account", "account", "account",
                "includeWaitingTransactions", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        boolean includeWaitingTransactions = "true".equalsIgnoreCase(req.getParameter("includeWaitingTransactions"));
        Chain chain = ParameterParser.getChain(req, false);
        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray transactions = new JSONArray();
        if (includeWaitingTransactions) {
            Nxt.getBlockchain().readLock();
        }
        try {
            if (accountIds.isEmpty() && chain == null) {
                try (DbIterator<? extends Transaction> transactionsIterator = Nxt.getTransactionProcessor().getAllUnconfirmedTransactions(firstIndex, lastIndex)) {
                    while (transactionsIterator.hasNext()) {
                        Transaction transaction = transactionsIterator.next();
                        transactions.add(JSONData.unconfirmedTransaction(transaction));
                    }
                }
            } else {
                DbIterator<? extends Transaction> dbIterator = chain == null ? Nxt.getTransactionProcessor().getAllUnconfirmedTransactions(0, -1) :
                        chain == FxtChain.FXT ? Nxt.getTransactionProcessor().getUnconfirmedFxtTransactions() :
                                Nxt.getTransactionProcessor().getUnconfirmedChildTransactions((ChildChain) chain);
                try (FilteringIterator<? extends Transaction> transactionsIterator = new FilteringIterator<>(
                        dbIterator,
                        transaction -> accountIds.isEmpty() || accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId()),
                        firstIndex, lastIndex)) {
                    while (transactionsIterator.hasNext()) {
                        Transaction transaction = transactionsIterator.next();
                        transactions.add(JSONData.unconfirmedTransaction(transaction));
                    }
                }
            }
            response.put("unconfirmedTransactions", transactions);

            if (includeWaitingTransactions) {
                response.put("waitingTransactions", Arrays.stream(Nxt.getTransactionProcessor().getAllWaitingTransactions()).
                        filter(t -> (chain == null || chain == t.getChain())
                                && (accountIds.isEmpty() || accountIds.contains(t.getSenderId()) || accountIds.contains(t.getRecipientId()))).
                        map(JSONData::unconfirmedTransaction).
                        collect(JSON.jsonArrayCollector()));
            }
        } finally {
            if (includeWaitingTransactions) {
                Nxt.getBlockchain().readUnlock();
            }
        }

        return response;
    }

}
