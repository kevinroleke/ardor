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
import nxt.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.List;

public class AtomicParentAppendix extends Appendix.AbstractAppendix {

    public static final int appendixType = 256;
    public static final String appendixName = "AtomicParent";

    public static final Parser appendixParser = new Parser() {
        @Override
        public AbstractAppendix parse(ByteBuffer buffer) {
            return new AtomicParentAppendix(buffer);
        }

        @Override
        public AbstractAppendix parse(JSONObject attachmentData) {
            if (!Appendix.hasAppendix(appendixName, attachmentData)) {
                return null;
            }
            return new AtomicParentAppendix(attachmentData);
        }
    };

    private final byte[] parentUnsignedHash;

    /**
     * Time in seconds to keep the transaction in the unconfirmed pool in case its parent is not available.
     * The transaction remains valid after this period expires and may be included in a block by a forger who has the
     * parent in his unconfirmed pool.
     * This additional deadline protects the sender of the atomic child from having their unconfirmed funds locked until
     * the actual deadline (which may need to be set far in the future) in case the creator of the atomic parent
     * adversarially does not submit the parent.
     */
    private final int orphanUnconfirmedPoolDeadline;

    private AtomicParentAppendix(ByteBuffer buffer) {
        super(buffer);
        orphanUnconfirmedPoolDeadline = Short.toUnsignedInt(buffer.getShort());
        this.parentUnsignedHash = new byte[32];
        buffer.get(this.parentUnsignedHash);
    }

    private AtomicParentAppendix(JSONObject attachmentData) {
        super(attachmentData);
        this.orphanUnconfirmedPoolDeadline = ((Long) attachmentData.get("orphanUnconfirmedPoolDeadline")).intValue();
        this.parentUnsignedHash = Convert.parseHexString((String)attachmentData.get("atomicParentUnsignedHash"));
    }

    public AtomicParentAppendix(int orphanUnconfirmedPoolDeadline, byte[] parentUnsignedHash) {
        this.orphanUnconfirmedPoolDeadline = orphanUnconfirmedPoolDeadline;
        this.parentUnsignedHash = parentUnsignedHash;
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
        return 2 + 32;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putShort((short) orphanUnconfirmedPoolDeadline);
        buffer.put(parentUnsignedHash);
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("atomicParentUnsignedHash", Convert.toHexString(parentUnsignedHash));
    }

    @Override
    public void validate(Transaction transaction) throws NxtException.ValidationException {
        if (Nxt.getBlockchain().getHeight() < Constants.ATOMIC_TRANSACTIONS) {
            throw new NxtException.NotYetEnabledException("Atomic transactions are not yet enabled");
        }
        if (transaction.getChain() == FxtChain.FXT) {
            throw new NxtException.NotValidException("Atomic chains are supported only on child chains");
        }
        //The reference from atomic child to atomic parent is validated when the parent is available - in
        // AtomicChildAppendix.validate. Additionally, to prevent the existence of atomic child without parent,
        // a ChildBlock transaction is not valid if an atomic child does not have an atomic parent in the same block.
        // As a result, an atomic child can be accepted in the unconfirmed pool without atomic parent but cannot be
        // bundled into a ChildBlock without its parent

        ChildTransaction childTransaction = (ChildTransaction) transaction;
        if (childTransaction.getReferencedTransactionId() != null) {
            throw new NxtException.NotValidException("Only the root of an atomic chain can use the referenced transaction feature");
        }
    }

    @Override
    public void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {

    }

    @Override
    public boolean isPhasable() {
        return false;
    }

    public int getOrphanUnconfirmedPoolDeadline() {
        return orphanUnconfirmedPoolDeadline;
    }

    public byte[] getParentUnsignedHash() {
        return parentUnsignedHash;
    }

    public static AtomicParentAppendix get(Transaction childTransaction) {
        List<? extends Appendix> appendages = childTransaction.getAppendages(a -> a instanceof AtomicParentAppendix, false);
        if (appendages.isEmpty()) {
            return null;
        }
        return (AtomicParentAppendix) appendages.get(0);
    }
}
