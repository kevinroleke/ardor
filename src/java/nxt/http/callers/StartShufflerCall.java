// Auto generated code, do not modify
package nxt.http.callers;

public class StartShufflerCall extends ChainSpecificCallBuilder<StartShufflerCall> {
    private StartShufflerCall() {
        super(ApiSpec.startShuffler);
    }

    public static StartShufflerCall create() {
        return new StartShufflerCall();
    }

    public static StartShufflerCall create(int chain) {
        return new StartShufflerCall().param("chain", chain);
    }

    public StartShufflerCall recipientSecretPhrase(String recipientSecretPhrase) {
        return param("recipientSecretPhrase", recipientSecretPhrase);
    }

    public StartShufflerCall recipientPublicKey(String recipientPublicKey) {
        return param("recipientPublicKey", recipientPublicKey);
    }

    public StartShufflerCall recipientPublicKey(byte[] recipientPublicKey) {
        return param("recipientPublicKey", recipientPublicKey);
    }

    public StartShufflerCall feeRateNQTPerFXT(long feeRateNQTPerFXT) {
        return param("feeRateNQTPerFXT", feeRateNQTPerFXT);
    }

    public StartShufflerCall shufflingFullHash(String shufflingFullHash) {
        return param("shufflingFullHash", shufflingFullHash);
    }

    public StartShufflerCall shufflingFullHash(byte[] shufflingFullHash) {
        return param("shufflingFullHash", shufflingFullHash);
    }
}
