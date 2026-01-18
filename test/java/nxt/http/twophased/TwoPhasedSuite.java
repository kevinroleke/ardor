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

package nxt.http.twophased;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.AbstractHttpApiSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.Iterator;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        TestApproveTransaction.class,
        TestCompositeVoting.class,
        TestCreateTwoPhased.class,
        TestGetAccountPhasedTransactions.class,
        TestGetAssetPhasedTransactions.class,
        TestGetCurrencyPhasedTransactions.class,
        TestGetExecutedTransactions.class,
        TestGetPhasingPoll.class,
        TestGetVoterPhasedTransactions.class,
        TestPropertyVoting.class,
        TestTrustlessAssetSwap.class,
        TestPhasedMessaging.class
})

public class TwoPhasedSuite extends AbstractHttpApiSuite {
    @SuppressWarnings("WhileLoopReplaceableByForEach")
    static boolean searchForTransactionId(JA transactionsJson, String transactionId) {
        boolean found = false;
        Iterator<JO> iterator = transactionsJson.iterator();
        while (iterator.hasNext()) {
            JO transactionObject = iterator.next();
            String iteratedTransactionId = transactionObject.getString("fullHash");
            if (iteratedTransactionId.equals(transactionId)) {
                found = true;
                break;
            }
        }
        return found;
    }
}

