/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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

package nxt.peer.overload;

import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.peer.helper.AbstractPeersTest;
import nxt.peer.helper.Loggy;
import nxt.peer.helper.Overload;
import nxt.peer.helper.PeersNetwork;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;

public class InboundConnectionOverload4x1Test extends AbstractPeersTest {

    /** Modify the test duration */
    private static final long TEST_DURATION = 15 * 60 * 1000L;
    private static final long FAST_TEST_DURATION = 3 * 60 * 1000L;
    private static final long testDuration = fastTest ? FAST_TEST_DURATION : TEST_DURATION;
    /** Modify to use the Peers workPool */
    private static final boolean useInternalWorkpool = false;
    /** Modify the number of Threads (0 for infinite, otherwise job throttling will be applied) */
    private static final int numThreads = 3;

    private static final int STARTUP_TIME_MS = 5000;

    @Rule
    public final Timeout timeoutRule = new Timeout((3 * (int) TEST_DURATION) + (4 * STARTUP_TIME_MS) + 60000);

    private static PeersNetwork net1;
    private static PeersNetwork net2;
    private static PeersNetwork net3;
    private static PeersNetwork net4;

    @BeforeClass
    public static void init() throws InterruptedException, IOException {

        folder.create();
        initPeersTest();

        Peers.addListener(Peer::unBlacklist, Peers.Event.BLACKLIST);

        net1 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_1), PEERS_NETWORK1, PEERS_NETWORK1);
        net2 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_2), PEERS_NETWORK2, PEERS_NETWORK2);
        net3 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_3), PEERS_NETWORK3, PEERS_NETWORK3);
        net4 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_4), PEERS_NETWORK4, PEERS_NETWORK4);

        net1.addPeers(MAIN_NETWORK);
        net2.addPeers(MAIN_NETWORK);
        net3.addPeers(MAIN_NETWORK);
        net4.addPeers(MAIN_NETWORK);

        /** name, runtime, alwaysOverload, randomOverload, alwaysWait, randomWait, useInternalWorkPool, maxThreads, connectDelay, doInvoke */
        net1.addOverloadTest(Overload.Name.CONNECTOBOT, testDuration, false, true, false, true, useInternalWorkpool, numThreads, 4, false);
        net2.addOverloadTest(Overload.Name.CONNECTOBOT, testDuration, false, true, false, true, useInternalWorkpool, numThreads, 5, false);
        net3.addOverloadTest(Overload.Name.CONNECTOBOT, testDuration, false, true, false, true, useInternalWorkpool, numThreads, 6, false);
        net4.addOverloadTest(Overload.Name.CONNECTOBOT, testDuration, false, true, false, true, useInternalWorkpool, numThreads, 7, false);

        /** name, runtime, alwaysOverload, randomOverload, useInternalWorkPool, maxThreads, disconnectDelay, doInvoke */
        net1.addOverloadTest(Overload.Name.DISCONNECTOBOT, testDuration, false, true, useInternalWorkpool, numThreads, 18, false); // +10
        net2.addOverloadTest(Overload.Name.DISCONNECTOBOT, testDuration, false, true, useInternalWorkpool, numThreads, 19, false);
        net3.addOverloadTest(Overload.Name.DISCONNECTOBOT, testDuration, false, true, useInternalWorkpool, numThreads, 20, false);
        net4.addOverloadTest(Overload.Name.DISCONNECTOBOT, testDuration, false, true, useInternalWorkpool, numThreads, 21, false);

        net1.overloadDelay(4 * STARTUP_TIME_MS);
        net2.overloadDelay(3 * STARTUP_TIME_MS);
        net3.overloadDelay(2 * STARTUP_TIME_MS);
        net4.overloadDelay(STARTUP_TIME_MS);

        net1.start();
        net2.start();
        net3.start();
        net4.start();
    }

    @After
    public void tearDown() {
        peerManager.removeAll(p -> true, true);
        printStatistics();
    }

    @AfterClass
    public static void shutdown() throws InterruptedException {
        net1.exit();
        net2.exit();
        net3.exit();
        net4.exit();
        net1.waitForExit();
        net2.waitForExit();
        net3.waitForExit();
        net4.waitForExit();
        folder.delete();
    }

    @Test
    public void inboundConnectionOverload4x1Test() throws InterruptedException {

        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        peer1.waitHandshake();
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        peer2.waitHandshake();
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        peer3.waitHandshake();
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer4.waitHandshake();

        assertPeers(4, 4, 0);


        prepareStatistics(peer1, peer2, peer3, peer4);

        Thread.sleep(testDuration + (5 * STARTUP_TIME_MS));
        Thread.sleep(1000L);
        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(10000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, true, false);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

}
