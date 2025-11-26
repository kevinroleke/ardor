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

package nxt.peer.helper;

import nxt.Nxt;
import nxt.configuration.Setup;
import nxt.peer.NetworkMessage;
import nxt.peer.Peer;
import nxt.peer.Peers;
import nxt.util.Listener;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public abstract class AbstractPeersTest {

    /** Use default P2P DB locations */
    public static final String testDbDir = PeersNetwork.TEST_DB_LOCATION + "/nxt";
    public static final String testStateDbDir = PeersNetwork.TEST_DB_LOCATION + "/state";
    public static TemporaryFolder folder = new TemporaryFolder();

    /** Modify the log level */
    private static final int LOG_LEVEL = 1;
    /** Override default properties, or add additional properties */
    private static Properties additionalProperties; // not needed for TestSuite

    private static Process torProcess;
    protected static String mainHost;
    protected static String testHost1;
    protected static String testHost2;
    protected static String testHost3;
    protected static int mainPort;
    protected static int testPort1;
    protected static int testPort2;
    protected static int testPort3;

    protected static final String MAIN_NETWORK = "127.0.0.1";
    protected static final String PEERS_NETWORK1 = "127.0.0.2";
    protected static final String PEERS_NETWORK2 = "127.0.0.3";
    protected static final String PEERS_NETWORK3 = "127.0.0.4";
    protected static final String PEERS_NETWORK4 = "127.0.0.5";
    protected static final String PEERS_NETWORK5 = "127.0.0.6";
    protected static final String PEERS_NETWORK6 = "127.0.0.7";
    protected static final String TEST_DB_FOLDER_1 = "127002";
    protected static final String TEST_DB_FOLDER_2 = "127003";
    protected static final String TEST_DB_FOLDER_3 = "127004";
    protected static final String TEST_DB_FOLDER_4 = "127005";
    protected static final String TEST_DB_FOLDER_5 = "127006";
    protected static final String TEST_DB_FOLDER_6 = "127007";

    protected static PeerManager peerManager;

    private static final String HEALTH_CHECK_MESSAGE = "I am OK";

    /** Individual test statistics */
    private boolean useStatistics = false;
    private Listener<Peer> blacklistListener = null;
    private Listener<Peer> unblacklistListener = null;
    private Listener<Peer> addPeerListener = null;
    private Listener<Peer> changeAddressListener = null;
    private Listener<Peer> changeServicesListener = null;
    private Listener<Peer> connectListener = null;
    private Listener<Peer> changeListener = null;
    private Listener<Peer> removePeerListener = null;
    private Map<Peer, Integer> numBlacklists = new HashMap<>();
    private Map<Peer, Integer> numUnblacklists = new HashMap<>();
    private Map<Peer, Integer> numAddPeers = new HashMap<>();
    private Map<Peer, Integer> numChangeAddresses = new HashMap<>();
    private Map<Peer, Integer> numChangeServices = new HashMap<>();
    protected Map<Peer, Integer> numConnects = new HashMap<>();
    protected Map<Peer, Integer> numDisconnects = new HashMap<>();
    private Map<Peer, Integer> numRemoves = new HashMap<>();

    /** Customizations */
    private static final Listener<Peer> unblacklister = Peer::unBlacklist;

    private static boolean runInSuite = false;
    private static boolean isStarted = false;
    protected static boolean fastTest = false;

    @BeforeClass
    public static void firstInit() {

        if (isStarted) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("nxt.isOffline", "false");
        properties.setProperty("nxt.apiServerHost", "127.0.0.1");
        properties.setProperty("nxt.enableAPIProxy", "false");
        properties.setProperty("nxt.peerServerHost", "127.0.0.1");
        properties.setProperty("nxt.myAddress", "127.0.0.1");
        properties.setProperty("nxt.defaultPeers", "");
        properties.setProperty("nxt.wellKnownPeers", "");
        properties.setProperty("nxt.defaultTestnetPeers", "");
        properties.setProperty("nxt.testnetPeers", "");
        properties.setProperty("nxt.enablePeerUPnP", "false");
        properties.setProperty("nxt.testDbDir", testDbDir);
        properties.setProperty("nxt.testStateDbDir", testStateDbDir);
        properties.setProperty("nxt.disablePeerUnBlacklistingThread", "true");
        properties.setProperty("nxt.disablePeerConnectingThread", "true");
        properties.setProperty("nxt.disableGetMorePeersThread", "true");
        properties.setProperty("nxt.disableUpdatePeerDbThread", "true");
        properties.setProperty("nxt.disableGenerateBlocksThread", "false");
        properties.setProperty("nxt.disableSecurityPolicy", "true");
        properties.setProperty("nxt.preventLocalPeersConnection", "false");
        properties.setProperty("nxt.validatePorts", "false");
        properties.setProperty("nxt.isTestnet", "true");
        properties.setProperty("nxt.isAutomatedTest", "true");
        properties.setProperty("nxt.savePeers", "false");
        properties.setProperty("nxt.usePeersDb", "false");
        properties.setProperty("nxt.testnetProxyBootstrapNodes", "");
        properties.setProperty("nxt.blacklistingPeriod", "1");
        properties.setProperty("nxt.disableProcessTransactionsThread", "true");
        properties.setProperty("nxt.communicationLogging", String.valueOf(LOG_LEVEL));
        properties.setProperty("nxt.disableAdminPassword", "true");
        properties.setProperty("nxt.disableGetMoreBlocksThread", "true");

        properties.setProperty("nxt.peerConnectTimeout", "3");
        properties.setProperty("nxt.peerReadTimeout", "3");

        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            properties.putAll(additionalProperties);
        }

        Nxt.init(Setup.UNIT_TEST, properties);
        isStarted = true;
    }

    public static void setRunInSuite(boolean runInSuite) {
        AbstractPeersTest.runInSuite = runInSuite;
    }

    public static void activateFastTest() {
        AbstractPeersTest.fastTest = true;
    }

    public static void setAdditionalProperties(Properties additionalProperties) {
        AbstractPeersTest.additionalProperties = additionalProperties;
    }

    public static void activateUnblacklisting() {
        peerManager.addListener(unblacklister, Peers.Event.BLACKLIST);
    }

    public static void deactivateUnblacklisting() {
        peerManager.removeListener(unblacklister, Peers.Event.BLACKLIST);
    }

    @After
    public void cleanUp() {
        if (useStatistics)
            resetStatistics();
    }

    @AfterClass
    public static void finalShutdown() {
        if (!runInSuite) {
            Nxt.getBlockchainProcessor().popOffTo(0);
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                Nxt.shutdown();
                return null;
            });
            if (torProcess != null && torProcess.isAlive()) {
                torProcess.destroy();
            }
            isStarted = false;
        }
    }

    protected static void initPeersTest() {

        peerManager = new PeerManager();
        peerManager.removeAll(p -> true, true);
    }

    protected static void assertPeers(int numPeers, int numConnected, int numHidden) {
        assertEquals(numPeers, peerManager.getNumPeers());
        assertEquals(numConnected, peerManager.getNumConnected());
    }

    protected void waitOnStep(BooleanSupplier condition) {
        while (!condition.getAsBoolean()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                fail();
            }
        }
    }

    protected void prepareStatistics(Peer... peers) {
        useStatistics = true;
        int numPeers = peers.length;
        numBlacklists = new HashMap<>(numPeers);
        numUnblacklists = new HashMap<>(numPeers);
        numAddPeers = new HashMap<>(numPeers);
        numChangeAddresses = new HashMap<>(numPeers);
        numChangeServices = new HashMap<>(numPeers);
        numConnects = new HashMap<>(numPeers);
        numDisconnects = new HashMap<>(numPeers);
        numRemoves = new HashMap<>(numPeers);
        for (Peer peer : peers) {
            numBlacklists.put(peer, 0);
            numUnblacklists.put(peer, 0);
            numAddPeers.put(peer, 0);
            numChangeAddresses.put(peer, 0);
            numChangeServices.put(peer, 0);
            numConnects.put(peer, 0);
            numDisconnects.put(peer, 0);
            numRemoves.put(peer, 0);
        }

        blacklistListener = p -> numBlacklists.put(p, numBlacklists.getOrDefault(p,0) + 1);
        unblacklistListener = p -> numUnblacklists.put(p, numUnblacklists.getOrDefault(p,0) + 1);
        addPeerListener = p -> numAddPeers.put(p, numAddPeers.getOrDefault(p,0) + 1);
        changeAddressListener = p -> numChangeAddresses.put(p, numChangeAddresses.getOrDefault(p,0) + 1);
        changeServicesListener = p -> {
            numChangeServices.put(p, numChangeServices.getOrDefault(p,0) + 1);
        };
        connectListener = p -> {};
        changeListener = p -> {
            if (p.getState() == Peer.State.CONNECTED) {
                numConnects.put(p, numConnects.getOrDefault(p,0) + 1);
            } else if (p.getState() == Peer.State.DISCONNECTED) {
                numDisconnects.put(p, numDisconnects.getOrDefault(p,0) + 1);
            }
        };
        removePeerListener = p -> numRemoves.put(p, numRemoves.getOrDefault(p,0) + 1);

        peerManager.addListener(blacklistListener, Peers.Event.BLACKLIST);
        peerManager.addListener(unblacklistListener, Peers.Event.UNBLACKLIST);
        peerManager.addListener(addPeerListener, Peers.Event.ADD_PEER);
        peerManager.addListener(changeAddressListener, Peers.Event.CHANGE_ANNOUNCED_ADDRESS);
        peerManager.addListener(changeServicesListener, Peers.Event.CHANGE_SERVICES);
        peerManager.addListener(connectListener, Peers.Event.ADD_ACTIVE_PEER);
        peerManager.addListener(changeListener, Peers.Event.CHANGE_ACTIVE_PEER);
        peerManager.addListener(removePeerListener, Peers.Event.REMOVE_PEER);
    }

    protected void resetStatistics() {
        useStatistics = false;
        peerManager.removeListener(blacklistListener, Peers.Event.BLACKLIST);
        peerManager.removeListener(unblacklistListener, Peers.Event.UNBLACKLIST);
        peerManager.removeListener(addPeerListener, Peers.Event.ADD_PEER);
        peerManager.removeListener(changeAddressListener, Peers.Event.CHANGE_ANNOUNCED_ADDRESS);
        peerManager.removeListener(changeServicesListener, Peers.Event.CHANGE_SERVICES);
        peerManager.removeListener(connectListener, Peers.Event.ADD_ACTIVE_PEER);
        peerManager.removeListener(changeListener, Peers.Event.CHANGE_ACTIVE_PEER);
        peerManager.removeListener(removePeerListener, Peers.Event.REMOVE_PEER);
        blacklistListener = null;
        unblacklistListener = null;
        addPeerListener = null;
        changeAddressListener = null;
        changeServicesListener = null;
        connectListener = null;
        changeListener = null;
        removePeerListener = null;
        numBlacklists = null;
        numUnblacklists = null;
        numAddPeers = null;
        numChangeAddresses = null;
        numChangeServices = null;
        numConnects = null;
        numDisconnects = null;
        numRemoves = null;
    }

    protected void printPeers() {
        Loggy.info("[RESULT] Peers:");
        Loggy.info("Current Peers: {}, {} connected",
                peerManager.getNumPeers(),
                peerManager.getNumConnected());
    }

    protected void printStatistics() {}

    protected void printStatistics(long testDuration) {
        Loggy.info("[RESULT]");
        Set<Peer> allPeers = new HashSet<>();
        allPeers.addAll(numBlacklists.keySet());
        allPeers.addAll(numUnblacklists.keySet());
        allPeers.addAll(numAddPeers.keySet());
        allPeers.addAll(numChangeAddresses.keySet());
        allPeers.addAll(numChangeServices.keySet());
        allPeers.addAll(numConnects.keySet());
        allPeers.addAll(numDisconnects.keySet());
        allPeers.addAll(numRemoves.keySet());
        List<Peer> peers = allPeers.stream().sorted(Comparator.comparing(Peer::getHost)).collect(Collectors.toList());
        for (Peer peer : peers) {
            Loggy.info("---- {}  {} connects, {} disconnects, {} blacklists, {} unblacklists, {} adds, {} removes, {} addressChanges, {} serviceChanges",
                    peer.getHost(), numConnects.get(peer), numDisconnects.get(peer), numBlacklists.get(peer), numUnblacklists.get(peer),
                    numAddPeers.get(peer), numRemoves.get(peer), numChangeAddresses.get(peer), numChangeServices.get(peer));
        }
        if (testDuration > 0) {
            //BigDecimal divisor = BigDecimal.valueOf((double) testDuration / 1000);
            BigDecimal divisor = BigDecimal.valueOf(testDuration).divide(BigDecimal.valueOf(1000), BigDecimal.ROUND_HALF_UP);
            for (Peer peer : peers) {
                BigDecimal consPerSec = BigDecimal.valueOf(numConnects.get(peer)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal discPerSec = BigDecimal.valueOf(numDisconnects.get(peer)).setScale(2, RoundingMode.HALF_UP);
                consPerSec = consPerSec.divide(divisor, RoundingMode.HALF_UP);
                discPerSec = discPerSec.divide(divisor, RoundingMode.HALF_UP);
                Loggy.info("---- {}  {} connects/s, {} disconnects/s",
                        peer.getHost(), consPerSec, discPerSec);
            }
        }
    }

    protected void sanityCheck(int numPeers, int numHidden, boolean isPureInboundTest, boolean isPureOutboundTest) {
        Loggy.info("[SANITY CHECK]");
        int numConnected = peerManager.getNumConnected();
        assertEquals(numConnected, peerManager.getNumConnected());
        assertEquals(numPeers, peerManager.getNumPeers());
        Loggy.info("sanity check successful...");
    }

    protected void healthCheckConnection(Peer... peers) {
        Loggy.info("[HEALTH CHECK 1]");
        int numPeers = peers.length;
        for (Peer peer : peers) {
            if (peer.getState() == Peer.State.DISCONNECTED) {
                peerManager.connect(peer).waitHandshake();
            }
        }
        assertPeers(numPeers, numPeers, 0);
        Loggy.info("health check 1 successful...");
    }

    protected void healthCheckReconnectAllOutbound(Peer... peers) throws InterruptedException {
        Loggy.info("[HEALTH CHECK 2]");
        int numPeers = peers.length;
        for (Peer peer : peers) {
            peer.disconnectPeer();
            Thread.sleep(10); // Wait for the other side to close cleanly
            peerManager.connect(peer).waitHandshake();
        }
        assertPeers(numPeers, numPeers, 0);
        Loggy.info("health check 2 successful...");
    }


    protected void healthCheckPingPong(Peer... peers) {
        Loggy.info("[HEALTH CHECK 3]");
        for (Peer peer : peers) {
            Instant start = Instant.now();
            NetworkMessage.CumulativeDifficultyMessage response =
                    (NetworkMessage.CumulativeDifficultyMessage)peer.sendRequest(new NetworkMessage.GetCumulativeDifficultyMessage());
            long tripTime = Duration.between(start, Instant.now()).toMillis();
            Loggy.info("---- [HEALTH CHECK] Ping to {} took {} ms", peer.getHost(), tripTime);
            assertNotNull(response);
            Loggy.info("health check 3 successful...");
        }
    }

    protected static void assertTcpConnections(int numActive, int numInbound, int numOutbound, int numZombie) {
//        assertEquals(numActive, tcpConnectionManager.getNumActive());
//        assertEquals(numInbound, tcpConnectionManager.getNumInbound());
//        assertEquals(numOutbound, tcpConnectionManager.getNumOutbound());
//        assertEquals(numZombie, tcpConnectionManager.getNumZombie());
    }
}
