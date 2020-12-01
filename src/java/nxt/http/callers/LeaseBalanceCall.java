// Auto generated code, do not modify
package nxt.http.callers;

public class LeaseBalanceCall extends CreateTwoSidesTransactionCallBuilder<LeaseBalanceCall> {
    private LeaseBalanceCall() {
        super(ApiSpec.leaseBalance);
    }

    public static LeaseBalanceCall create(int chain) {
        return new LeaseBalanceCall().param("chain", chain);
    }

    public LeaseBalanceCall period(int period) {
        return param("period", period);
    }
}
