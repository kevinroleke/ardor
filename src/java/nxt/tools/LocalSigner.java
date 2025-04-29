/*
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

package nxt.tools;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.addons.JO;
import nxt.blockchain.Attachment;
import nxt.blockchain.ChainTransactionId;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionImpl;
import nxt.crypto.Crypto;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.CalculateFeeCall;
import nxt.http.callers.GetBlockchainStatusCall;
import nxt.http.callers.GetBundlerRatesCall;
import nxt.http.callers.GetECBlockCall;
import nxt.http.callers.GetPhasingOnlyControlCall;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;
import nxt.util.security.BlockchainPermission;
import nxt.voting.PhasingAppendix;
import nxt.voting.PhasingParams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.List;

public class LocalSigner {

    /**
     * Helper for client applications to sign transactions without submitting the passphrase to a remote node
     * This utility method, signs a transaction locally without exposing the secretPhrase to a remote node
     * @param childChain child chain
     * @param recipientId recipient
     * @param attachment additional information specific to the transaction type
     * @param privateKey private key
     * @param feeNQT fee, specify -1 to calculate the best bundling fee
     * @param feeRateNQTPerFXT rate between child fee and parent fee
     * @param minBundlerBalanceFXT only consider bundlers with at list this bundling balance
     * @param referencedTransaction transaction to reference
     * @param url optional remote node to which to submit the transaction
     * @return the result of broadcasting the transaction
     */
    public static JO signAndBroadcast(ChildChain childChain, long recipientId, Attachment attachment, byte[] privateKey,
                                      long feeNQT, long feeRateNQTPerFXT, long minBundlerBalanceFXT, ChainTransactionId referencedTransaction, URL url) {
        Transaction.Builder builder = createBuilder(childChain, recipientId, attachment, privateKey, feeNQT, feeRateNQTPerFXT, minBundlerBalanceFXT, referencedTransaction, url);
        return signAndBroadcast(builder, privateKey, url);
    }

    public static JO signAndBroadcast(Transaction.Builder builder, byte[] privateKey, URL url) {
        Transaction transaction;
        try {
            transaction = builder.build(privateKey);
        } catch (NxtException.NotValidException e) {
            throw new IllegalStateException(e);
        }
        BroadcastTransactionCall broadcastTransactionCall = BroadcastTransactionCall.create().transactionBytes(transaction.getBytes()).remote(url);
        if (transaction.getPrunableAttachmentJSON() != null) {
            broadcastTransactionCall = broadcastTransactionCall.prunableAttachmentJSON(transaction.getPrunableAttachmentJSON().toJSONString());
        }
        return broadcastTransactionCall.call();
    }

    public static Transaction.Builder createBuilder(ChildChain childChain, long recipientId, Attachment attachment,
                                                    byte[] privateKey, long feeNQT, long feeRateNQTPerFXT,
                                                    long minBundlerBalanceFXT, ChainTransactionId referencedTransaction, URL url) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new BlockchainPermission("tools"));
        }

        byte[] publicKey = Crypto.getPublicKey(privateKey);

        // Obtain existing block to reference
        GetECBlockCall getECBlockCall = GetECBlockCall.create().remote(url);
        JO ecBlock = getECBlockCall.call();

        JO phasingControl = GetPhasingOnlyControlCall.create().account(Account.getId(publicKey)).remote(url).call();
        long phasingMaxFee = Long.MAX_VALUE;
        if (phasingControl.isExist("maxFees")) {
            String maxFeesStr = phasingControl.getJo("maxFees").getString(String.valueOf(childChain.getId()));
            if (maxFeesStr != null) {
                phasingMaxFee = Long.parseUnsignedLong(maxFeesStr);
            }
        }

        TransactionImpl.BuilderImpl builder;
        Transaction transaction;
        if (feeNQT == -1) {
            // Create the transaction to calculate transaction fee
            builder = childChain.newTransactionBuilder(publicKey, 0, -1, (short)15, attachment).recipientId(recipientId);
            setEcBlock(ecBlock, builder);
            setPhasing(phasingControl, builder, url);
            ((ChildTransactionImpl.BuilderImpl)builder).referencedTransaction(referencedTransaction);
            try {
                transaction = builder.build(privateKey);
            } catch (NxtException.NotValidException e) {
                throw new IllegalStateException(e);
            }
            CalculateFeeCall calculateFeeCall = CalculateFeeCall.create().transactionJSON(JSON.toJSONString(transaction.getJSONObject())).remote(url);
            final long minimumFeeFQT = calculateFeeCall.call().getLong("minimumFeeFQT");
            int counter = 0;
            while (feeRateNQTPerFXT == -1 && counter < 10) {
                List<JO> rates = GetBundlerRatesCall.create().minBundlerBalanceFXT(minBundlerBalanceFXT).remote(url).call().getArray("rates").objects();
                Long bestRate = rates.stream().filter(r -> r.getInt("chain") == childChain.getId()).filter(r -> r.getLong("currentFeeLimitFQT") > minimumFeeFQT).
                        map(r -> r.getLong("minRateNQTPerFXT")).sorted().findFirst().orElse(null);
                if (bestRate == null) {
                    counter++;
                    Logger.logInfoMessage("Fee not specified and best bundling fee cannot be determined, sleep and retry %d", counter);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    continue;
                }
                feeRateNQTPerFXT = bestRate;
            }
            if (counter == 10) {
                throw new IllegalStateException("Fee not specified and best bundling fee cannot be determined, retry later");
            }

            // Calculate the transaction fee and submit the transaction again
            feeNQT = BigDecimal.valueOf(minimumFeeFQT).multiply(BigDecimal.valueOf(feeRateNQTPerFXT)).divide(BigDecimal.valueOf(childChain.ONE_COIN), RoundingMode.HALF_EVEN).longValue();
        }
        if (feeNQT > phasingMaxFee) {
            throw new IllegalStateException("Fee exceeds the max fee set in Account Control. " +
                    "Provide as parameter fee below " + phasingMaxFee);
        }
        builder = childChain.newTransactionBuilder(publicKey, 0, feeNQT, (short)15, attachment);
        setEcBlock(ecBlock, builder);
        setPhasing(phasingControl, builder, url);
        if (attachment.getTransactionType().canHaveRecipient()) {
            builder.recipientId(recipientId);
        }
        ((ChildTransactionImpl.BuilderImpl)builder).referencedTransaction(referencedTransaction);
        return builder;
    }

    private static void setPhasing(JO phasingControl, TransactionImpl.BuilderImpl builder, URL url) {
        if (phasingControl.isExist("controlParams")) {
            PhasingParams phasingParams = new PhasingParams(phasingControl.getJo("controlParams").toJSONObject());
            int maxDuration = phasingControl.getInt("maxDuration", Constants.MAX_PHASING_DURATION);
            int currentHeight = GetBlockchainStatusCall.create().remote(url).call().getInt("numberOfBlocks") - 1;
            builder.appendix(new PhasingAppendix(currentHeight + maxDuration, phasingParams));
        }
    }

    private static void setEcBlock(JO ecBlock, TransactionImpl.BuilderImpl builder) {
        builder.timestamp(((Long) ecBlock.get("timestamp")).intValue())
                .ecBlockHeight(((Long) ecBlock.get("ecBlockHeight")).intValue())
                .ecBlockId(Convert.parseUnsignedLong((String) ecBlock.get("ecBlockId")));
    }
}
