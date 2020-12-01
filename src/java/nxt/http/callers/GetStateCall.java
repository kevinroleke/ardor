// Auto generated code, do not modify
package nxt.http.callers;

public class GetStateCall extends ChainSpecificCallBuilder<GetStateCall> {
    private GetStateCall() {
        super(ApiSpec.getState);
    }

    public static GetStateCall create() {
        return new GetStateCall();
    }

    public static GetStateCall create(int chain) {
        return new GetStateCall().param("chain", chain);
    }

    public GetStateCall includeCounts(boolean includeCounts) {
        return param("includeCounts", includeCounts);
    }

    public GetStateCall adminPassword(String adminPassword) {
        return param("adminPassword", adminPassword);
    }
}
