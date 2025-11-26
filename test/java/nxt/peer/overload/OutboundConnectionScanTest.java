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
import nxt.peer.helper.PeersNetwork;
import nxt.util.QueuedThreadPool;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OutboundConnectionScanTest extends AbstractPeersTest {

    /** Modify the number of steps to scan. Each step increases the connect/disconnect 'waitTime' (see test for details) */
    private static final int NUM_ITERATIONS = 10;

    @Rule
    public final Timeout timeoutRule = new Timeout((NUM_ITERATIONS + 1) * 60 * 1000);

    private static PeersNetwork net1;
    private static PeersNetwork net2;
    private static PeersNetwork net3;
    private static PeersNetwork net4;

    private QueuedThreadPool workPool = new QueuedThreadPool(1, 15);

    @BeforeClass
    public static void init() throws InterruptedException, IOException {

        folder.create();
        initPeersTest();
        activateUnblacklisting();

        peerManager.addListener(Peer::unBlacklist, Peers.Event.BLACKLIST);

        net1 = new PeersNetwork( folder.newFolder(TEST_DB_FOLDER_1), PEERS_NETWORK1, PEERS_NETWORK1);
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
    public void connectAndDisconnectImmediately_InSteps() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.addPeer(PEERS_NETWORK1);
        Peer peer2 = peerManager.addPeer(PEERS_NETWORK2);
        Peer peer3 = peerManager.addPeer(PEERS_NETWORK3);
        Peer peer4 = peerManager.addPeer(PEERS_NETWORK4);

        assertPeers(4, 0, 0);
        assertTcpConnections(0, 0, 0, 0);

        // Statistics per iteration
        Map<Integer, Integer> connectsPerIter = new HashMap<>(NUM_ITERATIONS);
        Map<Integer, Integer> disconnectsPerIter = new HashMap<>(NUM_ITERATIONS);

        List<Peer> peers = Arrays.asList(peer1, peer2, peer3, peer4);
        List<Callable<Object>> jobs;

        for (int i = 0; i < NUM_ITERATIONS + 1; i++) {
            Integer id = i;
            int waitTime = i;
            connectsPerIter.put(id, 0);
            disconnectsPerIter.put(id, 0);
            resetStatistics();
            prepareStatistics(peer1, peer2, peer3, peer4);
            jobs = peers.stream()
                    .map(p -> (Runnable) () -> {
                        peerManager.connect(p);
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        p.disconnectPeer();
                    })
                    .map(Executors::callable)
                    .collect(Collectors.toList());
            Loggy.info(">>> STEP {}", i);
            for (int j = 0; j < 5; j++) {
                workPool.invokeAll(jobs);
                Thread.sleep(waitTime);
            }
            Loggy.info(">>> WAIT");
            Thread.sleep(3500L); // the other side needs to timeout incomplete handshakes
            connectsPerIter.put(id, numConnects.values().stream().mapToInt(Integer::intValue).sum());
            disconnectsPerIter.put(id, numDisconnects.values().stream().mapToInt(Integer::intValue).sum());
            printPeers();
            printStatistics();
            sanityCheck(4, 0, false, true);
        }

        Loggy.info("[RESULT] connectAndDisconnectImmediately_InSteps:");
        Loggy.info("Connects / Disconnects per Step");
        IntStream.range(0, NUM_ITERATIONS + 1).boxed().forEach(i ->
                Loggy.info("----- Step {}: {} connects, {} disconnects", i,
                        connectsPerIter.getOrDefault(i, 0), disconnectsPerIter.getOrDefault(i, 0)));

        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

    @Test
    public void connectAndDisconnectImmediatelyWithWaitReady_InSteps() throws InterruptedException {

        // Connect to all 4 Peers
        Peer peer1 = peerManager.addPeer(PEERS_NETWORK1);
        Peer peer2 = peerManager.addPeer(PEERS_NETWORK2);
        Peer peer3 = peerManager.addPeer(PEERS_NETWORK3);
        Peer peer4 = peerManager.addPeer(PEERS_NETWORK4);

        assertPeers(4, 0, 0);
        assertTcpConnections(0, 0, 0, 0);

        // Statistics per iteration
        Map<Integer, Integer> connectsPerIter = new HashMap<>(NUM_ITERATIONS);
        Map<Integer, Integer> disconnectsPerIter = new HashMap<>(NUM_ITERATIONS);

        List<Peer> peers = Arrays.asList(peer1, peer2, peer3, peer4);
        List<Callable<Object>> jobs;

        for (int i = 0; i < NUM_ITERATIONS + 1; i++) {
            Integer id = i;
            int waitTime = i;
            connectsPerIter.put(id, 0);
            disconnectsPerIter.put(id, 0);
            resetStatistics();
            prepareStatistics(peer1, peer2, peer3, peer4);
            jobs = peers.stream()
                    .map(p -> (Runnable) () -> {
                        peerManager.connect(p).waitHandshake();
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        p.disconnectPeer();
                    })
                    .map(Executors::callable)
                    .collect(Collectors.toList());
            Loggy.info(">>> STEP {}", i);
            for (int j = 0; j < 5; j++) {
                workPool.invokeAll(jobs);
                Thread.sleep(waitTime);
            }
            Loggy.info(">>> WAIT");
            Thread.sleep(3500L); // the other side needs to timeout incomplete handshakes
            connectsPerIter.put(id, numConnects.values().stream().mapToInt(Integer::intValue).sum());
            disconnectsPerIter.put(id, numDisconnects.values().stream().mapToInt(Integer::intValue).sum());
            printPeers();
            printStatistics();
            sanityCheck(4, 0, false, true);
        }

        Loggy.info("[RESULT] connectAndDisconnectImmediatelyWithWaitReady_InSteps:");
        Loggy.info("Connects / Disconnects per Step");
        IntStream.range(0, NUM_ITERATIONS + 1).boxed().forEach(i ->
                Loggy.info("----- Step {}: {} connects, {} disconnects", i,
                        connectsPerIter.getOrDefault(i, 0), disconnectsPerIter.getOrDefault(i, 0)));

        healthCheckConnection(peer1, peer2, peer3, peer4);
        healthCheckReconnectAllOutbound(peer1, peer2, peer3, peer4);
        healthCheckPingPong(peer1, peer2, peer3, peer4);
    }

}
