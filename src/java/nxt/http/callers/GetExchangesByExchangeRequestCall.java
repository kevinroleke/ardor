// Auto generated code, do not modify
package nxt.http.callers;

public class GetExchangesByExchangeRequestCall extends ChainSpecificCallBuilder<GetExchangesByExchangeRequestCall> {
    private GetExchangesByExchangeRequestCall() {
        super(ApiSpec.getExchangesByExchangeRequest);
    }

    public static GetExchangesByExchangeRequestCall create() {
        return new GetExchangesByExchangeRequestCall();
    }

    public static GetExchangesByExchangeRequestCall create(int chain) {
        return new GetExchangesByExchangeRequestCall().param("chain", chain);
    }

    public GetExchangesByExchangeRequestCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetExchangesByExchangeRequestCall includeCurrencyInfo(boolean includeCurrencyInfo) {
        return param("includeCurrencyInfo", includeCurrencyInfo);
    }

    public GetExchangesByExchangeRequestCall transaction(String transaction) {
        return param("transaction", transaction);
    }

    public GetExchangesByExchangeRequestCall transaction(long transaction) {
        return unsignedLongParam("transaction", transaction);
    }

    public GetExchangesByExchangeRequestCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }
}
