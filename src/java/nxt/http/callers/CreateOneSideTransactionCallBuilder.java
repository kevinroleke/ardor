// Auto generated code, do not modify
package nxt.http.callers;

public class CreateOneSideTransactionCallBuilder<T extends CreateOneSideTransactionCallBuilder> extends ChainSpecificCallBuilder<T> {
    protected CreateOneSideTransactionCallBuilder(ApiSpec apiSpec) {
        super(apiSpec);
    }

    public T broadcast(boolean broadcast) {
        return param("broadcast", broadcast);
    }

    public T phasingRecipientPropertyName(String phasingRecipientPropertyName) {
        return param("phasingRecipientPropertyName", phasingRecipientPropertyName);
    }

    public T phasingQuorum(long phasingQuorum) {
        return param("phasingQuorum", phasingQuorum);
    }

    public T messageIsPrunable(boolean messageIsPrunable) {
        return param("messageIsPrunable", messageIsPrunable);
    }

    public T voucher(boolean voucher) {
        return param("voucher", voucher);
    }

    public T compressMessageToEncryptToSelf(String compressMessageToEncryptToSelf) {
        return param("compressMessageToEncryptToSelf", compressMessageToEncryptToSelf);
    }

    public T transactionPriority(String transactionPriority) {
        return param("transactionPriority", transactionPriority);
    }

    public T publicKey(String publicKey) {
        return param("publicKey", publicKey);
    }

    public T publicKey(byte[] publicKey) {
        return param("publicKey", publicKey);
    }

    public T phasingExpression(String phasingExpression) {
        return param("phasingExpression", phasingExpression);
    }

    public T phasingSubPolls(String phasingSubPolls) {
        return param("phasingSubPolls", phasingSubPolls);
    }

    public T ecBlockId(String ecBlockId) {
        return param("ecBlockId", ecBlockId);
    }

    public T ecBlockId(long ecBlockId) {
        return unsignedLongParam("ecBlockId", ecBlockId);
    }

    public T phased(boolean phased) {
        return param("phased", phased);
    }

    public T phasingHashedSecret(String phasingHashedSecret) {
        return param("phasingHashedSecret", phasingHashedSecret);
    }

    public T phasingHolding(String phasingHolding) {
        return param("phasingHolding", phasingHolding);
    }

    public T phasingHolding(long phasingHolding) {
        return unsignedLongParam("phasingHolding", phasingHolding);
    }

    public T atomicParentUnsignedHash(String atomicParentUnsignedHash) {
        return param("atomicParentUnsignedHash", atomicParentUnsignedHash);
    }

    public T phasingRecipientPropertySetter(String phasingRecipientPropertySetter) {
        return param("phasingRecipientPropertySetter", phasingRecipientPropertySetter);
    }

    public T atomicChildFullHash(String atomicChildFullHash) {
        return param("atomicChildFullHash", atomicChildFullHash);
    }

    public T atomicChildFullHash(byte[] atomicChildFullHash) {
        return param("atomicChildFullHash", atomicChildFullHash);
    }

    public T messageToEncryptToSelf(String messageToEncryptToSelf) {
        return param("messageToEncryptToSelf", messageToEncryptToSelf);
    }

    public T messageIsText(boolean messageIsText) {
        return param("messageIsText", messageIsText);
    }

    public T phasingMinBalance(long phasingMinBalance) {
        return param("phasingMinBalance", phasingMinBalance);
    }

    public T deadline(int deadline) {
        return param("deadline", deadline);
    }

    public T timestamp(int timestamp) {
        return param("timestamp", timestamp);
    }

    public T phasingWhitelisted(String... phasingWhitelisted) {
        return param("phasingWhitelisted", phasingWhitelisted);
    }

    public T messageToEncryptToSelfIsText(boolean messageToEncryptToSelfIsText) {
        return param("messageToEncryptToSelfIsText", messageToEncryptToSelfIsText);
    }

    public T referencedTransaction(String referencedTransaction) {
        return param("referencedTransaction", referencedTransaction);
    }

    public T feeNQT(long feeNQT) {
        return param("feeNQT", feeNQT);
    }

    public T phasingSenderPropertySetter(String phasingSenderPropertySetter) {
        return param("phasingSenderPropertySetter", phasingSenderPropertySetter);
    }

    public T minBundlerFeeLimitFQT(long minBundlerFeeLimitFQT) {
        return param("minBundlerFeeLimitFQT", minBundlerFeeLimitFQT);
    }

    public T phasingSenderPropertyName(String phasingSenderPropertyName) {
        return param("phasingSenderPropertyName", phasingSenderPropertyName);
    }

    public T message(String message) {
        return param("message", message);
    }

    public T messageFile(byte[] b) {
        return parts("messageFile", b);
    }

    public T encryptToSelfMessageData(String encryptToSelfMessageData) {
        return param("encryptToSelfMessageData", encryptToSelfMessageData);
    }

    public T encryptToSelfMessageData(byte[] encryptToSelfMessageData) {
        return param("encryptToSelfMessageData", encryptToSelfMessageData);
    }

    public T encryptToSelfMessageFile(byte[] b) {
        return parts("encryptToSelfMessageFile", b);
    }

    public T feeRateNQTPerFXT(long feeRateNQTPerFXT) {
        return param("feeRateNQTPerFXT", feeRateNQTPerFXT);
    }

    public T atomicOrphanUnconfirmedPoolDeadline(int atomicOrphanUnconfirmedPoolDeadline) {
        return param("atomicOrphanUnconfirmedPoolDeadline", atomicOrphanUnconfirmedPoolDeadline);
    }

    public T phasingRecipientPropertyValue(String phasingRecipientPropertyValue) {
        return param("phasingRecipientPropertyValue", phasingRecipientPropertyValue);
    }

    public T encryptToSelfMessageNonce(String encryptToSelfMessageNonce) {
        return param("encryptToSelfMessageNonce", encryptToSelfMessageNonce);
    }

    public T encryptToSelfMessageNonce(byte[] encryptToSelfMessageNonce) {
        return param("encryptToSelfMessageNonce", encryptToSelfMessageNonce);
    }

    public T phasingLinkedTransaction(String... phasingLinkedTransaction) {
        return param("phasingLinkedTransaction", phasingLinkedTransaction);
    }

    public T phasingFinishHeight(int phasingFinishHeight) {
        return param("phasingFinishHeight", phasingFinishHeight);
    }

    public T phasingParams(String phasingParams) {
        return param("phasingParams", phasingParams);
    }

    public T phasingHashedSecretAlgorithm(byte phasingHashedSecretAlgorithm) {
        return param("phasingHashedSecretAlgorithm", phasingHashedSecretAlgorithm);
    }

    public T ecBlockHeight(int ecBlockHeight) {
        return param("ecBlockHeight", ecBlockHeight);
    }

    public T minBundlerBalanceFXT(long minBundlerBalanceFXT) {
        return param("minBundlerBalanceFXT", minBundlerBalanceFXT);
    }

    public T phasingMinBalanceModel(byte phasingMinBalanceModel) {
        return param("phasingMinBalanceModel", phasingMinBalanceModel);
    }

    public T phasingVotingModel(byte phasingVotingModel) {
        return param("phasingVotingModel", phasingVotingModel);
    }

    public T phasingSenderPropertyValue(String phasingSenderPropertyValue) {
        return param("phasingSenderPropertyValue", phasingSenderPropertyValue);
    }
}
