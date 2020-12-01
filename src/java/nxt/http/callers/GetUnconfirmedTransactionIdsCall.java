// Auto generated code, do not modify
package nxt.http.callers;

public class GetUnconfirmedTransactionIdsCall extends ChainSpecificCallBuilder<GetUnconfirmedTransactionIdsCall> {
    private GetUnconfirmedTransactionIdsCall() {
        super(ApiSpec.getUnconfirmedTransactionIds);
    }

    public static GetUnconfirmedTransactionIdsCall create() {
        return new GetUnconfirmedTransactionIdsCall();
    }

    public static GetUnconfirmedTransactionIdsCall create(int chain) {
        return new GetUnconfirmedTransactionIdsCall().param("chain", chain);
    }

    public GetUnconfirmedTransactionIdsCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetUnconfirmedTransactionIdsCall firstIndex(int firstIndex) {
        return param("firstIndex", firstIndex);
    }

    public GetUnconfirmedTransactionIdsCall includeWaitingTransactions(
            boolean includeWaitingTransactions) {
        return param("includeWaitingTransactions", includeWaitingTransactions);
    }

    public GetUnconfirmedTransactionIdsCall lastIndex(int lastIndex) {
        return param("lastIndex", lastIndex);
    }

    public GetUnconfirmedTransactionIdsCall account(String... account) {
        return param("account", account);
    }

    public GetUnconfirmedTransactionIdsCall account(long... account) {
        return unsignedLongParam("account", account);
    }

    public GetUnconfirmedTransactionIdsCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }

    public GetUnconfirmedTransactionIdsCall adminPassword(String adminPassword) {
        return param("adminPassword", adminPassword);
    }
}
