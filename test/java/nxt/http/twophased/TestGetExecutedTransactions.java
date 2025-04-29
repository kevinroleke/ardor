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

package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.Constants;
import nxt.Nxt;
import nxt.Tester;
import nxt.account.PaymentFxtTransactionType;
import nxt.account.PaymentTransactionType;
import nxt.blockchain.FxtTransactionType;
import nxt.blockchain.TransactionType;
import nxt.http.accountControl.ACTestUtils;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.GetExecutedTransactionsCall;
import nxt.http.callers.SendMoneyCall;
import nxt.util.JSONAssert;
import nxt.voting.VoteWeighting;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static nxt.blockchain.ChildChain.IGNIS;
import static nxt.blockchain.FxtChain.FXT;

public class TestGetExecutedTransactions extends BlockchainTest {
    @Test
    public void testExecutedAtHeight() {
        long amount = IGNIS.ONE_COIN * 3;

        Set<String> expectedTransactionIds = new TreeSet<>();
        String approveTransactionId = null;
        List<String> transactionsToApprove = new ArrayList<>();

        int queriedHeight = Nxt.getBlockchain().getHeight() + 10;
        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                .phasingWhitelisted(CHUCK.getStrId())
                .phasingQuorum(1);

        for (int i = 0; i < 20; i++) {
            int height = Nxt.getBlockchain().getHeight();
            int finishHeight = height + 10;
            sendMoneyCall.phasingFinishHeight(finishHeight).amountNQT(amount + i);
            String fullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");
            if (height < queriedHeight - 2 && queriedHeight < finishHeight) {
                expectedTransactionIds.add(fullHash);
                transactionsToApprove.add(IGNIS.getId() + ":" + fullHash);
            }

            SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId()).secretPhrase(ALICE.getSecretPhrase()).
                    recipient(BOB.getStrId()).feeNQT(IGNIS.ONE_COIN);
            IntStream.range(0, 10).forEach(j -> {
                builder.amountNQT(amount + j);
                String fullHash1 = new JSONAssert(builder.call()).str("fullHash");
                if (height + 1 == queriedHeight) {
                    expectedTransactionIds.add(fullHash1);
                }
            });

            if (height + 1 == queriedHeight) {
                ApproveTransactionCall approveBuilder = ACTestUtils.approveBuilder(transactionsToApprove.get(0), CHUCK, null);
                approveBuilder.phasedTransaction(transactionsToApprove.toArray(new String[0]));
                approveTransactionId = new JSONAssert(approveBuilder.call()).str("fullHash");
            }

            generateBlock();
        }

        Set<String> actualTransactionIds = getExecutedTransactionsIds(queriedHeight, BOB, PaymentTransactionType.ORDINARY);

        Assert.assertEquals(expectedTransactionIds, actualTransactionIds);

        actualTransactionIds = getExecutedTransactionsIds(queriedHeight, null, PaymentTransactionType.ORDINARY);

        Assert.assertEquals(expectedTransactionIds, actualTransactionIds);

        actualTransactionIds = getExecutedTransactionsIds(queriedHeight, null, null);

        TreeSet<String> paymentsAndApproveTransaction = new TreeSet<>(expectedTransactionIds);
        paymentsAndApproveTransaction.add(approveTransactionId);
        Assert.assertEquals(paymentsAndApproveTransaction, actualTransactionIds);

        GetExecutedTransactionsCall builder = executedTransactionsBuilder(BOB, null);
        builder.height(queriedHeight);
        builder.type(PaymentTransactionType.ORDINARY.getType());
        actualTransactionIds = getTransactionIds(new JSONAssert(builder.call()));

        Assert.assertEquals(expectedTransactionIds, actualTransactionIds);
    }

    @Test
    public void testConfirmed() {
        long amount = IGNIS.ONE_COIN * 3;

        GetExecutedTransactionsCall queryBuilder = executedTransactionsBuilder(BOB, PaymentTransactionType.ORDINARY);
        queryBuilder.numberOfConfirmations(0);
        //add the current transactions as expected
        Set<String> expectedTransactionIds = new TreeSet<>(getTransactionIds(new JSONAssert(queryBuilder.call())));

        int confirmations = 5;
        int queriedHeight = Nxt.getBlockchain().getHeight() + 20 - confirmations;
        SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .phased(true)
                .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                .recipient(BOB.getStrId())
                .amountNQT(amount)
                .phasingVotingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                .phasingWhitelisted(CHUCK.getStrId())
                .phasingQuorum(1);

        IntStream.range(0, 20).forEach(i -> {
            int height = Nxt.getBlockchain().getHeight();
            int finishHeight = height + 10;
            sendMoneyCall.phasingFinishHeight(finishHeight).amountNQT(amount + i);
            String phasedFullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");

            SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId()).secretPhrase(ALICE.getSecretPhrase()).
                    recipient(BOB.getStrId()).feeNQT(IGNIS.ONE_COIN);
            IntStream.range(0, 10).forEach(j -> {
                builder.amountNQT(amount + j);
                String fullHash = new JSONAssert(builder.call()).str("fullHash");
                if (height + 1 <= queriedHeight) {
                    expectedTransactionIds.add(fullHash);
                }
            });

            generateBlock();
            if (height + 2 <= queriedHeight) {
                expectedTransactionIds.add(phasedFullHash);
            }

            ACTestUtils.approve(phasedFullHash, CHUCK, null);
        });

        queryBuilder = executedTransactionsBuilder(BOB, PaymentTransactionType.ORDINARY);
        queryBuilder.numberOfConfirmations(confirmations);

        JSONAssert result = new JSONAssert(queryBuilder.call());

        Assert.assertEquals(expectedTransactionIds, getTransactionIds(result));

        queryBuilder = executedTransactionsBuilder(null, PaymentTransactionType.ORDINARY);
        queryBuilder.numberOfConfirmations(confirmations);

        result = new JSONAssert(queryBuilder.call());
        Assert.assertTrue(result.str("errorDescription").startsWith("At least one of"));
    }

    @Test
    public void testOrder() {
        long amount = IGNIS.ONE_COIN * 3;

        List<String> phasedFullHashes = new ArrayList<>();
        LinkedList<String> expectedTransactionIds = new LinkedList<>();

        IntStream.range(0, 10).forEach(i -> {
            int height = Nxt.getBlockchain().getHeight();
            int finishHeight = height + 50;
            SendMoneyCall sendMoneyCall = SendMoneyCall.create(IGNIS.getId())
                    .secretPhrase(ALICE.getSecretPhrase())
                    .feeNQT(IGNIS.ONE_COIN)
                    .phased(true)
                    .phasingFinishHeight(Nxt.getBlockchain().getHeight() + 5)
                    .recipient(BOB.getStrId())
                    .amountNQT(amount)
                    .phasingVotingModel(VoteWeighting.VotingModel.ACCOUNT.getCode())
                    .phasingWhitelisted(CHUCK.getStrId())
                    .phasingQuorum(1);

            sendMoneyCall.phasingFinishHeight(finishHeight).amountNQT(amount + i);
            String phasedFullHash = new JSONAssert(sendMoneyCall.call()).str("fullHash");
            phasedFullHashes.add(phasedFullHash);
        });
        generateBlock();

        //interleave non-phased and executed phased transactions
        IntStream.range(0, 10).forEach(j -> {

            SendMoneyCall builder = SendMoneyCall.create(IGNIS.getId()).secretPhrase(ALICE.getSecretPhrase()).
                    recipient(BOB.getStrId()).feeNQT(IGNIS.ONE_COIN);
            builder.amountNQT(amount + j);
            String fullHash = new JSONAssert(builder.call()).str("fullHash");
            expectedTransactionIds.addFirst(fullHash);
            generateBlock();

            String phasedFullHash = phasedFullHashes.get(j);
            ACTestUtils.approve(phasedFullHash, CHUCK, null);
            expectedTransactionIds.addFirst(phasedFullHash);
            generateBlock();
        });

        //result must be ordered by execution height (descending), not acceptance height
        GetExecutedTransactionsCall queryBuilder = executedTransactionsBuilder(BOB, PaymentTransactionType.ORDINARY);
        queryBuilder.lastIndex(19); //get 20 results
        JSONAssert result = new JSONAssert(queryBuilder.call());

        Assert.assertEquals(expectedTransactionIds, getOrderedTransactionIds(result));
    }

    @Test
    public void testFXT() {
        long amount = FXT.ONE_COIN * 3;
        int queriedHeight = Nxt.getBlockchain().getHeight() + 3;
        List<String> expectedTransactionIds = new ArrayList<>();

        IntStream.range(0, 9).forEach(i -> {
            int height = Nxt.getBlockchain().getHeight();
            SendMoneyCall builder = SendMoneyCall.create(FXT.getId()).secretPhrase(ALICE.getSecretPhrase()).
                    recipient(BOB.getStrId()).feeNQT(FXT.ONE_COIN * 10);
            IntStream.range(0, Math.min(15, Constants.MAX_NUMBER_OF_FXT_TRANSACTIONS)).forEach(j -> {
                long time = System.currentTimeMillis();
                builder.amountNQT(amount + j);
                String fullHash = new JSONAssert(builder.call()).str("fullHash");
                if (height + 1 == queriedHeight) {
                    expectedTransactionIds.add(fullHash);
                }

                //ensure different arrival time of two subsequent transactions - since the order of expectedTransactionIds
                //should match the transaction_index in order for the pagination test to be valid
                while (System.currentTimeMillis() == time) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            generateBlock();
        });

        SortedSet<String> actualTransactionIds = getExecutedTransactionsIds(queriedHeight, BOB, PaymentFxtTransactionType.ORDINARY);

        Assert.assertEquals(new TreeSet<>(expectedTransactionIds), actualTransactionIds);

        GetExecutedTransactionsCall builder = executedTransactionsBuilder(BOB, PaymentFxtTransactionType.ORDINARY);
        builder.height(queriedHeight);

        int first = 3;
        int last = 4;

        builder.firstIndex(first);
        builder.lastIndex(expectedTransactionIds.size() - last);
        actualTransactionIds = getTransactionIds(new JSONAssert(builder.call()));

        List<String> expectedView = expectedTransactionIds.subList(0, expectedTransactionIds.size() - first);
        expectedView = expectedView.subList(last - 1, expectedView.size());

        Assert.assertEquals(new TreeSet<>(expectedView), actualTransactionIds);
    }

    private SortedSet<String> getExecutedTransactionsIds(int height, Tester recipient, TransactionType transactionType) {
        GetExecutedTransactionsCall builder = executedTransactionsBuilder(recipient, transactionType);
        builder.height(height);
        JSONAssert result = new JSONAssert(builder.call());

        return getTransactionIds(result);
    }

    private SortedSet<String> getTransactionIds(JSONAssert result) {
        return result.array("transactions", JSONObject.class).stream().
                map(t -> new JSONAssert(t).str("fullHash")).collect(Collectors.toCollection(TreeSet::new));
    }

    private List<String> getOrderedTransactionIds(JSONAssert result) {
        return result.array("transactions", JSONObject.class).stream().
                map(t -> new JSONAssert(t).str("fullHash")).collect(Collectors.toCollection(ArrayList::new));
    }

    private GetExecutedTransactionsCall executedTransactionsBuilder(Tester recipient, TransactionType transactionType) {
        GetExecutedTransactionsCall builder = GetExecutedTransactionsCall.create(transactionType instanceof FxtTransactionType ? FXT.getId() : IGNIS.getId());
        if (transactionType != null) {
            builder.type(transactionType.getType()).subtype(transactionType.getSubtype());
        }
        if (recipient != null) {
            builder.recipient(recipient.getStrId());
        }
        return builder;
    }
}
