// Auto generated code, do not modify
package nxt.http.callers;

public class SendMessageCall extends CreateTwoSidesTransactionCallBuilder<SendMessageCall> {
    private SendMessageCall() {
        super(ApiSpec.sendMessage);
    }

    public static SendMessageCall create(int chain) {
        return new SendMessageCall().param("chain", chain);
    }
}
