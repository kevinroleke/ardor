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
import nxt.addons.JO;
import nxt.configuration.Setup;
import nxt.configuration.SubSystem;
import nxt.http.callers.AddPeerCall;
import nxt.http.callers.DumpPeersCall;
import nxt.http.callers.ShutdownCall;

import nxt.peer.Peer;
import nxt.peer.Peers;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Start a background Ardor instance for test purpose. Each instance is bound to one protocol.
 */
public class PeersNetwork {

    /** Use the new P2P DB tests. The contents will be copied from this location to temp folder. First run may be slow */
    public static final String TEST_DB_LOCATION = "./nxt_p2p_unit_test_db";

    /** Default API port used for all instances */
    private static final int API_PORT = 26875;

    /** Temporary folder for DB */
    private final File tempFolder;

    /** TCP address for this background instance. This is also the API server address regardless of protocol */
    private final String tcpAddress;

    /** The API address to execute API calls */
    private final String apiAddress;

    /** The announcedAddress distributed to other nets */
    private String announcedAddress = null;

    /** Hide ports in announcedAddresses */
    private boolean hidePort = false;

    /** Hide the announced address */
    private boolean hideAddress = false;

    /** Add additional OverloadTest threads to be executed after startup */
    private final List<String> overloadTests = new ArrayList<>();

    /** Set the delay in millis after which the tests will start */
    private int overloadDelay = 5000;

    /** Add initial Peers */
    private List<String> addPeers = new ArrayList<>();

    /** Option to run without a full blockchain */
    private static boolean reducedSetup = false;

    private Process process;
    private volatile boolean isStarted = false;
    private volatile boolean isExiting = false;

    /**
     * Construct the Peer Network.
     *
     *
     * @param tempFolder       temporary folder to hold copied DB files
     * @param tcpAddress       specifies the host of the API and TCP NetworkListener
     * @param announcedAddress denotes which announcedAddress the network will send via GetInfo. Can be null
     */
    public PeersNetwork(File tempFolder, String tcpAddress, String announcedAddress) {

        this.tempFolder = tempFolder;
        if (tcpAddress == null || tcpAddress.isEmpty()) {
            throw new IllegalStateException("Please provide a valid tcpAddress in order for the API to be functional!");
        }
        this.tcpAddress = tcpAddress;
        apiAddress = "http://" + tcpAddress + ":" + API_PORT + "/nxt";
        this.announcedAddress = announcedAddress;
    }

    public static void useReducedSetup() {
        reducedSetup = true;
    }

    public String getTcpAddress() {
        return tcpAddress;
    }

    public void setAnnouncedAddress(String announcedAddress) {
        this.announcedAddress = announcedAddress;
    }

    public void hidePort() {
        hidePort = true;
    }

    public void hideAddress() {
        hideAddress = true;
    }

    /**
     * Adds a String with all the parameters necessary to start an OverloadTest in main().
     * A PeerNetwork can have multiple OverloadTests and launch them all in parallel.
     *
     * @param parameters first parameter is always {@link Overload.Name} as a String, followed by the exact number
     *                   of necessary constructor parameters for that particular Test (may vary per Test).
     */
    public void addOverloadTest(Object... parameters) {
        overloadTests.add(Overload.toString(parameters));
    }

    public void overloadDelay(int delay) {
        if (delay >= 0) {
            this.overloadDelay = delay;
        }
    }

    public void addPeers(String... remoteAddress) {
        addPeers = Arrays.asList(remoteAddress);
    }

    public void start() {
        if (isStarted) {
            return;
        }
        final String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        final String classPath = System.getProperty("java.class.path");
        try {
            ProcessBuilder builder = new ProcessBuilder(Arrays.asList(java, "-cp", classPath, getClass().getCanonicalName(),
                    tempFolder.getCanonicalPath(),
                    "TCP",
                    "ta=" + (tcpAddress == null ? "" : tcpAddress),
                    "aa=" + (announcedAddress == null ? "" : announcedAddress),
                    String.valueOf(hidePort),
                    String.valueOf(hideAddress),
                    "ov=" + String.join(";", overloadTests),
                    String.valueOf(overloadDelay),
                    "ap=" + String.join(",", addPeers)
            ));
            builder.redirectErrorStream(true);
            process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        boolean childProcessStarted = false;
        Scanner bootstrapScanner = new Scanner(process.getInputStream(), "UTF-8");
        while (bootstrapScanner.hasNextLine()) {
            final String line = bootstrapScanner.nextLine();
            Loggy.debug(">> " + line);
            if ((reducedSetup && (line.contains("Peer Networking - started -")))
                    || line.contains("main RUNNING ON TESTNET - DO NOT USE REAL ACCOUNTS!")) {
                childProcessStarted = true;
                break;
            }
        }

        if (!childProcessStarted) {
            throw new RuntimeException("Peer Networking " + tcpAddress + " not started", bootstrapScanner.ioException());
        }

        Thread processOutputThread = new Thread(() -> {
            Scanner scanner = new Scanner(process.getInputStream());
            while (scanner.hasNextLine()) {
                Loggy.debug("[PEER NET " + tcpAddress + "] " + scanner.nextLine());
            }
        });
        processOutputThread.start();

        isStarted = true;
    }

    public void connect(String peerAddress) {
        if (!isStarted) {
            throw new IllegalStateException("PeersNetwork " + tcpAddress + " not started!");
        }
        try {
            System.out.println("Calling API ConnectCall on " + apiAddress);
            URL apiURL = new URL(apiAddress);
            AddPeerCall.create().peer(peerAddress).remote(apiURL).callNoError();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> dumpPeers() {
        if (!isStarted) {
            throw new IllegalStateException("PeersNetwork " + tcpAddress + " not started!");
        }
        try {
            System.out.println("Calling API DumpPeersCall on " + apiAddress);
            URL apiURL = new URL(apiAddress);
            JO jo = DumpPeersCall.create().remote(apiURL).callNoError();
            int numPeers = jo.getInt("count");
            if (numPeers == 0) {
                return Collections.emptyList();
            }
            return Arrays.stream(jo.getString("peers").split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Exit in the background
     */
    public void exit() {
        System.out.println("EXIT called on PEERS NETWORK " + tcpAddress);
        if (isExiting || !isStarted || process == null || !process.isAlive()) {
            System.out.println("PEERS NETWORK " + tcpAddress + " is not alive");
            return;
        }
        isExiting = true;
        System.out.println("Stopping PeersNetwork " + tcpAddress + " ...");
        try {
            System.out.println("Requesting API ShutdownCall on " + apiAddress);
            URL apiURL = new URL(apiAddress);
            ShutdownCall.create().remote(apiURL).callNoError();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            // ignore
        }

    }

    /**
     * Wait for exit to complete. Blocking operation. Calls exit() if necessary.
     */
    public void waitForExit() {
        if (!isExiting) {
            exit();
        }
        if (process != null && process.isAlive()) {
            System.out.println("Waiting...");
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            process = null;
            System.out.println("PeersNetwork " + tcpAddress + " stopped.");
        }
    }

    //######### Following methods are executed in a separate process #########

    public static void main(String[] args) throws IOException {

        if (args.length < 9) {
            throw new RuntimeException("Invalid args supplied: " + Arrays.toString(args));
        }
        String testDb = args[0];
        //NetworkProtocol protocol = NetworkProtocol.valueOf(args[1]);
        String tcpAddress = args[2].substring(3);
        String announcedAddress = args[3].substring(3);
        String hidePort = args[4];
        boolean hideAddress = Boolean.valueOf(args[5]);
        String overload = args[6].substring(3);
        int overloadDelay = Integer.valueOf(args[7]);
        String peers = args[8].substring(3);

        File sourceDB = new File(TEST_DB_LOCATION);
        if (!sourceDB.exists() || !sourceDB.isDirectory()) {
            throw new RuntimeException("DB source not found !");
        }

        File destDB = new File(testDb);
        copyFolder(sourceDB.toPath(), destDB.toPath());

        Runtime.getRuntime().addShutdownHook(new Thread(PeersNetwork::shutdownProcess));

        List<Runnable> overloadTests = null;
        if (overload != null && !overload.isEmpty()) {
            overloadTests = new ArrayList<>();
            String[] tests = overload.split(";");
            for (String test : tests) {
                test = test.trim();
                if (!test.isEmpty())
                    overloadTests.add(Overload.fromString(test));
            }
        }

        Properties properties = PeersNetwork.createProperties(testDb, tcpAddress, announcedAddress, hidePort, hideAddress);
        Nxt.init(reducedSetup ? getReducedSetup() : Setup.UNIT_TEST, properties);

        Peers.addListener(Peer::unBlacklist, Peers.Event.BLACKLIST);

        if (!peers.isEmpty()) {
            for (String peerAddr : peers.split(",")) {
                Peer peer = Peers.findOrCreatePeer(peerAddr, true);
                Peers.addPeer(peer);
            }
        }

        if (overloadTests != null) {
            try {
                Thread.sleep(overloadDelay);
            } catch (InterruptedException e) {
                throw new RuntimeException("Initial wait for test start interrupted!");
            }

            long maxRuntime = -1;
            List<Overload.OverloadTest> bgTasks = new ArrayList<>();
            for (Runnable test : overloadTests) {
                bgTasks.add((Overload.OverloadTest) test);
                maxRuntime = Math.max(maxRuntime, ((Overload.OverloadTest) test).getRuntime());
                Thread backgroundThread = new Thread(test);
                backgroundThread.start();
            }
            try {
                Thread.sleep(maxRuntime);
            } catch (InterruptedException e) {
                throw new RuntimeException("Wait for task completion interrupted!");
            }
            bgTasks.forEach(Overload.OverloadTest::stop);
        }

    }

    private static Setup getReducedSetup() {
        return new Setup() {
            @Override
            public List<SubSystem> initSequence() {
                return Arrays.asList(SubSystem.LOGGER, SubSystem.PEER_NETWORKING);
            }

            @Override
            public List<SubSystem> shutdownSequence() {
                return Arrays.asList(SubSystem.PEER_NETWORKING, SubSystem.LOGGER);
            }
        };
    }

    private static void shutdownProcess() {
        Loggy.debug("shutdownProcess called...");
        //Nxt.shutdown(); // not needed, as the DB is bootstrapped everytime from the MAIN DB
    }

    private static void copyFolder(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }
    }

    private static void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static Properties createProperties(String testDb, String tcpAddress,
                                               String announcedAddress, String hidePort, boolean hideAddress) {
        Properties properties = new Properties();
        properties.setProperty("nxt.isOffline", "false");
        properties.setProperty("nxt.isTestnet", "true");
        properties.setProperty("nxt.isAutomatedTest", "true");
        properties.setProperty("nxt.defaultPeers", "");
        properties.setProperty("nxt.wellKnownPeers", "");
        properties.setProperty("nxt.defaultTestnetPeers", "");
        properties.setProperty("nxt.testnetPeers", "");
        properties.setProperty("nxt.savePeers", "false");
        properties.setProperty("nxt.usePeersDb", "false");
        properties.setProperty("nxt.testDbDir", testDb + "/nxt");
        properties.setProperty("nxt.testStateDbDir", testDb + "/state");
        properties.setProperty("nxt.disablePeerUnBlacklistingThread", "true");
        properties.setProperty("nxt.disablePeerConnectingThread", "true");
        properties.setProperty("nxt.disableGetMorePeersThread", "true");
        properties.setProperty("nxt.disableUpdatePeerDbThread", "true");
        properties.setProperty("nxt.disableGenerateBlocksThread", reducedSetup ? "true" : "false");
        properties.setProperty("nxt.disableGetMoreBlocksThread", "true");
        properties.setProperty("nxt.enableAPIProxy", "false");
        properties.setProperty("nxt.enablePeerUPnP", "false");
        properties.setProperty("nxt.disableSecurityPolicy", "true");
        properties.setProperty("nxt.preventLocalPeersConnection", "false");
        properties.setProperty("nxt.validatePorts", "false");
        properties.setProperty("nxt.testnetProxyBootstrapNodes", "");
        properties.setProperty("nxt.communicationLogging", "1");
        properties.setProperty("nxt.disableAdminPassword", "true");
        properties.setProperty("nxt.apiServerHost", tcpAddress);
        properties.setProperty("nxt.hideMyPort", hidePort);

        if (tcpAddress == null || tcpAddress.isEmpty()) {
            throw new IllegalArgumentException("TCP address not specified!");
        }
        properties.setProperty("nxt.peerConnectTimeout", "5");
        properties.setProperty("nxt.peerReadTimeout", "5");
        properties.setProperty("nxt.useTcp", "true");
        properties.setProperty("nxt.useTor", "false");
        properties.setProperty("nxt.peerServerHost", tcpAddress);
        properties.setProperty("nxt.myAddress", hideAddress ? "" : announcedAddress);


        return properties;
    }

}
