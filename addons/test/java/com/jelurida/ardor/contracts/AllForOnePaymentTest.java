/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package com.jelurida.ardor.contracts;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.blockchain.ChildTransaction;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TriggerContractByHeightCall;
import nxt.http.callers.TriggerContractByRequestCall;
import nxt.http.callers.TriggerContractByTransactionCall;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.util.Convert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static nxt.blockchain.ChildChain.IGNIS;

public class AllForOnePaymentTest extends AbstractContractTest {

    @Test
    public void randomProbabilisticDistribution() {
        Random r = new Random(0);
        DistributedRandomNumberGenerator distribution = new DistributedRandomNumberGenerator();
        Map<String, Long> collect = new HashMap<>();
        collect.put(ALICE.getStrId(), 300L);
        collect.put(BOB.getStrId(), 500L);
        collect.put(CHUCK.getStrId(), 200L);
        Map<String, Long> hitMap = new HashMap<>();
        hitMap.put(ALICE.getStrId(), 0L);
        hitMap.put(BOB.getStrId(), 0L);
        hitMap.put(CHUCK.getStrId(), 0L);
        for (int i = 0; i < 100000; i++) {
            String account = distribution.processInvocationImpl(r.nextDouble() ,collect);
            long value = hitMap.get(account);
            hitMap.put(account, ++value);
        }
        Assert.assertEquals(29927L, (long)hitMap.get(ALICE.getStrId()));
        Assert.assertEquals(49893L, (long)hitMap.get(BOB.getStrId()));
        Assert.assertEquals(20180L, (long)hitMap.get(CHUCK.getStrId()));
    }

    @Test
    public void allForOnePayment() {
        JO setupParams = new JO();
        setupParams.put("frequency", "Value from properties will override this setting");
        String contractName = AllForOnePayment.class.getSimpleName();
        ContractTestHelper.deployContract(AllForOnePayment.class, setupParams, false);
        ContractTestHelper.deployContract(DistributedRandomNumberGenerator.class, null, true);
        JO messageJson = new JO();
        messageJson.put("seed", ContractTestHelper.getRandomSeed(System.identityHashCode(messageJson))); // Specify a random seed
        String message = messageJson.toJSONString();
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                recipient(ALICE.getRsAccount()).
                amountNQT(500 * IGNIS.ONE_COIN).
                encryptedMessageIsPrunable(true).
                messageToEncrypt(message).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();

        messageJson = new JO();
        messageJson.put("seed", ContractTestHelper.getRandomSeed(System.identityHashCode(messageJson))); // Specify a random seed
        message = messageJson.toJSONString();
        response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(CHUCK.getSecretPhrase()).
                recipient(ALICE.getRsAccount()).
                amountNQT(300 * IGNIS.ONE_COIN).
                encryptedMessageIsPrunable(true).
                messageToEncrypt(message).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        // Same account pays twice in different blocks
        response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(DAVE.getSecretPhrase()).
                recipient(ALICE.getRsAccount()).
                amountNQT(100 * IGNIS.ONE_COIN).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();

        response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(DAVE.getSecretPhrase()).
                recipient(ALICE.getRsAccount()).
                amountNQT(100 * IGNIS.ONE_COIN).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();

        // Check the state of the contract before making a payment
        response = TriggerContractByRequestCall.create().contractName(contractName).callNoError();
        JA payments = response.getArray("payments");
        Assert.assertEquals(4, payments.size());
        Assert.assertEquals(100000000000L, response.getLong("paymentAmountNQT"));

        generateBlockWithDescription("Now the distribution takes place (height 6)");
        generateBlockWithDescription("And now the reward transaction is processed");

        List<Long> participants = new ArrayList<>(Arrays.asList(ALICE.getId(), BOB.getId(), CHUCK.getId(), DAVE.getId()));
        ChildTransaction childTransaction = testAndGetLastChildTransaction(2, 0, 0, a -> a == 99998000000L, 2000000L, ALICE, null, null);
        Assert.assertTrue(participants.contains(childTransaction.getRecipientId()));
        childTransaction.getAppendages().stream()
                .filter(PrunablePlainMessageAppendix.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("At least 1 PrunablePlainMessageAppendix expected"));

        // Trigger the contract based on the transaction it just submitted
        response = TriggerContractByTransactionCall.create(IGNIS.getId()).
                triggerFullHash(Convert.toHexString(childTransaction.getFullHash())).
                apply(true).
                validate(true).
                call();
        Assert.assertTrue(response.getString("errorDescription").startsWith("Cannot approve transaction, validatorSecretPhrase not specified"));

        // Trigger the contract based on a specific height without actually submitting the transactions
        response = TriggerContractByHeightCall.create().
                contractName(contractName).
                height(6).
                callNoError();
        JA transactions = response.getArray("transactions");
        Assert.assertEquals(1, transactions.size());
        JO transactionJson = transactions.get(0).getJo("transactionJSON");
        Assert.assertEquals(99998000000L, transactionJson.getLong("amountNQT"));
        Assert.assertEquals(2000000L, transactionJson.getLong("feeNQT"));
        Assert.assertEquals(ALICE.getAccount().getId(), transactionJson.getEntityId("sender"));
        long recipient = transactionJson.getEntityId("recipient");
        Assert.assertTrue(participants.contains(recipient));
    }

}
