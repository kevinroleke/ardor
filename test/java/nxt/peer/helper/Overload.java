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


import nxt.peer.NetworkMessage;
import nxt.peer.Peer;

import nxt.peer.Peers;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Overload {

    public enum Name {
        CONNECTOBOT,
        DISCONNECTOBOT,
        REQUESTOBOT,
        SENDOBOT
    }

    public interface OverloadTest {
        long getRuntime();

        void stop();
    }

    public static Runnable fromString(String parameters) {
        if (parameters == null || parameters.isEmpty()) {
            throw new IllegalArgumentException("OverloadTest parameters are invalid!");
        }
        String[] params = parameters.split(",");
        switch (Name.valueOf(params[0])) {
            case CONNECTOBOT:
                return new ConnectoBot(
                        Long.valueOf(params[1]),
                        Boolean.valueOf(params[2]),
                        Boolean.valueOf(params[3]),
                        Boolean.valueOf(params[4]),
                        Boolean.valueOf(params[5]),
                        Boolean.valueOf(params[6]),
                        Integer.valueOf(params[7]),
                        Integer.valueOf(params[8]),
                        Boolean.valueOf(params[9]));
            case DISCONNECTOBOT:
                return new DisconnectoBot(
                        Long.valueOf(params[1]),
                        Boolean.valueOf(params[2]),
                        Boolean.valueOf(params[3]),
                        Boolean.valueOf(params[4]),
                        Integer.valueOf(params[5]),
                        Integer.valueOf(params[6]),
                        Boolean.valueOf(params[7]));
            case REQUESTOBOT:
                return new RequestoBot(
                        Long.valueOf(params[1]),
                        Integer.valueOf(params[2]),
                        Boolean.valueOf(params[3]),
                        Integer.valueOf(params[4]),
                        Integer.valueOf(params[5]),
                        Boolean.valueOf(params[6]));
            case SENDOBOT:
                return new SendoBot(
                        Long.valueOf(params[1]),
                        Integer.valueOf(params[2]),
                        Integer.valueOf(params[3]),
                        Boolean.valueOf(params[4]),
                        Integer.valueOf(params[5]),
                        Integer.valueOf(params[6]),
                        Boolean.valueOf(params[7]));
        }
        throw new IllegalArgumentException("OverloadTest " + params[0] + " unknown!");
    }

    public static String toString(Object... parameters) {
        if (parameters == null || parameters.length == 0) {
            throw new IllegalArgumentException("OverloadTest parameters are invalid!");
        }
        StringBuilder sb = new StringBuilder();
        Name testName = (Name) parameters[0];
        switch (testName) {
            case CONNECTOBOT:
                if (parameters.length != 10)
                    throw new IllegalArgumentException("Wrong number of parameters!");
                sb.append(testName.name()).append(",");
                sb.append((long) parameters[1]).append(",");
                sb.append((boolean) parameters[2]).append(",");
                sb.append((boolean) parameters[3]).append(",");
                sb.append((boolean) parameters[4]).append(",");
                sb.append((boolean) parameters[5]).append(",");
                sb.append((boolean) parameters[6]).append(",");
                sb.append((int) parameters[7]).append(",");
                sb.append((int) parameters[8]).append(",");
                sb.append((boolean) parameters[9]);
                return sb.toString();
            case DISCONNECTOBOT:
                if (parameters.length != 8)
                    throw new IllegalArgumentException("Wrong number of parameters!");
                sb.append(testName.name()).append(",");
                sb.append((long) parameters[1]).append(",");
                sb.append((boolean) parameters[2]).append(",");
                sb.append((boolean) parameters[3]).append(",");
                sb.append((boolean) parameters[4]).append(",");
                sb.append((int) parameters[5]).append(",");
                sb.append((int) parameters[6]).append(",");
                sb.append((boolean) parameters[7]);
                return sb.toString();
            case REQUESTOBOT:
                if (parameters.length != 7)
                    throw new IllegalArgumentException("Wrong number of parameters!");
                sb.append(testName.name()).append(",");
                sb.append((long) parameters[1]).append(",");
                sb.append((int) parameters[2]).append(",");
                sb.append((boolean) parameters[3]).append(",");
                sb.append((int) parameters[4]).append(",");
                sb.append((int) parameters[5]).append(",");
                sb.append((boolean) parameters[6]);
                return sb.toString();
            case SENDOBOT:
                if (parameters.length != 8)
                    throw new IllegalArgumentException("Wrong number of parameters!");
                sb.append(testName.name()).append(",");
                sb.append((long) parameters[1]).append(",");
                sb.append((int) parameters[2]).append(",");
                sb.append((int) parameters[3]).append(",");
                sb.append((boolean) parameters[4]).append(",");
                sb.append((int) parameters[5]).append(",");
                sb.append((int) parameters[6]).append(",");
                sb.append((boolean) parameters[7]);
                return sb.toString();
        }
        throw new IllegalArgumentException("OverloadTest " + parameters[0] + " unknown!");
    }

    /** The 'global' request ID tracker */
    private static final AtomicLong requestId = new AtomicLong(1000000000);

    /** The 'global' message number tracker */
    private static final AtomicLong messageId = new AtomicLong(1000000000);

    /** The random String source */
    private static final List<String> RANDOM_STRINGS = Arrays.asList("Name1%?", "Name2#?", "Any!");

    /**
     * Get a suitable ExecutorService determined from parameters.
     *
     * @param useInternalWorkPool use the Peers workPool (has precedence over other parameters)
     * @param maxThreads          if {@code > 0}, uses a fixed ThreadPool Executor. If {@code == 0}, then
     *                            an unlimited ThreadPool will be used (currently CachedThreadPool)
     * @return the ExecutorService to spawn new job Threads
     */
    private static ExecutorService getExecutor(boolean useInternalWorkPool, int maxThreads) {
        if (useInternalWorkPool) {
            throw new UnsupportedOperationException();
        }
        if (maxThreads > 0) {
            return Executors.newFixedThreadPool(maxThreads);
        }
        return Executors.newCachedThreadPool();
    }

    /**
     * A Thread that starts other threads to execute multiple connects in parallel to randomly chosen Peers.
     * <p>
     * The nature of the random selection mechanism where same Peer can be selected multiple times in one iteration
     * is chosen on purpose to uncover unintended loopholes in the system.
     */
    public static class ConnectoBot implements Runnable, OverloadTest {

        /** Job runtime in millis */
        private final long runtime;

        /** Always add more jobs than there are Peers, to emphasize focus on race-conditions */
        private final boolean alwaysOverload;

        /** May randomly add more jobs than there are Peers, to increase race-condition probability */
        private final boolean randomOverload;

        /** Always wait until connection is READY */
        private final boolean alwaysWait;

        /** May randomly wait until connection is READY */
        private final boolean randomWait;

        /** Use Peers workPool */
        private final boolean useInternalWorkPool;

        /** Limit the maximum number of active threads */
        private final int maxThreads;

        /** Delay connect jobs between iterations */
        private final int connectDelay;

        /** Invoke all jobs at once and wait for completion */
        private final boolean doInvoke;

        /** Start-stop control */
        private boolean doRun;
        private ExecutorService executor;
        private final boolean isThrottled; // Apply Job throttling mechanism for fixed Thread Pools
        private Thread runningThread = null;

        public ConnectoBot(long runtime, boolean alwaysOverload, boolean randomOverload,
                           boolean alwaysWait, boolean randomWait, boolean useInternalWorkPool,
                           int maxThreads, int connectDelay, boolean doInvoke) {
            this.runtime = runtime;
            this.alwaysOverload = alwaysOverload;
            this.randomOverload = randomOverload;
            this.alwaysWait = alwaysWait;
            this.randomWait = randomWait;
            this.useInternalWorkPool = useInternalWorkPool;
            this.maxThreads = maxThreads;
            this.connectDelay = connectDelay;
            this.doInvoke = doInvoke;
            this.isThrottled = !useInternalWorkPool && maxThreads > 0;
            doRun = true;
        }

        @Override
        public void stop() {
            doRun = false;
            if (runningThread != null) {
                runningThread.interrupt();
            }
            if (!useInternalWorkPool) {
                executor.shutdownNow();
                if (isThrottled) {
                    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                    pool.getQueue().clear();
                }
                try {
                    executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Await termination interrupted!");
                }
            }
        }

        @Override
        public long getRuntime() {
            return runtime;
        }

        @Override
        public void run() {
            PeerManager peerManager = new PeerManager();
            int numPeers = peerManager.getNumPeers();
            if (numPeers == 0) {
                throw new IllegalStateException("No Peers found!");
            }
            runningThread = Thread.currentThread();
            executor = Overload.getExecutor(useInternalWorkPool, maxThreads);
            List<Peer> peers = peerManager.getAllPeers();
            Loggy.info("[START] ConnectoBot with {} Peers", numPeers);
            Runnable[] connectJobs = null;
            List<Callable<Object>> connectCalls = null;
            Instant start = Instant.now();
            while (doRun && Duration.between(start, Instant.now()).toMillis() < runtime) {
                int numConnects = getNumConnects(numPeers);
                Loggy.info("ConnectoBot iteration for {} peers", numConnects);
                if (doInvoke)
                    connectCalls = new ArrayList<>(numConnects);
                else
                    connectJobs = new Runnable[numConnects];
                for (int i = 0; i < numConnects; i++) {
                    int peerId = ThreadLocalRandom.current().nextInt(numPeers);
                    Peer peer = peers.get(peerId);
                    Runnable job = getWaitReady() ? () -> peerManager.connect(peer).waitHandshake()
                            : () -> peerManager.connect(peer);
                    if (doInvoke) {
                        connectCalls.add(Executors.callable(job));
                    } else
                        connectJobs[i] = job;
                }
                try {
                    if (doInvoke)
                        executor.invokeAll(connectCalls);
                    else {
                        for (Runnable job : connectJobs) {
                            executor.submit(job);
                        }
                    }
                    if (connectDelay > 0)
                        Thread.sleep(connectDelay);
                    if (isThrottled) {
                        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                        if (pool.getQueue().size() > maxThreads * 2) {
                            Instant waitStart = Instant.now();
                            while (!pool.getQueue().isEmpty()) {
                                Thread.sleep(10);
                            }
                            Loggy.debug("ConnectoBot is throttled: SLEEP for {} ms", Duration.between(waitStart, Instant.now()).toMillis());
                        }
                    }
                } catch (InterruptedException | RejectedExecutionException e) {
                    Loggy.debug("ConnectoBot interrupted successfully");
                    break;
                }
            }
            doRun = false;
            Loggy.info("[END] ConnectoBot", numPeers);
        }

        private int getNumConnects(int numPeers) {
            if (alwaysOverload)
                return numPeers == 1 ? 2 : ThreadLocalRandom.current().nextInt(numPeers + 1, 2 * numPeers);
            if (randomOverload)
                return ThreadLocalRandom.current().nextInt(1, 2 * numPeers);
            return numPeers == 1 ? 1 : ThreadLocalRandom.current().nextInt(1, numPeers);
        }

        private boolean getWaitReady() {
            if (alwaysWait)
                return true;
            if (randomWait)
                return ThreadLocalRandom.current().nextBoolean();
            return false;
        }
    }

    /**
     * A Thread that starts other threads to execute multiple disconnects in parallel to randomly chosen Peers.
     * <p>
     * The nature of the random selection mechanism where same Peer can be selected multiple times in one iteration
     * is chosen on purpose to uncover unintended loopholes in the system.
     */
    public static class DisconnectoBot implements Runnable, OverloadTest {

        /** Job runtime in millis */
        private final long runtime;

        /** Always add more jobs than there are Peers, to emphasize focus on race-conditions */
        private final boolean alwaysOverload;

        /** May randomly add more jobs than there are Peers, to increase race-condition probability */
        private final boolean randomOverload;

        /** Use Peers workPool */
        private final boolean useInternalWorkPool;

        /** Limit the maximum number of active threads */
        private final int maxThreads;

        /** Delay connect jobs between iterations */
        private final int disconnectDelay;

        /** Invoke all jobs at once and wait for completion */
        private final boolean doInvoke;

        /** Start-stop control */
        private boolean doRun;
        private ExecutorService executor;
        private final boolean isThrottled; // Apply Job throttling mechanism for fixed Thread Pools
        private Thread runningThread = null;

        public DisconnectoBot(long runtime, boolean alwaysOverload, boolean randomOverload,
                              boolean useInternalWorkPool, int maxThreads, int disconnectDelay, boolean doInvoke) {
            this.runtime = runtime;
            this.alwaysOverload = alwaysOverload;
            this.randomOverload = randomOverload;
            this.useInternalWorkPool = useInternalWorkPool;
            this.maxThreads = maxThreads;
            this.disconnectDelay = disconnectDelay;
            this.doInvoke = doInvoke;
            this.isThrottled = !useInternalWorkPool && maxThreads > 0;
            doRun = true;
        }

        @Override
        public void stop() {
            doRun = false;
            if (runningThread != null) {
                runningThread.interrupt();
            }
            if (!useInternalWorkPool) {
                executor.shutdownNow();
                if (isThrottled) {
                    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                    pool.getQueue().clear();
                }
                try {
                    executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Await termination interrupted!");
                }
            }
        }

        @Override
        public long getRuntime() {
            return runtime;
        }

        @Override
        public void run() {
            PeerManager peerManager = new PeerManager();
            int numPeers = peerManager.getNumPeers();
            if (numPeers == 0) {
                throw new IllegalStateException("No Peers found!");
            }
            runningThread = Thread.currentThread();
            executor = Overload.getExecutor(useInternalWorkPool, maxThreads);
            List<Peer> peers = peerManager.getAllPeers();
            Loggy.info("[START] DisconnectoBot with {} Peers", numPeers);
            Runnable[] disconnectJobs = null;
            List<Callable<Object>> disconnectCalls = null;
            Instant start = Instant.now();
            while (doRun && Duration.between(start, Instant.now()).toMillis() < runtime) {
                int numDisconnects = getNumDisconnects(numPeers);
                Loggy.info("DisconnectoBot iteration for {} peers", numDisconnects);
                if (doInvoke)
                    disconnectCalls = new ArrayList<>(numDisconnects);
                else
                    disconnectJobs = new Runnable[numDisconnects];
                for (int i = 0; i < numDisconnects; i++) {
                    int peerId = ThreadLocalRandom.current().nextInt(numPeers);
                    Peer peer = peers.get(peerId);
                    if (doInvoke) {
                        disconnectCalls.add(Executors.callable(peer::disconnectPeer));
                    } else
                        disconnectJobs[i] = peer::disconnectPeer;
                }
                try {
                    if (doInvoke)
                        executor.invokeAll(disconnectCalls);
                    else {
                        for (Runnable job : disconnectJobs) {
                            executor.submit(job);
                        }
                    }
                    if (disconnectDelay > 0)
                        Thread.sleep(disconnectDelay);
                    if (isThrottled) {
                        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                        if (pool.getQueue().size() > maxThreads * 2) {
                            Instant waitStart = Instant.now();
                            while (!pool.getQueue().isEmpty()) {
                                Thread.sleep(10);
                            }
                            Loggy.debug("DisconnectoBot is throttled: SLEEP for {} ms", Duration.between(waitStart, Instant.now()).toMillis());
                        }
                    }
                } catch (InterruptedException | RejectedExecutionException e) {
                    Loggy.debug("DisconnectoBot interrupted successfully");
                    break;
                }
            }
            doRun = false;
            Loggy.info("[END] DisconnectoBot", numPeers);
        }

        private int getNumDisconnects(int numPeers) {
            if (alwaysOverload)
                return numPeers == 1 ? 2 : ThreadLocalRandom.current().nextInt(numPeers + 1, 2 * numPeers);
            if (randomOverload)
                return ThreadLocalRandom.current().nextInt(1, 2 * numPeers);
            return numPeers == 1 ? 1 : ThreadLocalRandom.current().nextInt(1, numPeers);
        }
    }

    /**
     * A Thread that starts other threads to execute multiple requests in parallel to randomly chosen Peers.
     */
    public static class RequestoBot implements Runnable, OverloadTest {

        /** Job runtime in millis */
        private final long runtime;

        /** Number of Requests per iteration */
        private final int numRequests;

        /** Use Peers workPool */
        private final boolean useInternalWorkPool;

        /** Limit the maximum number of active threads */
        private final int maxThreads;

        /** Delay connect jobs between iterations */
        private final int sendDelay;

        /** Invoke all jobs at once and wait for completion */
        private final boolean doInvoke;

        /** Start-stop control */
        private boolean doRun;
        private ExecutorService executor;
        private final boolean isThrottled; // Apply Job throttling mechanism for fixed Thread Pools
        private Thread runningThread = null;

        /** Response statistics */
        private volatile long numSuccessful = 0;
        private volatile long numErrors = 0;
        private volatile NetworkMessage lastSuccess = null;

        public RequestoBot(long runtime, int numRequests, boolean useInternalWorkPool, int maxThreads,
                           int sendDelay, boolean doInvoke) {
            this.runtime = runtime;
            this.numRequests = numRequests;
            this.useInternalWorkPool = useInternalWorkPool;
            this.maxThreads = maxThreads;
            this.sendDelay = sendDelay;
            this.doInvoke = doInvoke;
            this.isThrottled = !useInternalWorkPool && maxThreads > 0;
            doRun = true;
        }

        @Override
        public void stop() {
            doRun = false;
            if (runningThread != null) {
                runningThread.interrupt();
            }
            if (!useInternalWorkPool) {
                executor.shutdownNow();
                if (isThrottled) {
                    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                    pool.getQueue().clear();
                }
                try {
                    executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Await termination interrupted!");
                }
            }
        }

        @Override
        public long getRuntime() {
            return runtime;
        }

        @Override
        public void run() {
            PeerManager peerManager = new PeerManager();
            int numPeers = peerManager.getNumPeers();
            if (numPeers == 0) {
                throw new IllegalStateException("No Peers found!");
            }
            if (numRequests <= 0) {
                throw new IllegalStateException("Number of Requests must be positive!");
            }
            runningThread = Thread.currentThread();
            executor = Overload.getExecutor(useInternalWorkPool, maxThreads);
            List<Peer> peers = peerManager.getAllPeers();
            Loggy.info("[START] RequestoBot with {} Peers", numPeers);
            Runnable[] requestJobs = null;
            List<Callable<Object>> requestCalls = null;
            Instant start = Instant.now();
            while (doRun && Duration.between(start, Instant.now()).toMillis() < runtime) {
                Loggy.info("RequestoBot iteration for {} requests", numRequests);
                if (doInvoke)
                    requestCalls = new ArrayList<>(numRequests);
                else
                    requestJobs = new Runnable[numRequests];
                for (int i = 0; i < numRequests; i++) {
                    int peerId = ThreadLocalRandom.current().nextInt(numPeers);
                    Peer peer = peers.get(peerId);
                    Runnable job = () -> {
                        if (peer.getState() == Peer.State.CONNECTED) {
                            NetworkMessage response = peer.sendRequest(new NetworkMessage.GetPeersMessage());
                            if (response == null) {
                                numErrors++;
                            } else {
                                numSuccessful++;
                                lastSuccess = response;
                            }
                        }
                    };
                    if (doInvoke) {
                        requestCalls.add(Executors.callable(job));
                    } else
                        requestJobs[i] = job;
                }
                try {
                    if (doInvoke)
                        executor.invokeAll(requestCalls);
                    else {
                        for (Runnable job : requestJobs) {
                            executor.submit(job);
                        }
                    }
                    if (sendDelay > 0)
                        Thread.sleep(sendDelay);
                    if (isThrottled) {
                        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                        if (pool.getQueue().size() > maxThreads * 2) {
                            Instant waitStart = Instant.now();
                            while (!pool.getQueue().isEmpty()) {
                                Thread.sleep(10);
                            }
                            Loggy.debug("RequestoBot is throttled: SLEEP for {} ms", Duration.between(waitStart, Instant.now()).toMillis());
                        }
                    }
                } catch (InterruptedException | RejectedExecutionException e) {
                    Loggy.debug("RequestoBot interrupted successfully");
                    break;
                }
            }
            doRun = false;
            Loggy.info("[END] RequestoBot", numPeers);
        }

        public long getNumSuccessful() {
            return numSuccessful;
        }

        public long getNumErrors() {
            return numErrors;
        }

        public NetworkMessage getLastSuccess() {
            return lastSuccess;
        }

    }

    /**
     * A Thread that starts other threads to send multiple messages in parallel to randomly chosen Peers.
     */
    public static class SendoBot implements Runnable, OverloadTest {

        private static final String PING_MESSAGE = "PING MSG ";

        /** Job runtime in millis */
        private final long runtime;

        /** Number of messages per iteration */
        private final int numMessages;

        /** Number of messages per iteration */
        private final int delay;

        /** Use Peers workPool */
        private final boolean useInternalWorkPool;

        /** Limit the maximum number of active threads */
        private final int maxThreads;

        /** Delay connect jobs between iterations */
        private final int sendDelay;

        /** Invoke all jobs at once and wait for completion */
        private final boolean doInvoke;

        /** Start-stop control */
        private boolean doRun;
        private ExecutorService executor;
        private final boolean isThrottled; // Apply Job throttling mechanism for fixed Thread Pools
        private Thread runningThread = null;

        public SendoBot(long runtime, int numMessages, int delay, boolean useInternalWorkPool, int maxThreads,
                        int sendDelay, boolean doInvoke) {
            this.runtime = runtime;
            this.numMessages = numMessages;
            this.delay = delay;
            this.useInternalWorkPool = useInternalWorkPool;
            this.maxThreads = maxThreads;
            this.sendDelay = sendDelay;
            this.doInvoke = doInvoke;
            this.isThrottled = !useInternalWorkPool && maxThreads > 0;
            doRun = true;
        }

        @Override
        public void stop() {
            doRun = false;
            if (runningThread != null) {
                runningThread.interrupt();
            }
            if (!useInternalWorkPool) {
                executor.shutdownNow();
                if (isThrottled) {
                    ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                    pool.getQueue().clear();
                }
                try {
                    executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Await termination interrupted!");
                }
            }
        }

        @Override
        public long getRuntime() {
            return runtime;
        }

        @Override
        public void run() {
            PeerManager peerManager = new PeerManager();
            int numPeers = peerManager.getNumPeers();
            if (numPeers == 0) {
                throw new IllegalStateException("No Peers found!");
            }
            if (numMessages <= 0) {
                throw new IllegalStateException("Number of messages must be positive!");
            }
            runningThread = Thread.currentThread();
            executor = Overload.getExecutor(useInternalWorkPool, maxThreads);
            List<Peer> peers = peerManager.getAllPeers();
            Loggy.info("[START] SendoBot with {} Peers", numPeers);
            Runnable[] messageJobs = null;
            List<Callable<Object>> messageCalls = null;
            Instant start = Instant.now();
            while (doRun && Duration.between(start, Instant.now()).toMillis() < runtime) {
                Loggy.info("SendoBot iteration for {} messages", numMessages);
                if (doInvoke)
                    messageCalls = new ArrayList<>(numMessages);
                else
                    messageJobs = new Runnable[numMessages];
                for (int i = 0; i < numMessages; i++) {
                    int peerId = ThreadLocalRandom.current().nextInt(numPeers);
                    Peer peer = peers.get(peerId);
                    Runnable job = () -> peer.sendMessage(nextRandomMessage(delay));
                    if (doInvoke) {
                        messageCalls.add(Executors.callable(job));
                    } else
                        messageJobs[i] = job;
                }
                try {
                    if (doInvoke)
                        executor.invokeAll(messageCalls);
                    else {
                        for (Runnable job : messageJobs) {
                            executor.submit(job);
                        }
                    }
                    if (sendDelay > 0)
                        Thread.sleep(sendDelay);
                    if (isThrottled) {
                        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
                        if (pool.getQueue().size() > maxThreads * 2) {
                            Instant waitStart = Instant.now();
                            while (!pool.getQueue().isEmpty()) {
                                Thread.sleep(10);
                            }
                            Loggy.debug("SendoBot is throttled: SLEEP for {} ms", Duration.between(waitStart, Instant.now()).toMillis());
                        }
                    }
                } catch (InterruptedException | RejectedExecutionException e) {
                    Loggy.debug("SendoBot interrupted successfully");
                    break;
                }
            }
            doRun = false;
            Loggy.info("[END] SendoBot", numPeers);
        }

        private NetworkMessage nextRandomMessage(int delay) {
            // todo: use FloodoBot to flood Peers with InfoMessages, BlockchainStates, BundlerRates, etc.
            return new NetworkMessage.GetPeersMessage();
        }
    }

}
