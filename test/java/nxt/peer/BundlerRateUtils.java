/*
 * Copyright © 2024-2025 Jelurida Swiss SA
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

import nxt.Tester;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;

import java.util.Collections;

public class BundlerRateUtils {

    public static void simulateRemoteRate(ChildChain childChain, Tester bundlerAccount, long rate) {
        BundlerRate bundlerRate = new BundlerRate(childChain, rate, 100 * FxtChain.FXT.ONE_COIN, bundlerAccount.getPrivateKey());
        bundlerRate.setBalance(bundlerAccount.getChainBalance(childChain.getId()));
        Peers.updateBundlerRates(new DummyPeer(), null, Collections.singletonList(bundlerRate));
    }

    public static void updateRatesOfStartedBundlers() {
        Peers.updateMyBundlerRates();
    }
}
