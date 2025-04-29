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

package nxt.blockchain;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.atomictxs.AtomicChain;
import nxt.blockchain.atomictxs.AtomicChainsSet;
import nxt.crypto.Crypto;
import nxt.db.DbIterator;
import nxt.db.FilteringIterator;
import nxt.peer.BundlerRate;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.util.security.BlockchainPermission;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Bundler {

    /**
     * Bundling filter - transactions for which the {@link #ok(Bundler, ChildTransaction)} method returns false are not processed by the {@link Rule}
     */
    public interface Filter {
        boolean ok(Bundler bundler, ChildTransaction childTransaction);

        default boolean ok(Bundler bundler, AtomicChain<? extends ChildTransaction> atomicChain) {
            return atomicChain.stream().allMatch(t -> ok(bundler, t));
        }

        default String getName() {
            return getClass().getSimpleName();
        }

        /**
         * For UI to work, the bundler description must also be added to translation.json with key
         * bundler_filter_help_*lowercase name*
         *
         * @return The bundler description.
         */
        default String getDescription() {
            return null;
        }
        default String getParameter() {
            return null;
        }
        default void setParameter(String parameter) {
            if (parameter != null && !parameter.isEmpty()) {
                throw new IllegalArgumentException("Bundler " + getClass() + " does not support parameters");
            }
        }
    }

    public interface FeeCalculator {
        long calculateFeeFQT(ChildTransactionImpl childTransaction, Rule rule);
        String getName();
        default void validateRule(Rule rule) {
        }
    }

    public static class MinFeeCalculator implements FeeCalculator {
        public static final String NAME = "MIN_FEE";

        @Override
        public long calculateFeeFQT(ChildTransactionImpl childTransaction, Rule rule) {
            int blockchainHeight = Nxt.getBlockchain().getHeight();
            return rule.overpay(childTransaction.getMinimumFeeFQT(blockchainHeight));
        }

        @Override
        public String getName() {
            return NAME;
        }
    }

    public static class ProportionalFeeCalculator implements FeeCalculator {
        @Override
        public long calculateFeeFQT(ChildTransactionImpl childTransaction, Rule rule) {
            long childFee = childTransaction.getFee();
            long proportionalFeeFQT = Convert.longValueExact(BigInteger.valueOf(childFee).multiply(Constants.ONE_FXT_BIG_INTEGER)
                    .divide(rule.minRateNQTPerFXTBigInteger));
            int blockchainHeight = Nxt.getBlockchain().getHeight();
            long feeFQT = Math.max(proportionalFeeFQT, childTransaction.getMinimumFeeFQT(blockchainHeight));
            return rule.overpay(feeFQT);
        }

        @Override
        public String getName() {
            return "PROPORTIONAL_FEE";
        }

        @Override
        public void validateRule(Rule rule) {
            if (rule.minRateNQTPerFXT == 0) {
                throw new IllegalArgumentException("Division by zero: proportional fee calculator cannot be used with 0 rate");
            }
        }
    }

    /**
     * Bundling rule - transactions that match the filter and minimum rate of the rule are bundled. The fee paid by the
     * bundler for the transaction is calculated according to the feeCalculator of the rule. More than one rule can be
     * specified per bundler, the transaction is processed according to the first rule which accepts the transaction.
     */
    public static class Rule {
        protected final FeeCalculator feeCalculator;
        protected final List<Filter> filters;
        protected final long minRateNQTPerFXT;
        protected final BigInteger minRateNQTPerFXTBigInteger;
        protected final long overpayFQTPerFXT;
        protected final BigInteger overpayFQTPerFXTBigInteger;

        private Rule(long minRateNQTPerFXT, long overpayFQTPerFXT, FeeCalculator feeCalculator,
                     List<Filter> filters) {
            this.minRateNQTPerFXT = minRateNQTPerFXT;
            this.minRateNQTPerFXTBigInteger = BigInteger.valueOf(this.minRateNQTPerFXT);
            this.overpayFQTPerFXT = overpayFQTPerFXT;
            this.overpayFQTPerFXTBigInteger = BigInteger.valueOf(this.overpayFQTPerFXT);
            this.feeCalculator = feeCalculator;
            this.filters = filters;
        }

        protected long calculateFeeFQT(ChildTransactionImpl childTransaction) {
            return feeCalculator.calculateFeeFQT(childTransaction, this);
        }

        public final long getMinRateNQTPerFXT() {
            return minRateNQTPerFXT;
        }

        public final long getOverpayFQTPerFXT() {
            return overpayFQTPerFXT;
        }

        public List<Filter> getFilters() {
            return filters;
        }

        public FeeCalculator getFeeCalculator() {
            return feeCalculator;
        }

        protected boolean isAtomicChainAccepted(Bundler bundler, AtomicChain<ChildTransactionImpl> chain) {
            int blockchainHeight = Nxt.getBlockchain().getHeight();
            long minChildFeeFQT = chain.stream()
                    .mapToLong(t -> t.getMinimumFeeFQT(blockchainHeight)).reduce(0, Math::addExact);
            long childFee = chain.getTotalFee();
            BigInteger minFeeNQTMulOneFXT = minRateNQTPerFXTBigInteger.multiply(BigInteger.valueOf(minChildFeeFQT));
            if (BigInteger.valueOf(childFee).multiply(Constants.ONE_FXT_BIG_INTEGER).compareTo(minFeeNQTMulOneFXT) < 0) {
                //Logger.logDebugMessage("Bundler not bundling %s fee %d [NQT] lower than min required fee %d [NQT]",
                //        chain,
                //        BigInteger.valueOf(childFee),
                //        minFeeNQTMulOneFXT.divide(Constants.ONE_FXT_BIG_INTEGER));
                return false;
            }
            return filters.stream().allMatch(filter -> filter.ok(bundler, chain));
        }

        public long overpay(long feeFQT) {
            return Math.addExact(feeFQT, Convert.longValueExact(overpayFQTPerFXTBigInteger
                    .multiply(BigInteger.valueOf(feeFQT)).divide(Constants.ONE_FXT_BIG_INTEGER)));
        }
    }

    private static class BroadcastedFxtTransaction {
        private BroadcastedFxtTransaction(ChildBlockFxtTransaction tx) {
            id = tx.getId();
            expirationTime = tx.getExpiration();
            feeFQT = tx.getFee();
        }
        private final long id;
        private final int expirationTime;
        private final long feeFQT;
    }

    private static final short defaultChildBlockDeadline = (short)Nxt.getIntProperty("nxt.defaultChildBlockDeadline");
    private static final Filter bundlingFilter; //kept for backward compatibility
    private static final Map<String, Filter> availableBundlingFilters;
    private static final Map<String, FeeCalculator> availableFeeCalculators;
    static {
        String filterClass = Nxt.getStringProperty("nxt.bundlingFilter");
        try {
            if (filterClass != null) {
                bundlingFilter = Class.forName(filterClass).asSubclass(Filter.class).getDeclaredConstructor().newInstance();
                availableBundlingFilters = Collections.singletonMap(bundlingFilter.getName(), bundlingFilter);
                Logger.logInfoMessage("Enforced " + bundlingFilter.getName() + " bundling filter to all rules");
            } else {
                bundlingFilter = null;
                List<String> filterClasses = Nxt.getStringListProperty("nxt.availableBundlingFilters");
                Map<String, Filter> filters = new LinkedHashMap<>(filterClasses.size());
                for (String filterClassStr : filterClasses) {
                    Filter filter = Class.forName(filterClassStr).asSubclass(Filter.class).getDeclaredConstructor().newInstance();
                    Filter prevFilter = filters.put(filter.getName(), filter);
                    if (prevFilter != null) {
                        RuntimeException runtimeException = new RuntimeException("Bundling filters " +
                                prevFilter.getClass() + " and " + filter.getClass() + " have equal names");
                        Logger.logErrorMessage(runtimeException.getMessage());
                        throw runtimeException;
                    }
                }
                availableBundlingFilters = Collections.unmodifiableMap(filters);
            }

            Map<String, FeeCalculator> calculators = new LinkedHashMap<>();
            FeeCalculator calculator = new MinFeeCalculator();
            calculators.put(calculator.getName(), calculator);
            calculator = new ProportionalFeeCalculator();
            calculators.put(calculator.getName(), calculator);

            List<String> customCalculatorClasses = Nxt.getStringListProperty("nxt.customBundlingFeeCalculators");
            for (String calculatorClass : customCalculatorClasses) {
                calculator = Class.forName(calculatorClass).asSubclass(FeeCalculator.class).getDeclaredConstructor().newInstance();
                calculators.put(calculator.getName(), calculator);
            }
            availableFeeCalculators = Collections.unmodifiableMap(calculators);
        } catch (ReflectiveOperationException e) {
            Logger.logErrorMessage(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static final Map<ChildChain, Map<Long, Bundler>> bundlers = new ConcurrentHashMap<>();
    private static final TransactionProcessorImpl transactionProcessor = TransactionProcessorImpl.getInstance();
    private final Queue<BroadcastedFxtTransaction> broadcastedQueue = new ArrayDeque<>();

    public static Bundler getBundler(ChildChain childChain, long accountId) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        Map<Long, Bundler> childChainBundlers = bundlers.get(childChain);
        return childChainBundlers == null ? null : childChainBundlers.get(accountId);
    }

    public static Filter createBundlingFilter(String name, String parameter) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        Filter filter;
        if (Bundler.bundlingFilter != null) {
            if (name != null && !name.equals(Bundler.bundlingFilter.getName())) {
                throw new IllegalArgumentException("The enforced bundling filter is " + Bundler.bundlingFilter.getName() +
                        ". Either use this filter, or change the nxt.bundlingFilter property");
            }
            filter = Bundler.bundlingFilter;
        } else {
            filter = Bundler.availableBundlingFilters.get(name);
            if (filter == null) {
                throw new IllegalArgumentException("Unknown filter " + name);
            }
        }
        try {
            //Create new filter instance for every bundling rule to allow different parameter per rule
            filter = filter.getClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            Logger.logErrorMessage(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        filter.setParameter(parameter);
        return filter;
    }

    public static Rule createBundlingRule(long minRateNQTPerFXT, long overpayFQTPerFXT,
                                          String feeCalculatorName, List<Filter> filters) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        if (feeCalculatorName == null || feeCalculatorName.isEmpty()) {
            feeCalculatorName = MinFeeCalculator.NAME;
        }
        if (bundlingFilter != null) {
            if (filters.isEmpty()) {
                filters = Collections.singletonList(bundlingFilter);
            } else if (filters.size() != 1 || !filters.get(0).getClass().equals(Bundler.bundlingFilter.getClass())) {
                throw new IllegalArgumentException("The enforced bundling filter is " + Bundler.bundlingFilter.getName() +
                        ". Either use this filter, or change the nxt.bundlingFilter property");
            }
        }
        FeeCalculator feeCalculator = availableFeeCalculators.get(feeCalculatorName);
        if (feeCalculator == null) {
            throw new IllegalArgumentException("Unknown fee calculator " + feeCalculatorName);
        }
        Rule rule = new Rule(minRateNQTPerFXT, overpayFQTPerFXT, feeCalculator, filters);
        feeCalculator.validateRule(rule);
        return rule;
    }

    public static synchronized Bundler addOrChangeBundler(ChildChain childChain, byte[] privateKey,
                                                          long totalFeesLimitFQT, List<Rule> bundlingRules) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        Bundler bundler = new Bundler(childChain, privateKey, totalFeesLimitFQT, bundlingRules);
        bundler.runBundling();
        return bundler;
    }

    public static synchronized Bundler addBundlingRule(ChildChain childChain, byte[] privateKey, Rule rule) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        long accountId = Account.getId(Crypto.getPublicKey(privateKey));
        Bundler bundler = getBundler(childChain, accountId);
        if (bundler != null) {
            bundler.bundlingRules.add(rule);
            bundler.runBundling();
            return bundler;
        }
        return null;
    }

    /**
     * Enrich a prospective child block list with child transactions according
     * to the specified
     * <code>bundlingRules</code> and the <code>totalFeesLimitFQT</code> .
     * <p>
     * The <code>totalFeesLimitFQT</code> is internally adjusted by subtracting
     * the minimum required fee for each transaction already present in the
     * provided <code>childTransactions</code> list.
     *
     * @param childTransactions The list of child transactions that are currently
     *                          planned for inclusion in the child block.
     * @param childChain        The child chain from which transactions to be
     *                          added to the list.
     * @param timestamp         The timestamp of the prospective ChildBlock transaction
     * @param deadline          The deadline of the prospective ChildBlock transaction
     * @param totalFeesLimitFQT Limit on the fee that the child block transactions
     *                          can consume
     * @param bundlingRules     Bundling rules for filtering or prioritizing the
     *                          additional transactions
     * @return The minimum fee the ChildBlock transaction must pay
     * @throws nxt.NxtException.NotValidException If total fee is not enough to
     *                          cover the minimum fee for the existing transactions
     */
    public static long enrichChildBlockList(List<ChildTransaction> childTransactions,
                                            ChildChain childChain, int timestamp, short deadline,
                                            long totalFeesLimitFQT, List<Rule> bundlingRules) throws NxtException.NotValidException {
        long calculatedFeeFQT = childTransactions.stream()
                .mapToLong(Transaction::getMinimumFeeFQT)
                .sum();
        totalFeesLimitFQT -= calculatedFeeFQT;
        if (totalFeesLimitFQT < 0) {
            throw new NxtException.NotValidException("Total fee not enough to cover the minimum fee for the existing transactions");
        }
        Bundler tempBundler = new Bundler(childChain, totalFeesLimitFQT, bundlingRules);
        List<AtomicChain<ChildTransactionImpl>> orderedList = tempBundler.getBundlableUnconfirmedTransactions(timestamp, deadline);
        orderedList.removeIf(c -> c.stream().anyMatch(childTransactions::contains));
        FilterResult filterResult = tempBundler.filterAndCollectFee(orderedList, childTransactions);
        calculatedFeeFQT = Math.addExact(calculatedFeeFQT, filterResult.feeFQT);
        return calculatedFeeFQT;
    }

    public static List<Bundler> getAllBundlers() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        List<Bundler> allBundlers = new ArrayList<>();
        bundlers.values().forEach(childChainBundlers -> allBundlers.addAll(childChainBundlers.values()));
        return allBundlers;
    }

    public static List<Bundler> getChildChainBundlers(ChildChain childChain) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        Map<Long, Bundler> childChainBundlers = bundlers.get(childChain);
        return childChainBundlers == null ? Collections.emptyList() : new ArrayList<>(childChainBundlers.values());
    }

    public static List<Bundler> getAccountBundlers(long accountId) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        List<Bundler> accountBundlers = new ArrayList<>();
        bundlers.values().forEach(childChainBundlers -> {
            Bundler bundler = childChainBundlers.get(accountId);
            if (bundler != null) {
                accountBundlers.add(bundler);
            }
        });
        return accountBundlers;
    }

    public static List<BundlerRate> getBundlerRates() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        if (bundlingFilter != null) {
            // do not advertise rates when using a custom filter
            return Collections.emptyList();
        }
        List<BundlerRate> rates = new ArrayList<>();
        getAllBundlers().forEach(bundler -> {
            BundlerRate bundlerRate = bundler.getBundlerRate();
            if (bundlerRate != null && bundlerRate.getChain().isEnabled()) {
                rates.add(bundlerRate);
            }
        });
        return rates;
    }

    public static Bundler stopBundler(ChildChain childChain, long accountId) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        Map<Long, Bundler> childChainBundlers = bundlers.get(childChain);
        return childChainBundlers == null ? null : childChainBundlers.remove(accountId);
    }

    public static void stopAccountBundlers(long accountId) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        bundlers.values().forEach(childChainBundlers -> childChainBundlers.remove(accountId));
    }

    public static void stopChildChainBundlers(ChildChain childChain) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        bundlers.remove(childChain);
    }

    public static void stopAllBundlers() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        bundlers.clear();
    }

    public static Collection<Filter> getAvailableFilters() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        return Collections.unmodifiableCollection(availableBundlingFilters.values());
    }

    public static Collection<FeeCalculator> getAvailableFeeCalculators() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("bundling"));
        }
        return Collections.unmodifiableCollection(availableFeeCalculators.values());
    }

    public static void init() {}

    static {
        transactionProcessor.addListener(transactions -> bundlers.values().forEach(chainBundlers -> chainBundlers.values().forEach(bundler -> {
            boolean hasChildChainTransactions = false;
            for (Transaction transaction : transactions) {
                if (transaction.getChain() == bundler.childChain) {
                    hasChildChainTransactions = true;
                    break;
                }
            }
            if (hasChildChainTransactions) {
                bundler.runBundling();
            }
        })), TransactionProcessor.Event.ADDED_UNCONFIRMED_TRANSACTIONS);

        //use the BLOCK_PUSHED event to not start new thread for this
        Nxt.getBlockchainProcessor().addListener(block -> {
                if (!Nxt.getBlockchainProcessor().isDownloading()) {
                    bundlers.values().forEach(chainBundlers ->
                            chainBundlers.values().forEach(bundler -> bundler.restoreFeesFromExpiredTransactions(block)));
                }
            }, BlockchainProcessor.Event.BLOCK_PUSHED);
    }

    private final ChildChain childChain;
    private final byte[] privateKey;
    private final byte[] publicKey;
    private final long accountId;

    private final long totalFeesLimitFQT;

    private final List<Rule> bundlingRules;

    private final AtomicLong currentTotalFeesFQT = new AtomicLong();
    private final AtomicLong confirmedTotalFeesFQT = new AtomicLong();

    private Bundler(ChildChain childChain, long totalFeesLimitFQT, List<Rule> bundlingRules) {
        this(childChain, null, totalFeesLimitFQT, bundlingRules);
    }

    private Bundler(ChildChain childChain, byte[] privateKey, long totalFeesLimitFQT, List<Rule> bundlingRules) {
        this.childChain = childChain;
        this.privateKey = privateKey;
        this.totalFeesLimitFQT = totalFeesLimitFQT;
        this.bundlingRules = new ArrayList<>(bundlingRules);
        if (privateKey != null) {
            this.publicKey = Crypto.getPublicKey(privateKey);
            this.accountId = Account.getId(publicKey);
            Map<Long, Bundler> chainBundlers = bundlers.computeIfAbsent(childChain, k -> new ConcurrentHashMap<>());
            chainBundlers.put(accountId, this);
        } else {
            this.publicKey = null;
            this.accountId = 0;
        }
    }

    public final ChildChain getChildChain() {
        return childChain;
    }

    public final byte[] getPublicKey() {
        return publicKey;
    }

    public final long getAccountId() {
        return accountId;
    }

    public final long getTotalFeesLimitFQT() {
        return totalFeesLimitFQT;
    }

    public final long getCurrentTotalFeesFQT() {
        return currentTotalFeesFQT.get();
    }

    public final long getConfirmedTotalFeesFQT() {
        return confirmedTotalFeesFQT.get();
    }


    public List<Rule> getBundlingRules() {
        return Collections.unmodifiableList(bundlingRules);
    }

    /**
     * @return Minimum rate among unfiltered rules, <code>null</code> if all rules are filtered
     */
    public final BundlerRate getBundlerRate() {
        long minPublicRate = Long.MAX_VALUE;
        for (Rule r : bundlingRules) {
            if (r.filters.isEmpty()) {
                minPublicRate = Math.min(minPublicRate, r.minRateNQTPerFXT);
            }
        }
        if (minPublicRate != Long.MAX_VALUE) {
            return new BundlerRate(childChain, minPublicRate,
                    (totalFeesLimitFQT != 0 ? totalFeesLimitFQT - currentTotalFeesFQT.get() : Long.MAX_VALUE), privateKey);
        } else {
            return null;
        }
    }

    private void runBundling() {
        BlockchainImpl.getInstance().writeLock();
        try {
            int timestamp = Nxt.getEpochTime();
            List<ChildBlockFxtTransaction> childBlockFxtTransactions = new ArrayList<>();
            List<AtomicChain<ChildTransactionImpl>> orderedChains = getBundlableUnconfirmedTransactions(timestamp, defaultChildBlockDeadline);

            boolean addMoreChildBlockTransactions = true;
            while (addMoreChildBlockTransactions && !orderedChains.isEmpty()) {
                List<ChildTransaction> childTransactions = new ArrayList<>();
                FilterResult filterResult = filterAndCollectFee(orderedChains, childTransactions);
                addMoreChildBlockTransactions = filterResult.isOutputFull;
                if (childTransactions.size() > 0) {
                    if (filterResult.feeFQT > FxtChain.FXT.getBalanceHome().getBalance(accountId).getUnconfirmedBalance()) {
                        Logger.logInfoMessage("Bundler account " + Long.toUnsignedString(accountId)
                                + " does not have sufficient balance to cover total Ardor fees " + filterResult.feeFQT);
                    } else if (!hasBetterChildBlockFxtTransaction(childTransactions, filterResult.feeFQT)) {
                        try {
                            ChildBlockFxtTransaction childBlockFxtTransaction = bundle(childTransactions, filterResult.feeFQT, timestamp);
                            currentTotalFeesFQT.addAndGet(filterResult.feeFQT);
                            synchronized (broadcastedQueue) {
                                broadcastedQueue.offer(new BroadcastedFxtTransaction(childBlockFxtTransaction));
                            }
                            childBlockFxtTransactions.add(childBlockFxtTransaction);
                        } catch (NxtException.NotCurrentlyValidException e) {
                            Logger.logDebugMessage(e.getMessage(), e);
                        } catch (NxtException.ValidationException e) {
                            Logger.logInfoMessage(e.getMessage(), e);
                        }
                    }
                }
            }

            childBlockFxtTransactions.forEach(childBlockFxtTransaction -> {
                try {
                    transactionProcessor.broadcast(childBlockFxtTransaction);
                } catch (NxtException.ValidationException e) {
                    Logger.logErrorMessage(e.getMessage(), e);
                }
            });
        } finally {
            BlockchainImpl.getInstance().writeUnlock();
        }
    }

    private List<AtomicChain<ChildTransactionImpl>> getBundlableUnconfirmedTransactions(int timestamp, short deadline) {
        AtomicChainsSet<ChildTransactionImpl> atomicChains = new AtomicChainsSet<>(
                    Comparator.comparingLong(AtomicChain<ChildTransactionImpl>::getFeePerByte)
                            .thenComparing(AtomicChain.FULL_HASH_COMPARATOR));
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                TransactionProcessorImpl.getInstance().getUnconfirmedChildTransactions(childChain),
                transaction -> transaction.getTransaction().hasAllReferencedTransactions(transaction.getTimestamp(), 0))) {
            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                ChildTransactionImpl childTransaction = (ChildTransactionImpl) unconfirmedTransaction.getTransaction();
                if (childTransaction.getExpiration() < timestamp + 60 * deadline || childTransaction.getTimestamp() > timestamp) {
                    continue;
                }
                atomicChains.add(childTransaction);
            }
        }
        return atomicChains.getCompleteChains();
    }

    private static class FilterResult {
        /**
         * True if the output list exceeds the MAX_NUMBER_OF_CHILD_TRANSACTIONS
         * or the payloadSize exceeds MAX_CHILDBLOCK_PAYLOAD_LENGTH
         */
        boolean isOutputFull;

        /**
         * The total fee paid for the transactions that are accepted by the filter
         * routine
         */
        long feeFQT;
    }

    /**
     * Iterates the transactions in the orderedInputList and moves the ones that are
     * accepted by the bundler rules to the outputList until the output is full
     * by count or payload size. The fee paid for each transaction is accumulated
     * in the totalFeeFQT field of the result. Transactions for which totalFeeFQT
     * would exceed the totalFeesLimitFQT are filtered out.
     * <p>
     * Transactions accepted by preceding bundling rules are prioritized over
     * ones accepted by subsequent rules no matter their position in
     * orderedInputList.
     *
     * @param orderedInputList The input list. Should be ordered according to the
     *                         transactions priority, usually the fee per byte they pay.
     * @param outputList      The output list. May not be empty, the payloadLength
     *                        will be calculated accordingly
     * @return A {@link FilterResult} instance
     */
    private FilterResult filterAndCollectFee(List<AtomicChain<ChildTransactionImpl>> orderedInputList,
                                             List<ChildTransaction> outputList) {
        FilterResult result = new FilterResult();
        int payloadLength = outputList.stream().mapToInt(Transaction::getFullSize).sum();
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        for (Rule bundlingRule : bundlingRules) {
            Iterator<AtomicChain<ChildTransactionImpl>> it = orderedInputList.iterator();
            while (it.hasNext()) {
                AtomicChain<ChildTransactionImpl> chain = it.next();

                int childFullSize = chain.getFullSize();
                if (payloadLength + childFullSize > Constants.MAX_CHILDBLOCK_PAYLOAD_LENGTH) {
                    continue;
                }
                if (outputList.size() + chain.size() > Constants.MAX_NUMBER_OF_CHILD_TRANSACTIONS) {
                    continue;
                }
                if (!bundlingRule.isAtomicChainAccepted(this, chain)) {
                    continue;
                }
                long feeFQT = chain.stream()
                        .mapToLong(bundlingRule::calculateFeeFQT).reduce(0, Math::addExact);
                if (Math.addExact(currentTotalFeesFQT.get(), Math.addExact(result.feeFQT, feeFQT)) > totalFeesLimitFQT && totalFeesLimitFQT > 0) {
                    Logger.logDebugMessage("Bundler " + Long.toUnsignedString(accountId) + " will exceed total fees limit, not bundling");
                    continue;
                }
                if (chain.stream().anyMatch(t -> t.attachmentIsDuplicate(duplicates, true))) {
                    continue;
                }
                it.remove();
                outputList.addAll(chain);
                result.feeFQT = Math.addExact(result.feeFQT, feeFQT);
                payloadLength += childFullSize;
                if (outputList.size() == Constants.MAX_NUMBER_OF_CHILD_TRANSACTIONS
                        || payloadLength == Constants.MAX_CHILDBLOCK_PAYLOAD_LENGTH) {
                    result.isOutputFull = true;
                    return result;
                }
            }
        }
        return result;
    }


    private void restoreFeesFromExpiredTransactions(Block lastBlock) {
        //we stay on the safe side and restore the currentTotalFeesFQT only after the created transaction
        // has expired, or else it could become valid due to blockchain reorganization
        int expirationThreshold = lastBlock.getTimestamp() + Constants.MAX_TIMEDRIFT;
        while (true) {
            BroadcastedFxtTransaction transaction;
            synchronized (broadcastedQueue) {
                transaction = broadcastedQueue.peek();
                if (transaction == null || expirationThreshold <= transaction.expirationTime) {
                    break;
                }
                broadcastedQueue.poll();
            }

            if (Nxt.getBlockchain().getFxtTransaction(transaction.id) == null) {
                currentTotalFeesFQT.addAndGet(-transaction.feeFQT);
            } else {
                confirmedTotalFeesFQT.addAndGet(transaction.feeFQT);
            }
        }
    }

    private ChildBlockFxtTransaction bundle(List<ChildTransaction> childTransactions, long feeFQT, int timestamp) throws NxtException.ValidationException {
        FxtTransaction.Builder builder = FxtChain.FXT.newTransactionBuilder(publicKey, 0, feeFQT, defaultChildBlockDeadline,
                new ChildBlockAttachment(childTransactions));
        builder.timestamp(timestamp);
        ChildBlockFxtTransaction childBlockFxtTransaction = (ChildBlockFxtTransaction)builder.build(privateKey);
        childBlockFxtTransaction.validate();
        /*
        Logger.logDebugMessage("Created ChildBlockFxtTransaction: " + Long.toUnsignedString(childBlockFxtTransaction.getId()) + " "
                + JSON.toJSONString(childBlockFxtTransaction.getJSONObject()));
        */
        return childBlockFxtTransaction;
    }

    private boolean hasBetterChildBlockFxtTransaction(List<ChildTransaction> childTransactions, long fee) {
        try (DbIterator<UnconfirmedTransaction> unconfirmedTransactions = transactionProcessor.getUnconfirmedFxtTransactions()) {
            while (unconfirmedTransactions.hasNext()) {
                FxtTransaction fxtTransaction = (FxtTransaction)unconfirmedTransactions.next().getTransaction();
                if (fxtTransaction.getType() == ChildBlockFxtTransactionType.INSTANCE
                        && ((ChildBlockFxtTransaction)fxtTransaction).getChildChain() == childChain) {
                    if (fxtTransaction.getFee() >= fee) {
                        try {
                            fxtTransaction.validate();
                        } catch (NxtException.ValidationException e) {
                            continue;
                        }
                        if (((ChildBlockFxtTransactionImpl)fxtTransaction).containsAll(childTransactions)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

}
