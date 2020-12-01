// Auto generated code, do not modify
package nxt.http.callers;

import nxt.http.APICall;

public class ChainSpecificCallBuilder<T extends ChainSpecificCallBuilder> extends APICall.Builder<T> {
    protected ChainSpecificCallBuilder(ApiSpec apiSpec) {
        super(apiSpec);
    }

    public T chain(String chain) {
        return param("chain", chain);
    }

    public T chain(int chain) {
        return param("chain", chain);
    }
}
