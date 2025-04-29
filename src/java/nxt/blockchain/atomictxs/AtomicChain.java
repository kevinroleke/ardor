package nxt.blockchain.atomictxs;

import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.Transaction;
import nxt.blockchain.UnconfirmedTransaction;
import nxt.blockchain.UtxComparableData;
import nxt.util.Convert;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a chain of atomic transactions. The transactions in the chain
 * are ordered from child to parent, with the root being the last transaction in the chain.
 *
 * @param <T> Type of transaction
 */
public class AtomicChain<T extends Transaction> extends AbstractList<T> implements Comparable<AtomicChain<T>> {

    public final static Comparator<AtomicChain<? extends Transaction>> FULL_HASH_COMPARATOR = (c1, c2) -> {
        int sizeComparison = Integer.compare(c1.size(), c2.size());
        if (sizeComparison != 0) {
            return sizeComparison;
        }
        for (int i = 0; i < c1.size(); i++) {
            int hashComparison = Convert.byteArrayComparator.compare(
                    c1.get(i).getFullHash(), c2.get(i).getFullHash());
            if (hashComparison != 0) {
                return hashComparison;
            }
        }
        return 0;
    };

    private final List<AtomicTransaction<T>> transactions;
    private final boolean isComplete;

    private UtxComparableData comparableData;

    private int fullSize = -1;

    private long totalFee = -1;

    AtomicChain(AtomicTransaction<T> start) {
        this(Collections.singletonList(start));
    }

    private AtomicChain(List<AtomicTransaction<T>> transactions) {
        this.transactions = Collections.unmodifiableList(transactions);
        this.isComplete = getLeaf().getChildId() == null
                && AtomicParentAppendix.get(getRoot().getTransaction()) == null;
    }

    @Override
    public T get(int i) {
        return transactions.get(i).getTransaction();
    }

    @Override
    public int size() {
        return transactions.size();
    }

    AtomicChain<T> addNewRoot(AtomicTransaction<T> t) {
        if (getRoot().getId().equals(t.getChildId())) {
            List<AtomicTransaction<T>> newList = new ArrayList<>(transactions);
            newList.add(t);
            return new AtomicChain<>(newList);
        } else {
            throw new IllegalArgumentException("Not a parent of the current root" + t);
        }
    }

    AtomicChain<T>[] splitAtTransaction(ChainTransactionId id) {
        int index = -1;
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            throw new IllegalArgumentException("Transaction not found in the chain");
        }

        AtomicChain<T> firstChain;
        if (index > 0) {
            List<AtomicTransaction<T>> firstPart = new ArrayList<>(
                    transactions.subList(0, index));
            firstChain = new AtomicChain<>(firstPart);
        } else {
            firstChain = null;
        }

        AtomicChain<T> secondChain;
        if (index < transactions.size() - 1) {
            List<AtomicTransaction<T>> secondPart = new ArrayList<>(
                    transactions.subList(index + 1, transactions.size()));
            secondChain = new AtomicChain<>(secondPart);
        } else {
            secondChain = null;
        }

        return new AtomicChain[]{firstChain, secondChain};
    }

    AtomicChain<T> appendChildren(AtomicChain<T> chain) {
        if (chain.getRoot().getId().equals(getLeaf().getChildId())) {
            List<AtomicTransaction<T>> newList = new ArrayList<>(
                    transactions.size() + chain.transactions.size());
            newList.addAll(chain.transactions);
            newList.addAll(transactions);
            return new AtomicChain<>(newList);
        } else {
            throw new IllegalArgumentException("Not the next child " + chain);
        }
    }

    public boolean isComplete() {
        return isComplete;
    }

    public T getRootTransaction() {
        return getRoot().getTransaction();
    }

    AtomicTransaction<T> getRoot() {
        return transactions.get(transactions.size() - 1);
    }

    AtomicTransaction<T> getLeaf() {
        return transactions.get(0);
    }

    List<AtomicTransaction<T>> getAtomicTransactions() {
        return transactions;
    }

    public UtxComparableData getComparableData() {
        if (comparableData == null) {
            boolean isBundled;
            long arrivalTimestamp;
            T rootTransaction = getRootTransaction();
            if (rootTransaction instanceof UnconfirmedTransaction) {
                UnconfirmedTransaction utx = (UnconfirmedTransaction) rootTransaction;
                isBundled = utx.isBundled();
                arrivalTimestamp = utx.getArrivalTimestamp();
            } else {
                isBundled = false;
                arrivalTimestamp = Convert.fromEpochTime(rootTransaction.getTimestamp());
            }
            comparableData = new UtxComparableData(rootTransaction, isBundled, isComplete(),
                    arrivalTimestamp, getTotalFee(), getFullSize());
        }
        return comparableData;
    }

    public int getFullSize() {
        if (fullSize < 0) {
            fullSize = transactions.stream().map(AtomicTransaction::getTransaction)
                    .mapToInt(Transaction::getFullSize).sum();
        }
        return fullSize;
    }

    public long getTotalFee() {
        if (totalFee < 0) {
            totalFee = transactions.stream().map(AtomicTransaction::getTransaction)
                    .mapToLong(Transaction::getFee).reduce(0, Math::addExact);
        }
        return totalFee;
    }

    public long getFeePerByte() {
        return getTotalFee() / getFullSize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AtomicChain<?> that = (AtomicChain<?>) o;
        return Objects.equals(transactions, that.transactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactions);
    }

    @Override
    public int compareTo(AtomicChain<T> other) {
        return FULL_HASH_COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return "AtomicChain["
                + transactions.stream().map(AtomicTransaction::getTransaction)
                    .map(transaction -> AtomicTransaction.shortFullHashToHex(transaction.getFullHash()))
                .collect(Collectors.joining(", "))
                + ']';
    }

    @SafeVarargs
    public static <T extends Transaction> AtomicChain<T> of(T... elements) {
        AtomicChain<T> chain = new AtomicChain<>(
                Arrays.stream(elements).map(AtomicTransaction::new).collect(
                        Collectors.toList()));
        ChainTransactionId childId = null;
        for (AtomicTransaction<T> at: chain.transactions) {
            if (childId != null) {
                if (!childId.equals(at.getChildId())) {
                    throw new IllegalArgumentException("Transaction " + at + " does not specify child id " + childId);
                }
            }
            childId = at.getId();
        }
        return chain;
    }
}
