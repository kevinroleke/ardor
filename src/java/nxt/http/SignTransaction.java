/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.http;

import nxt.NxtException;
import nxt.blockchain.Transaction;
import nxt.blockchain.atomictxs.AtomicChildAppendix;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SignTransaction extends APIServlet.APIRequestHandler {

    static final SignTransaction instance = new SignTransaction();

    private SignTransaction() {
        super(new APITag[] {APITag.TRANSACTIONS}, "unsignedTransactionJSON", "unsignedTransactionBytes",
                "prunableAttachmentJSON", "secretPhrase", "validate", "atomicChildFullHash");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String transactionJSON = Convert.emptyToNull(req.getParameter("unsignedTransactionJSON"));
        String transactionBytes = Convert.emptyToNull(req.getParameter("unsignedTransactionBytes"));
        String prunableAttachmentJSON = Convert.emptyToNull(req.getParameter("prunableAttachmentJSON"));

        Transaction.Builder builder = ParameterParser.parseTransaction(transactionJSON, transactionBytes, prunableAttachmentJSON);

        byte[] privateKey = ParameterParser.getPrivateKey(req, true);
        boolean validate = !"false".equalsIgnoreCase(req.getParameter("validate"));

        JSONObject response = new JSONObject();
        try {
            byte[] atomicChildFullHash = ParameterParser.getBytes(req, "atomicChildFullHash", false);
            if (atomicChildFullHash != Convert.EMPTY_BYTE) {
                builder.appendix(new AtomicChildAppendix(atomicChildFullHash));
            }
            Transaction transaction = builder.build(privateKey);
            JSONObject signedTransactionJSON = JSONData.unconfirmedTransaction(transaction);
            if (validate) {
                transaction.validate();
                response.put("verify", transaction.verifySignature());
            }
            response.put("transactionJSON", signedTransactionJSON);
            response.put("fullHash", signedTransactionJSON.get("fullHash"));
            response.put("signatureHash", signedTransactionJSON.get("signatureHash"));
            response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
            JSONData.putPrunableAttachment(response, transaction);
        } catch (NxtException.ValidationException|RuntimeException e) {
            Logger.logErrorMessage("Incorrect unsigned transaction json or bytes" ,e);
            JSONData.putException(response, e, "Incorrect unsigned transaction json or bytes");
        }
        return response;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected boolean requiredBlockUpdateLock() {
        return true;
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}
