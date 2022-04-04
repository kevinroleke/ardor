// Auto generated code, do not modify
package nxt.http.callers;

public class SetAssetTradeRoyaltiesCall extends CreateOneSideTransactionCallBuilder<SetAssetTradeRoyaltiesCall> {
    private SetAssetTradeRoyaltiesCall() {
        super(ApiSpec.setAssetTradeRoyalties);
    }

    public static SetAssetTradeRoyaltiesCall create(int chain) {
        return new SetAssetTradeRoyaltiesCall().param("chain", chain);
    }

    public SetAssetTradeRoyaltiesCall royaltiesPercentage(String royaltiesPercentage) {
        return param("royaltiesPercentage", royaltiesPercentage);
    }

    public SetAssetTradeRoyaltiesCall asset(String asset) {
        return param("asset", asset);
    }

    public SetAssetTradeRoyaltiesCall asset(long asset) {
        return unsignedLongParam("asset", asset);
    }
}
