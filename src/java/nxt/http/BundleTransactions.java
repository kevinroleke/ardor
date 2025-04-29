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
import nxt.blockchain.Bundler;
import nxt.blockchain.ChildBlockAttachment;
import nxt.blockchain.ChildBlockFxtTransactionType;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.Transaction;
import nxt.blockchain.UnconfirmedTransaction;
import nxt.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Transaction created by this call must expire before any transaction bundled.
 * For this reason if deadline not set it is calculated based on transactions bundled.
 * But not greater than configured by 'nxt.defaultChildBlockDeadline' property.
 * <p>
 * When <code>isChildrenListEnrichment</code> is <code>true</code>, the list of child
 * transactions is supplemented with currently circulating unconfirmed transactions that
 * meet the bundling rules specified in <code>enrichmentBundlingRulesJSON</code>.
 * Additionally:
 * <ul>
 *     <li>
 *         If no <code>transactionFullHash</code>-es are provided, the list includes
 *         only circulating transactions, effectively performing a single iteration
 *         of the bundler procedure. In this case, the <code>enrichmentChildChain</code>
 *         parameter should be provided.
 *     </li>
 *     <li>
 *         If the <code>feeNQT</code> parameter is not provided, the fee for the ChildBlock
 *         transaction created by this call is calculated internally, with a limit
 *         specified in <code>enrichmentTotalFeeLimitFQT</code>. The minimum fee applies
 *         to the provided <code>transactionFullHash</code>-es, while other transactions
 *         pay a fee according to the bundling rule that accepts them.
 *     </li>
 *     <li>
 *         The <code>enrichmentBundlingRulesJSON</code> is a JSON-formatted string
 *         containing an array of bundling rule objects. Each object must include
 *         the required field <code>minRateNQTPerFXT</code>. An optional <code>filter</code>
 *         field can also be provided to exclude specific transactions. The other
 *         two fields, <code>overpayFQTPerFXT</code> and <code>feeCalculatorName</code>,
 *         are used either to calculate the actual fee paid by the ChildBlock transaction
 *         (if <code>feeNQT</code> is provided) or to filter out transactions that would
 *         exceed the <code>feeNQT</code>.
 *     </li>
 * </ul>
 */

public final class BundleTransactions extends CreateTransaction {
    private static final int defaultChildBlockDeadline = Nxt.getIntProperty("nxt.defaultChildBlockDeadline");

    static final BundleTransactions instance = new BundleTransactions();

    private BundleTransactions() {
        super(ChildBlockFxtTransactionType.INSTANCE, new APITag[]{APITag.FORGING, APITag.CREATE_TRANSACTION},
                "transactionFullHash", "transactionFullHash", "transactionFullHash",
                "isChildrenListEnrichment", "enrichmentChildChain", "enrichmentTotalFeeLimitFQT", "enrichmentBundlingRulesJSON");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Account account = ParameterParser.getSenderAccount(req);
        List<ChildTransaction> childTransactions = new ArrayList<>();
        String[] transactionFullHashesValues = req.getParameterValues("transactionFullHash");
        boolean enrichChildrenList = "true".equalsIgnoreCase(req.getParameter("isChildrenListEnrichment"));
        ChildChain enrichmentChildChain = enrichChildrenList ?
                ParameterParser.getChildChain(req, "enrichmentChildChain", false) : null;
        if (transactionFullHashesValues == null || transactionFullHashesValues.length == 0) {
            if (enrichmentChildChain == null) {
                return JSONResponses.missing("transactionFullHash");
            } else {
                transactionFullHashesValues = new String[]{};
            }
        }
        final int now = Nxt.getEpochTime();
        for (String s : transactionFullHashesValues) {
            byte[] hash = Convert.parseHexString(s);
            UnconfirmedTransaction unconfirmedTransaction = Nxt.getTransactionProcessor().getUnconfirmedTransaction(Convert.fullHashToId(hash));
            if (unconfirmedTransaction == null || !Arrays.equals(hash, unconfirmedTransaction.getFullHash())) {
                return JSONResponses.UNKNOWN_TRANSACTION_FULL_HASH;
            }
            if (!(unconfirmedTransaction.getTransaction() instanceof ChildTransaction)) {
                return JSONResponses.INCORRECT_TRANSACTION;
            }
            ChildTransaction childTransaction = (ChildTransaction) unconfirmedTransaction.getTransaction();
            if (enrichmentChildChain == null) {
                enrichmentChildChain = childTransaction.getChain();
            } else {
                if (!enrichmentChildChain.equals(childTransaction.getChain())) {
                    return JSONResponses.error("Chain of transaction " + s + " differs from the enrichmentChildChain");
                }
            }
            if (unconfirmedTransaction.getExpiration() <= now) {
                continue;
            }
            childTransactions.add(childTransaction);
        }
        short deadline = getDeadline(req, childTransactions);
        int timestamp = ParameterParser.getTimestamp(req);
        if (timestamp <= 0) {
            timestamp = Nxt.getEpochTime();
        }
        long feeNQT = ParameterParser.getLong(req, "feeNQT", -1L, Constants.MAX_BALANCE_NQT, -1);
        if (enrichChildrenList) {
            if (enrichmentChildChain == null) {
                return JSONResponses.missing("transactionFullHash", "enrichmentChildChain");
            }
            JSONArray enrichmentRulesJson = ParameterParser.getJsonArray(req, "enrichmentBundlingRulesJSON");
            List<Bundler.Rule> enrichmentRules;
            if (enrichmentRulesJson != null) {
                enrichmentRules = ParameterParser.parseBundlingRulesJson(enrichmentRulesJson);
            } else {
                return JSONResponses.error("Please set the enrichmentBundlingRulesJSON " +
                        "parameter with at least one rule e.g. [{\"minRateNQTPerFXT\":<rate>}]");
            }
            long totalFeesLimitFQT = feeNQT;
            if (totalFeesLimitFQT < 0) {
                totalFeesLimitFQT = ParameterParser.getLong(req, "enrichmentTotalFeeLimitFQT",
                        -1L, Constants.MAX_BALANCE_NQT, true);
            }
            long minFeeFQT = Bundler.enrichChildBlockList(childTransactions,
                    enrichmentChildChain, timestamp, deadline, totalFeesLimitFQT, enrichmentRules);
            if (feeNQT < 0) {
                feeNQT = minFeeFQT;
            }
        }
        ChildBlockAttachment attachment = new ChildBlockAttachment(childTransactions);
        return transactionParameters(req, account, attachment)
                .setFeeNQT(feeNQT)
                .setTimestamp(timestamp)
                .setDeadline(deadline)
                .createTransaction();
    }

    /**
     * Calculating minimal deadline for bundling transaction.
     * <ol>
     *  <li>'deadline' request parameter is used if set. </li>
     *  <li>Minimal expiration time of included transactions is used to calculate deadline</li>
     *  <li>But not greater then value configured by nxt property 'nxt.defaultChildBlockDeadline'</li>
     * </ol>
     * Since deadline must result in expiration time before expiration time of any included transaction, we calculate deadline from expiration time and current Epoch Time.
     * Original formula for calculating transaction expiration time is <code>timestamp + deadline * 60</code> see {@link nxt.blockchain.TransactionImpl#getExpiration()}.
     */
    private short getDeadline(HttpServletRequest req, List<ChildTransaction> childTransactions) throws ParameterException {
        int requestedDeadline = ParameterParser.getInt(req, "deadline", 1, Short.MAX_VALUE, false);
        if (requestedDeadline != 0) {
            return (short) requestedDeadline;
        }
        final int timestamp = getTimestamp(req);
        int calculatedDeadline = childTransactions.stream()
                .mapToInt(Transaction::getExpiration)
                .map(expiration -> (expiration - timestamp) / 60)
                .min()
                .orElse(defaultChildBlockDeadline);
        return (short) Math.min(calculatedDeadline, defaultChildBlockDeadline);
    }

    private int getTimestamp(HttpServletRequest req) throws ParameterException {
        int timestamp = ParameterParser.getTimestamp(req);
        if (timestamp != 0) {
            return timestamp;
        }
        return Nxt.getEpochTime() + 1;
    }

}
