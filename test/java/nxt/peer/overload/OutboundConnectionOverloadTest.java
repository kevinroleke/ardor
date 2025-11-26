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

public class OutboundConnectionOverloadTest extends AbstractPeersTest {

    /** Modify the test duration */
    private static final long TEST_DURATION = 10 * 60 * 1000L;
    private static final long FAST_TEST_DURATION = 2 * 60 * 1000L;
    private static final long testDuration = fastTest ? FAST_TEST_DURATION : TEST_DURATION;

    @Rule
    public final Timeout timeoutRule = new Timeout(3 * (int) TEST_DURATION + 60000000);

    private static PeersNetwork net1;
    private static PeersNetwork net2;
    private static PeersNetwork net3;
    private static PeersNetwork net4;

    @BeforeClass
    public static void init() throws InterruptedException, IOException {

        folder.create();
        initPeersTest();
        activateUnblacklisting();

        net1 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_1), PEERS_NETWORK1, PEERS_NETWORK1);
        net2 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_2), PEERS_NETWORK2, PEERS_NETWORK2);
        net3 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_3), PEERS_NETWORK3, PEERS_NETWORK3);
        net4 = new PeersNetwork(folder.newFolder(TEST_DB_FOLDER_4), PEERS_NETWORK4, PEERS_NETWORK4);

        net1.start();
        net2.start();
        net3.start();
        net4.start();
    }

    @After
    public void tearDown() throws InterruptedException {
        peerManager.removeAll(p -> true, true);
        printStatistics();
        Thread.sleep(5000);
    }

    @AfterClass
    public static void shutdown() throws InterruptedException {
        deactivateUnblacklisting();
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
    public void regular1x4OutboundTest() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer1.waitHandshake();
        peer2.waitHandshake();
        peer3.waitHandshake();
        peer4.waitHandshake();

        assertPeers(4, 4, 0);

        prepareStatistics(peer1, peer2, peer3, peer4);

        // Add Overload ConnectoBot and DisconnectoBot
        Overload.ConnectoBot connectoBot = new Overload.ConnectoBot(testDuration, false, true, false, true, false, 10, 10, false);
        Overload.DisconnectoBot disconnectoBot = new Overload.DisconnectoBot(testDuration, false, true, false, 10, 55, false);
        Thread connectorThread = new Thread(connectoBot);
        Thread disconnectorThread = new Thread(disconnectoBot);
        connectorThread.start();
        disconnectorThread.start();

        Thread.sleep(testDuration);
        Thread.sleep(1000L);

        connectoBot.stop();
        disconnectoBot.stop();

        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(10000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, false, true);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

    @Test
    public void fastConnectTest() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer1.waitHandshake();
        peer2.waitHandshake();
        peer3.waitHandshake();
        peer4.waitHandshake();

        assertPeers(4, 4, 0);
        assertTcpConnections(4, 0, 4, 0);

        prepareStatistics(peer1, peer2, peer3, peer4);

        // Add Overload ConnectoBot and DisconnectoBot
        Overload.ConnectoBot connectoBot = new Overload.ConnectoBot(testDuration, false, true, false, true, false, 8, 1, false);
        Overload.DisconnectoBot disconnectoBot = new Overload.DisconnectoBot(testDuration, false, true, false, 4, 12, false);
        Thread connectorThread = new Thread(connectoBot);
        Thread disconnectorThread = new Thread(disconnectoBot);
        connectorThread.start();
        disconnectorThread.start();

        Thread.sleep(testDuration);
        Thread.sleep(5000L);

        connectoBot.stop();
        disconnectoBot.stop();

        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(10000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, false, true);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

    @Test
    public void fastDisconnectTest() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer1.waitHandshake();
        peer2.waitHandshake();
        peer3.waitHandshake();
        peer4.waitHandshake();

        assertPeers(4, 4, 0);
        assertTcpConnections(4, 0, 4, 0);

        prepareStatistics(peer1, peer2, peer3, peer4);

        // Add Overload ConnectoBot and DisconnectoBot
        Overload.ConnectoBot connectoBot = new Overload.ConnectoBot(testDuration, false, true, false, true, false, 4, 12, false);
        Overload.DisconnectoBot disconnectoBot = new Overload.DisconnectoBot(testDuration, false, true, false, 8, 1, false);
        Thread connectorThread = new Thread(connectoBot);
        Thread disconnectorThread = new Thread(disconnectoBot);
        connectorThread.start();
        disconnectorThread.start();

        Thread.sleep(testDuration);
        Thread.sleep(5000L);

        connectoBot.stop();
        disconnectoBot.stop();

        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(15000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, false, true);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

    @Test
    public void chaosTest_superFast() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer1.waitHandshake();
        peer2.waitHandshake();
        peer3.waitHandshake();
        peer4.waitHandshake();

        assertPeers(4, 4, 0);
        assertTcpConnections(4, 0, 4, 0);

        prepareStatistics(peer1, peer2, peer3, peer4);

        // Add Overload ConnectoBot and DisconnectoBot
        Overload.ConnectoBot connectoBot = new Overload.ConnectoBot(testDuration, false, true, false, true, false, 10, 3, false);
        Overload.DisconnectoBot disconnectoBot = new Overload.DisconnectoBot(testDuration, false, true, false, 10, 2, false);
        Thread connectorThread = new Thread(connectoBot);
        Thread disconnectorThread = new Thread(disconnectoBot);
        connectorThread.start();
        disconnectorThread.start();

        Thread.sleep(testDuration);
        Thread.sleep(1000L);

        connectoBot.stop();
        disconnectoBot.stop();

        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(15000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, false, true);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

    @Test
    public void chaosTest_superFast_Inverted() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer1.waitHandshake();
        peer2.waitHandshake();
        peer3.waitHandshake();
        peer4.waitHandshake();

        assertPeers(4, 4, 0);
        assertTcpConnections(4, 0, 4, 0);

        prepareStatistics(peer1, peer2, peer3, peer4);

        // Add Overload ConnectoBot and DisconnectoBot
        Overload.ConnectoBot connectoBot = new Overload.ConnectoBot(testDuration, false, true, false, true, false, 10, 2, false);
        Overload.DisconnectoBot disconnectoBot = new Overload.DisconnectoBot(testDuration, false, true, false, 10, 3, false);
        Thread connectorThread = new Thread(connectoBot);
        Thread disconnectorThread = new Thread(disconnectoBot);
        connectorThread.start();
        disconnectorThread.start();

        Thread.sleep(testDuration);
        Thread.sleep(1000L);

        connectoBot.stop();
        disconnectoBot.stop();

        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(15000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, false, true);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

    @Test
    public void chaosTest_withHandshakeFinished() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.connect(PEERS_NETWORK1);
        Peer peer2 = peerManager.connect(PEERS_NETWORK2);
        Peer peer3 = peerManager.connect(PEERS_NETWORK3);
        Peer peer4 = peerManager.connect(PEERS_NETWORK4);
        peer1.waitHandshake();
        peer2.waitHandshake();
        peer3.waitHandshake();
        peer4.waitHandshake();

        assertPeers(4, 4, 0);
        assertTcpConnections(4, 0, 4, 0);

        prepareStatistics(peer1, peer2, peer3, peer4);

        // Add Overload ConnectoBot and DisconnectoBot
        Overload.ConnectoBot connectoBot = new Overload.ConnectoBot(testDuration, false, true, false, true, false, 10, 3, false);
        Overload.DisconnectoBot disconnectoBot = new Overload.DisconnectoBot(testDuration, false, true, false, 10, 7, false);
        Thread connectorThread = new Thread(connectoBot);
        Thread disconnectorThread = new Thread(disconnectoBot);
        connectorThread.start();
        disconnectorThread.start();

        Thread.sleep(testDuration);
        Thread.sleep(1000L);

        connectoBot.stop();
        disconnectoBot.stop();

        Thread.sleep(Math.max(testDuration / 10, 60000)); // There may be jobs left in the Executor queues, so we wait for all to finish
        //Thread.sleep(15000L);

        Loggy.info("------------------ [END] ------------------");

        printPeers();
        printStatistics(testDuration);

        sanityCheck(4, 0, false, true);
        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

}
