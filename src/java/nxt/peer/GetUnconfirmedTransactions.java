/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
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

package nxt.peer;

import nxt.Nxt;
import nxt.blockchain.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

final class GetUnconfirmedTransactions {

    private GetUnconfirmedTransactions() {}

    /**
     * Process the GetUnconfirmedTransactions message and return the Transactions message.
     * The request contains a list of unconfirmed transactions to exclude.
     *
     * A maximum of 100 unconfirmed transactions will be returned.
     *
     * @param   peer                    Peer
     * @param   request                 Request message
     * @return                          Response message
     */
    static NetworkMessage processRequest(PeerImpl peer, NetworkMessage.GetUnconfirmedTransactionsMessage request) {
        List<Long> exclude = request.getExclusions();
        List<? extends Transaction> transactions = Nxt.getTransactionProcessor().getCachedUnconfirmedTransactions(exclude, 100);
        return new NetworkMessage.TransactionsMessage(request.getMessageId(), transactions);
    }
}
