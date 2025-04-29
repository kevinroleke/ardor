/*
 * Copyright © 2025 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.blockchain;

import nxt.blockchain.atomictxs.AtomicChildAppendix;
import nxt.blockchain.atomictxs.AtomicParentAppendix;
import nxt.util.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TestChildTransaction implements DummyChildTransaction {
    private final ChainTransactionId id;
    private final long fee;
    private final short deadline;

    private final List<Appendix> appendices = new ArrayList<>();

    public TestChildTransaction(String shortId, ChildTransaction atomicChild) {
        this(shortId, atomicChild, true, 0, 15);
    }

    public TestChildTransaction(String shortId, ChildTransaction atomicChild,
                                boolean hasParent, long fee, int deadline) {
        String id = "2:" + shortId
                + new String(new char[64 - shortId.length() * 2]).replace('\0',
                '0')
                + shortId;
        this.id = ChainTransactionId.fromStringId(id);
        this.fee = fee;
        if (deadline < Short.MIN_VALUE || deadline > Short.MAX_VALUE) {
            throw new ArithmeticException("Integer overflow: cannot convert to short");
        }
        this.deadline = (short) deadline;
        if (atomicChild != null) {
            appendices.add(new AtomicChildAppendix(atomicChild.getFullHash()));
        }
        if (hasParent) {
            appendices.add(new AtomicParentAppendix(60, new byte[32]));
        }
    }
 
    @Override
    public List<? extends Appendix> getAppendages(Filter<Appendix> filter,
                                                  boolean includeExpiredPrunable) {
        return appendices.stream().filter(filter::ok).collect(
                Collectors.toList());
    }

    @Override
    public ChildChain getChain() {
        return this.id.getChildChain();
    }

    @Override
    public byte[] getFullHash() {
        return this.id.getFullHash();
    }

    public ChainTransactionId getChainTxId() {
        return id;
    }

    @Override
    public long getId() {
        return id.getTransactionId();
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public int getFullSize() {
        return 100;
    }

    @Override
    public long getFee() {
        return fee;
    }

    @Override
    public short getDeadline() {
        return deadline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestChildTransaction that = (TestChildTransaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
