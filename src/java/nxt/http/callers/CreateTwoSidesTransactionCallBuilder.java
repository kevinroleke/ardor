// Auto generated code, do not modify
package nxt.http.callers;

public class CreateTwoSidesTransactionCallBuilder<T extends CreateTwoSidesTransactionCallBuilder> extends CreateOneSideTransactionCallBuilder<T> {
    protected CreateTwoSidesTransactionCallBuilder(ApiSpec apiSpec) {
        super(apiSpec);
    }

    public T encryptedMessageData(String encryptedMessageData) {
        return param("encryptedMessageData", encryptedMessageData);
    }

    public T encryptedMessageData(byte[] encryptedMessageData) {
        return param("encryptedMessageData", encryptedMessageData);
    }

    public T encryptedMessageFile(byte[] b) {
        return parts("encryptedMessageFile", b);
    }

    public T encryptedMessageIsPrunable(boolean encryptedMessageIsPrunable) {
        return param("encryptedMessageIsPrunable", encryptedMessageIsPrunable);
    }

    public T compressMessageToEncrypt(String compressMessageToEncrypt) {
        return param("compressMessageToEncrypt", compressMessageToEncrypt);
    }

    public T recipientPublicKey(String recipientPublicKey) {
        return param("recipientPublicKey", recipientPublicKey);
    }

    public T recipientPublicKey(byte[] recipientPublicKey) {
        return param("recipientPublicKey", recipientPublicKey);
    }

    public T recipient(String recipient) {
        return param("recipient", recipient);
    }

    public T recipient(long recipient) {
        return unsignedLongParam("recipient", recipient);
    }

    public T encryptedMessageNonce(String encryptedMessageNonce) {
        return param("encryptedMessageNonce", encryptedMessageNonce);
    }

    public T encryptedMessageNonce(byte[] encryptedMessageNonce) {
        return param("encryptedMessageNonce", encryptedMessageNonce);
    }

    public T messageToEncryptIsText(boolean messageToEncryptIsText) {
        return param("messageToEncryptIsText", messageToEncryptIsText);
    }

    public T messageToEncrypt(String messageToEncrypt) {
        return param("messageToEncrypt", messageToEncrypt);
    }

    public T messageToEncryptFile(byte[] b) {
        return parts("messageToEncryptFile", b);
    }
}
