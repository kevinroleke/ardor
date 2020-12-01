// Auto generated code, do not modify
package nxt.http.callers;

public class RetrievePrunedTransactionCall extends ChainSpecificCallBuilder<RetrievePrunedTransactionCall> {
    private RetrievePrunedTransactionCall() {
        super(ApiSpec.retrievePrunedTransaction);
    }

    public static RetrievePrunedTransactionCall create() {
        return new RetrievePrunedTransactionCall();
    }

    public static RetrievePrunedTransactionCall create(int chain) {
        return new RetrievePrunedTransactionCall().param("chain", chain);
    }

    public RetrievePrunedTransactionCall transactionFullHash(String transactionFullHash) {
        return param("transactionFullHash", transactionFullHash);
    }

    public RetrievePrunedTransactionCall transactionFullHash(byte[] transactionFullHash) {
        return param("transactionFullHash", transactionFullHash);
    }
}
