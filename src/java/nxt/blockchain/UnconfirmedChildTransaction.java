/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
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

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.messaging.EncryptToSelfMessageAppendix;
import nxt.messaging.EncryptedMessageAppendix;
import nxt.messaging.MessageAppendix;
import nxt.voting.PhasingAppendix;

import java.sql.ResultSet;
import java.sql.SQLException;

final class UnconfirmedChildTransaction extends UnconfirmedTransaction implements ChildTransaction {

    UnconfirmedChildTransaction(ChildTransactionImpl transaction, long arrivalTimestamp, boolean isBundled) {
        super(transaction, arrivalTimestamp, isBundled);
    }

    UnconfirmedChildTransaction(ResultSet rs) throws SQLException, NxtException.NotValidException {
        super(TransactionImpl.newTransactionBuilder(rs.getBytes("transaction_bytes")), rs);
    }

    @Override
    public ChildTransactionImpl getTransaction() {
        return (ChildTransactionImpl)super.getTransaction();
    }

    @Override
    public ChildChain getChain() {
        return getTransaction().getChain();
    }

    @Override
    public long getFxtTransactionId() {
        return getTransaction().getFxtTransactionId();
    }

    @Override
    public MessageAppendix getMessage() {
        return getTransaction().getMessage();
    }

    @Override
    public EncryptedMessageAppendix getEncryptedMessage() {
        return getTransaction().getEncryptedMessage();
    }

    @Override
    public EncryptToSelfMessageAppendix getEncryptToSelfMessage() {
        return getTransaction().getEncryptToSelfMessage();
    }

    @Override
    public PhasingAppendix getPhasing() {
        return getTransaction().getPhasing();
    }

    @Override
    public ChainTransactionId getReferencedTransactionId() {
        return getTransaction().getReferencedTransactionId();
    }

    @Override
    public ChildTransaction getAtomicChild() {
        return getTransaction().getAtomicChild();
    }

    @Override
    public void validate() throws NxtException.ValidationException {
        super.validate();
        if (Nxt.getBlockchain().getHeight() >= Constants.ATOMIC_TRANSACTIONS) {
            short maxDeadline = ChildTransactionImpl.getMaxDeadline(getTransaction());
            if (getDeadline() > maxDeadline) {
                throw new NxtException.NotValidException("Deadline " +
                        getDeadline() + " exceeds the max deadline of " +
                        maxDeadline + "; fee = " + getFee());
            }
        }
    }
}
