package nxt.http;

import nxt.Nxt;
import nxt.NxtException;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.blockchain.Transaction;
import nxt.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetRecentBlockchainTransactions extends APIServlet.APIRequestHandler {

    static final GetRecentBlockchainTransactions instance = new GetRecentBlockchainTransactions();

    private GetRecentBlockchainTransactions() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.TRANSACTIONS}, "account", "timestamp", "type", "subtype",
                "firstIndex", "lastIndex", "numberOfConfirmations", "withMessage", "phasedOnly", "nonPhasedOnly",
                "includeExpiredPrunable", "includePhasingResult", "executedOnly");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Chain chain = ParameterParser.getChain(req);

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactions = new JSONArray();
        if (chain instanceof ChildChain) {
            try (DbIterator<? extends Transaction> iterator =
                    Nxt.getBlockchain().getTransactions((ChildChain)chain, firstIndex, lastIndex)) {
                while (iterator.hasNext()) {
                    Transaction transaction = iterator.next();
                    transactions.add(JSONData.transaction(transaction));
                }
            }
        } else {
            try (DbIterator<? extends Transaction> iterator =
                    Nxt.getBlockchain().getTransactions((FxtChain)chain, firstIndex, lastIndex)) {
                while (iterator.hasNext()) {
                    Transaction transaction = iterator.next();
                    transactions.add(JSONData.transaction(transaction));
                }
            }
        }

        JSONObject response = new JSONObject();
        response.put("transactions", transactions);
        return response;
    }
}
