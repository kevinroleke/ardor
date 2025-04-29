package nxt.blockchain.atomictxs;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Appendix;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.FxtChain;
import nxt.blockchain.Transaction;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class AtomicChildAppendix extends Appendix.AbstractAppendix {

    public static final int appendixType = 128;
    public static final String appendixName = "AtomicChild";
    public static final byte[] MAGIC_HEADER = {'A', 'T'};
    public static final short MAGIC_HEADER_SHORT_LE = 'A' | ('T' << 8);
    private static final int HASH_LENGTH = 32;

    public static final Parser appendixParser = new Parser() {
        @Override
        public AbstractAppendix parse(ByteBuffer buffer) throws NxtException.NotValidException {
            return new AtomicChildAppendix(buffer);
        }

        @Override
        public AbstractAppendix parse(JSONObject attachmentData) {
            if (!Appendix.hasAppendix(appendixName, attachmentData)) {
                return null;
            }
            return new AtomicChildAppendix(attachmentData);
        }
    };

    private final byte[] childFullHash;

    private AtomicChildAppendix(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        short header = buffer.getShort();
        if (MAGIC_HEADER_SHORT_LE != header) {
            throw new NxtException.NotValidException(String.format("Wrong header %x", header));
        }
        this.childFullHash = new byte[HASH_LENGTH];
        buffer.get(this.childFullHash);

    }

    private AtomicChildAppendix(JSONObject attachmentData) {
        super(attachmentData);
        this.childFullHash = Convert.parseHexString((String)attachmentData.get("atomicChildFullHash"));
    }

    public AtomicChildAppendix(byte[] childFullHash) {
        this.childFullHash = childFullHash;
    }

    @Override
    public int getAppendixType() {
        return appendixType;
    }

    @Override
    public boolean isAllowed(Chain chain) {
        return chain instanceof ChildChain;
    }

    @Override
    public String getAppendixName() {
        return appendixName;
    }

    @Override
    protected int getMySize() {
        return 2 + HASH_LENGTH;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.put(MAGIC_HEADER);
        buffer.put(childFullHash);
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("atomicChildFullHash", Convert.toHexString(childFullHash));
    }

    @Override
    public void validate(Transaction transaction) throws NxtException.ValidationException {
        if (Nxt.getBlockchain().getHeight() < Constants.ATOMIC_TRANSACTIONS) {
            throw new NxtException.NotYetEnabledException("Atomic transactions are not yet enabled");
        }
        if (transaction.getChain() == FxtChain.FXT) {
            throw new NxtException.NotValidException("Atomic chains are supported only on child chains");
        }

        ChildTransaction atomicParent = (ChildTransaction) transaction;
        ChildChain childChain = atomicParent.getChain();
        int blockchainHeight = Nxt.getBlockchain().getHeight();
        if (childChain.getTransactionHome().hasTransaction(childFullHash, blockchainHeight)) {
            throw new NxtException.NotCurrentlyValidException("Atomic child transaction already included at an earlier height");
        }
        ChildTransaction atomicChild = atomicParent.getAtomicChild();
        if (atomicChild == null) {
            throw new IllegalStateException(String.format("Missing atomic child transaction %s in db",
                    Convert.toHexString(childFullHash)));
        }
        if (atomicChild.getChain().getId() != childChain.getId()) {
            throwFieldDifferenceException(atomicParent, atomicChild, "chain",
                    childChain, atomicChild.getChain());
        }
        if (atomicChild.getTimestamp() != atomicParent.getTimestamp()) {
            throwFieldDifferenceException(atomicParent, atomicChild, "timestamp",
                    atomicParent.getTimestamp(), atomicChild.getTimestamp());
        }
        if (atomicChild.getDeadline() != atomicParent.getDeadline()) {
            throwFieldDifferenceException(atomicParent, atomicChild, "deadline",
                    atomicParent.getDeadline(), atomicChild.getDeadline());
        }
        if (atomicChild.getBlockId() != atomicParent.getBlockId()) {
            throwFieldDifferenceException(atomicParent, atomicChild, "blockId",
                    atomicParent.getBlockId(), atomicChild.getBlockId());
        }
        AtomicParentAppendix parentAppendix = AtomicParentAppendix.get(atomicChild);
        if (parentAppendix == null) {
            throw new NxtException.NotValidException("Atomic Child transaction " + atomicChild.getStringId()
                    + " is not specifying an atomic parent");
        }
        byte[] unsignedBytes = atomicParent.getUnsignedBytes();
        zeroChildFullHash(unsignedBytes, atomicChild.getFullHash());
        byte[] parentUnsignedHash = Crypto.sha256().digest(unsignedBytes);
        if (!Arrays.equals(parentAppendix.getParentUnsignedHash(), parentUnsignedHash)) {
            throw new NxtException.NotValidException("Hash of atomic parent's unsigned bytes mismatch");
        }
    }

    private static void throwFieldDifferenceException(ChildTransaction atomicParent, ChildTransaction atomicChild,
                                                      String fieldName, Object parentValue, Object childValue)
            throws NxtException.NotValidException {
        throw new NxtException.NotValidException("Atomic transactions with different " + fieldName + " "
                + atomicParent.getStringId() + ":" + parentValue + " != "
                + atomicChild.getStringId() + ":" + childValue);
    }

    public static void zeroChildFullHash(byte[] transactionBytes, byte[] childFullHash) throws NxtException.ValidationException {
        boolean found = false;

        for (int i = 0; i < transactionBytes.length - MAGIC_HEADER.length - HASH_LENGTH; i++) {
            if (transactionBytes[i] == MAGIC_HEADER[0] && transactionBytes[i + 1] == MAGIC_HEADER[1]) {
                // Found a potential header
                boolean isMatch = true;
                for (int j = 0; j < HASH_LENGTH; j++) {
                    if (transactionBytes[i + MAGIC_HEADER.length + j] != childFullHash[j]) {
                        isMatch = false;
                        break;
                    }
                }
                if (isMatch) {
                    if (found) {
                        throw new NxtException.NotValidException("Multiple occurrences of the atomic child fullHash found.");
                    }
                    Arrays.fill(transactionBytes, i + MAGIC_HEADER.length, i + MAGIC_HEADER.length + HASH_LENGTH, (byte) 0);
                    found = true;
                }
            }
        }
        if (!found) {
            throw new NxtException.NotValidException("Atomic child fullHash not found in parent bytes");
        }
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {

    }

    @Override
    public boolean isPhasable() {
        return false;
    }

    public byte[] getChildFullHash() {
        return childFullHash;
    }

    public static AtomicChildAppendix get(Transaction childTransaction) {
        List<? extends Appendix> appendages = childTransaction.getAppendages(a -> a instanceof AtomicChildAppendix, false);
        if (appendages.isEmpty()) {
            return null;
        }
        return (AtomicChildAppendix) appendages.get(0);
    }
}
