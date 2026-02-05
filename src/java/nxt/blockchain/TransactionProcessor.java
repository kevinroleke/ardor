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

import nxt.NxtException;
import nxt.db.DbIterator;
import nxt.util.Observable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Stream;

public interface TransactionProcessor extends Observable<List<? extends Transaction>,TransactionProcessor.Event> {

    enum Event {
        REMOVED_UNCONFIRMED_TRANSACTIONS,
        ADDED_UNCONFIRMED_TRANSACTIONS,
        ADDED_CONFIRMED_TRANSACTIONS,
        RELEASE_PHASED_TRANSACTION,
        REJECT_PHASED_TRANSACTION
    }

    List<Long> getAllUnconfirmedTransactionIds();
    
    DbIterator<? extends Transaction> getAllUnconfirmedTransactions();

    DbIterator<? extends Transaction> getAllUnconfirmedTransactions(int from, int to);

    DbIterator<? extends Transaction> getAllUnconfirmedTransactions(String sort);

    DbIterator<? extends Transaction> getAllUnconfirmedTransactions(int from, int to, String sort);

    DbIterator<? extends Transaction> getUnconfirmedFxtTransactions();

    DbIterator<? extends Transaction> getUnconfirmedChildTransactions(ChildChain chain);

    UnconfirmedTransaction getUnconfirmedTransaction(long transactionId);

    UnconfirmedTransaction[] getAllWaitingTransactions();

    Transaction[] getAllBroadcastedTransactions();

    void clearUnconfirmedTransactions();

    void requeueAllUnconfirmedTransactions();

    void rebroadcastAllUnconfirmedTransactions();

    void broadcast(Transaction transaction) throws NxtException.ValidationException;

    void broadcastLater(Transaction transaction);

    List<ChainTransactionId> filterPeerRequestIds(Stream<ChainTransactionId> requestIdsStream, int maxSize);

    List<Transaction> filterPeerTransactions(List<Transaction> transactions);

    List<? extends Transaction> processPeerTransactions(List<Transaction> transactions) throws NxtException.NotValidException;

    void processLater(Collection<? extends FxtTransaction> transactions);

    List<? extends Transaction> getCachedUnconfirmedTransactions(List<Long> exclude, int maxCount);

    List<Transaction> restorePrunableData(List<Transaction> transactions);

    Map<String, Object> getUnconfirmedPoolInfo();
}
