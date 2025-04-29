// Auto generated code, do not modify
package nxt.http.callers;

public class BundleTransactionsCall extends CreateOneSideTransactionCallBuilder<BundleTransactionsCall> {
    private BundleTransactionsCall() {
        super(ApiSpec.bundleTransactions);
    }

    public static BundleTransactionsCall create(int chain) {
        return new BundleTransactionsCall().param("chain", chain);
    }

    public BundleTransactionsCall enrichmentChildChain(String enrichmentChildChain) {
        return param("enrichmentChildChain", enrichmentChildChain);
    }

    public BundleTransactionsCall enrichmentChildChain(int enrichmentChildChain) {
        return param("enrichmentChildChain", enrichmentChildChain);
    }

    public BundleTransactionsCall isChildrenListEnrichment(boolean isChildrenListEnrichment) {
        return param("isChildrenListEnrichment", isChildrenListEnrichment);
    }

    public BundleTransactionsCall enrichmentBundlingRulesJSON(String enrichmentBundlingRulesJSON) {
        return param("enrichmentBundlingRulesJSON", enrichmentBundlingRulesJSON);
    }

    public BundleTransactionsCall enrichmentTotalFeeLimitFQT(long enrichmentTotalFeeLimitFQT) {
        return param("enrichmentTotalFeeLimitFQT", enrichmentTotalFeeLimitFQT);
    }

    public BundleTransactionsCall transactionFullHash(String... transactionFullHash) {
        return param("transactionFullHash", transactionFullHash);
    }

    public BundleTransactionsCall transactionFullHash(byte[]... transactionFullHash) {
        return param("transactionFullHash", transactionFullHash);
    }
}
