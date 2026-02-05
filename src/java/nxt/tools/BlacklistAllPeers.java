/*
 * Copyright © 2022-2023 Jelurida IP B.V.
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
package nxt.tools;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.callers.BlacklistPeerCall;
import nxt.http.callers.GetPeersCall;
import nxt.util.Logger;

import java.net.MalformedURLException;
import java.net.URL;

public class BlacklistAllPeers {
    public static void main(String[] args) throws MalformedURLException {
        URL url;
        if (args.length > 0) {
            url = new URL(args[0]);
        } else {
            url = new URL("http://localhost:27876/nxt");
        }
        blacklist(url, false);
        blacklist(url, true);
    }

    private static void blacklist(URL url, boolean connected) {
        GetPeersCall call = GetPeersCall.create().remote(url);
        if (connected) {
            call.state("CONNECTED");
        }
        JO allPeersResp = call.callNoError();
        JA peers = allPeersResp.getArray("peers");
        for (int i = 0; i < peers.size(); i++) {
            String peer = peers.getString(i);
            Logger.logDebugMessage("Blacklisting " + peer);
            BlacklistPeerCall.create().remote(url).peer(peer).callNoError();
        }
    }
}
