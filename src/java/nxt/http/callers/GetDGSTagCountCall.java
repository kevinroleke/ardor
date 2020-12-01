// Auto generated code, do not modify
package nxt.http.callers;

public class GetDGSTagCountCall extends ChainSpecificCallBuilder<GetDGSTagCountCall> {
    private GetDGSTagCountCall() {
        super(ApiSpec.getDGSTagCount);
    }

    public static GetDGSTagCountCall create() {
        return new GetDGSTagCountCall();
    }

    public static GetDGSTagCountCall create(int chain) {
        return new GetDGSTagCountCall().param("chain", chain);
    }

    public GetDGSTagCountCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetDGSTagCountCall inStockOnly(String inStockOnly) {
        return param("inStockOnly", inStockOnly);
    }

    public GetDGSTagCountCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }
}
