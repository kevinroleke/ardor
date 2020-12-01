// Auto generated code, do not modify
package nxt.http.callers;

public class ShufflingCreateCall extends CreateOneSideTransactionCallBuilder<ShufflingCreateCall> {
    private ShufflingCreateCall() {
        super(ApiSpec.shufflingCreate);
    }

    public static ShufflingCreateCall create(int chain) {
        return new ShufflingCreateCall().param("chain", chain);
    }

    public ShufflingCreateCall holding(String holding) {
        return param("holding", holding);
    }

    public ShufflingCreateCall holding(long holding) {
        return unsignedLongParam("holding", holding);
    }

    public ShufflingCreateCall amount(long amount) {
        return param("amount", amount);
    }

    public ShufflingCreateCall registrationPeriod(int registrationPeriod) {
        return param("registrationPeriod", registrationPeriod);
    }

    public ShufflingCreateCall participantCount(byte participantCount) {
        return param("participantCount", participantCount);
    }

    public ShufflingCreateCall holdingType(byte holdingType) {
        return param("holdingType", holdingType);
    }
}
