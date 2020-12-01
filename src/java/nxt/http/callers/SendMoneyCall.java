// Auto generated code, do not modify
package nxt.http.callers;

public class SendMoneyCall extends CreateTwoSidesTransactionCallBuilder<SendMoneyCall> {
    private SendMoneyCall() {
        super(ApiSpec.sendMoney);
    }

    public static SendMoneyCall create(int chain) {
        return new SendMoneyCall().param("chain", chain);
    }

    public SendMoneyCall amountNQT(long amountNQT) {
        return param("amountNQT", amountNQT);
    }
}
