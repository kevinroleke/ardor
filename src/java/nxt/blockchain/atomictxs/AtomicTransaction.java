package nxt.blockchain.atomictxs;

import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.Transaction;
import nxt.util.Convert;

import java.util.Arrays;
import java.util.Objects;

class AtomicTransaction<T extends Transaction> {
    private final T transaction;
    private final ChainTransactionId id;
    private final ChainTransactionId childId;

    public AtomicTransaction(T transaction) {
        this.transaction = transaction;
        this.id = ChainTransactionId.getChainTransactionId(transaction);
        this.childId = getChildId(transaction);
    }

    private static <T extends Transaction> ChainTransactionId getChildId(T t) {
        AtomicChildAppendix appendix = AtomicChildAppendix.get(t);
        return appendix != null ?
                new ChainTransactionId(t.getChain().getId(), appendix.getChildFullHash()) : null;
    }

    public T getTransaction() {
        return transaction;
    }

    public ChainTransactionId getId() {
        return id;
    }

    public ChainTransactionId getChildId() {
        return childId;
    }

    public static String shortFullHashToHex(byte[] fullHash) {
        return Convert.toHexString(Arrays.copyOfRange(fullHash, 0, 8));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AtomicTransaction<?> that = (AtomicTransaction<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AtomicTransaction["
                + shortFullHashToHex(id.getFullHash())
                + (childId == null ? "" : ", child=" + shortFullHashToHex(childId.getFullHash()))
                + "]";
    }
}
