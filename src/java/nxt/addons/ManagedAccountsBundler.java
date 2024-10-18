package nxt.addons;

import nxt.blockchain.Bundler;
import nxt.blockchain.ChildTransaction;
import nxt.crypto.KeyDerivation;
import nxt.crypto.SerializedMasterPublicKey;
import nxt.messaging.MessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.util.Convert;

import java.util.Arrays;

public class ManagedAccountsBundler implements Bundler.Filter {
    private SerializedMasterPublicKey serializedMasterPublicKey;

    @Override
    public boolean ok(Bundler bundler, ChildTransaction childTransaction) {
        int indexHint = getManagedAccountIndexHint(childTransaction);
        if (indexHint < 0) {
            return false;
        }
        KeyDerivation.Bip32Node bip32NodeData = KeyDerivation.deriveChildPublicKey(serializedMasterPublicKey, indexHint);
        return Arrays.equals(bip32NodeData.getPublicKey(), childTransaction.getSenderPublicKey());
    }

    private static int getManagedAccountIndexHint(ChildTransaction childTransaction) {
        String message;
        MessageAppendix messageAppendix = childTransaction.getMessage();
        PrunablePlainMessageAppendix prunablePlainMessageAppendix = childTransaction.getPrunablePlainMessage();
        if (messageAppendix == null && prunablePlainMessageAppendix == null) {
            return -1;
        }
        if (prunablePlainMessageAppendix != null) {
            message = Convert.toString(prunablePlainMessageAppendix.getMessage(), prunablePlainMessageAppendix.isText());
        } else {
            message = Convert.toString(messageAppendix.getMessage(), messageAppendix.isText());
        }
        JO messageJo = JO.parse(message);
        try {
            return messageJo.getInt(AbstractContractContext.MANAGED_ACCOUNTS_INDEX_HINT_FIELD);
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    @Override
    public String getName() {
        return "ManagedAccountsBundler";
    }

    @Override
    public String getDescription() {
        return "Bundles only transactions sent by accounts managed by contracts, utilizing the HD wallet (BIP32) functionality." +
                "The parameter must be the serialized master public key of the managed accounts";
    }

    @Override
    public String getParameter() {
        return Convert.toHexString(serializedMasterPublicKey.getSerializedMasterPublicKey());
    }

    @Override
    public void setParameter(String parameter) {
        serializedMasterPublicKey = new SerializedMasterPublicKey(Convert.parseHexString(parameter));
    }
}
