// Auto generated code, do not modify
package nxt.http.callers;

public class StopStandbyShufflerCall extends ChainSpecificCallBuilder<StopStandbyShufflerCall> {
    private StopStandbyShufflerCall() {
        super(ApiSpec.stopStandbyShuffler);
    }

    public static StopStandbyShufflerCall create() {
        return new StopStandbyShufflerCall();
    }

    public static StopStandbyShufflerCall create(int chain) {
        return new StopStandbyShufflerCall().param("chain", chain);
    }

    public StopStandbyShufflerCall holding(String holding) {
        return param("holding", holding);
    }

    public StopStandbyShufflerCall holding(long holding) {
        return unsignedLongParam("holding", holding);
    }

    public StopStandbyShufflerCall holdingType(byte holdingType) {
        return param("holdingType", holdingType);
    }

    public StopStandbyShufflerCall account(String account) {
        return param("account", account);
    }

    public StopStandbyShufflerCall account(long account) {
        return unsignedLongParam("account", account);
    }

    public StopStandbyShufflerCall adminPassword(String adminPassword) {
        return param("adminPassword", adminPassword);
    }
}
