// Auto generated code, do not modify
package nxt.http.callers;

public class GetAccountPhasedTransactionCountCall extends ChainSpecificCallBuilder<GetAccountPhasedTransactionCountCall> {
    private GetAccountPhasedTransactionCountCall() {
        super(ApiSpec.getAccountPhasedTransactionCount);
    }

    public static GetAccountPhasedTransactionCountCall create() {
        return new GetAccountPhasedTransactionCountCall();
    }

    public static GetAccountPhasedTransactionCountCall create(int chain) {
        return new GetAccountPhasedTransactionCountCall().param("chain", chain);
    }

    public GetAccountPhasedTransactionCountCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetAccountPhasedTransactionCountCall account(String account) {
        return param("account", account);
    }

    public GetAccountPhasedTransactionCountCall account(long account) {
        return unsignedLongParam("account", account);
    }

    public GetAccountPhasedTransactionCountCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }
}
