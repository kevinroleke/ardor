/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2022 Jelurida IP B.V.
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

package nxt.http.shuffling;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.account.HoldingType;
import nxt.addons.JO;
import nxt.blockchain.Fee;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.GetShufflingCall;
import nxt.http.callers.GetShufflingParticipantsCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.IssueCurrencyCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.ShufflingCancelCall;
import nxt.http.callers.ShufflingCreateCall;
import nxt.http.callers.ShufflingProcessCall;
import nxt.http.callers.ShufflingRegisterCall;
import nxt.http.callers.ShufflingVerifyCall;
import nxt.http.callers.SignTransactionCall;
import nxt.http.callers.StartShufflerCall;
import nxt.http.callers.StopShufflerCall;
import nxt.http.callers.TransferAssetCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.util.Logger;

import static nxt.blockchain.ChildChain.IGNIS;

public class ShufflingUtil {

    public static final Tester ALICE_RECIPIENT = new Tester("oiketrdgfxyjqhwds");
    public static final Tester BOB_RECIPIENT = new Tester("5ehtrd9oijnkter");
    public static final Tester CHUCK_RECIPIENT = new Tester("sdfxbejytdgfqrwefsrd");
    public static final Tester DAVE_RECIPIENT = new Tester("gh-=e49rsiufzn4^");

    static final long defaultShufflingAmount = 1500000000;
    static final long defaultHoldingShufflingAmount = 40000;
    static long shufflingAsset;
    static long shufflingCurrency;
    static final int chainId = IGNIS.getId();
    static final long SHUFFLING_REGISTER_FEE = IGNIS.ONE_COIN/100;
    static final long SHUFFLING_PROCESSING_FEE = IGNIS.ONE_COIN/10 + Fee.NEW_ACCOUNT_FEE;
    static final long SHUFFLING_VERIFY_FEE = IGNIS.ONE_COIN/10;
    static final long SHUFFLING_TOTAL_FEE = 2 * SHUFFLING_REGISTER_FEE + SHUFFLING_PROCESSING_FEE;

    static JO create(Tester creator) {
        return create(creator, 4);
    }

    public static JO create(Tester creator, int participantCount) {
        JO response = ShufflingCreateCall.create(IGNIS.getId()).
                secretPhrase(creator.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                amount(defaultShufflingAmount).
                participantCount((byte) participantCount).
                registrationPeriod(10).
                callNoError();
        Logger.logMessage("shufflingCreateResponse: " + response.toJSONString());
        return response;
    }

    static JO createAssetShuffling(Tester creator) {
        JO response = IssueAssetCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .name("phased")
                .description("shuffling transaction testing")
                .quantityQNT(1000000)
                .decimals(2)
                .feeNQT(1000 * IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        shufflingAsset = Long.parseUnsignedLong(Tester.responseToStringId(response));
        BlockchainTest.generateBlock();
        response = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .recipient(BlockchainTest.BOB.getRsAccount())
                .asset(shufflingAsset)
                .quantityQNT(100000)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        Logger.logMessage("transferAssetResponse: " + response.toJSONString());
        response = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .recipient(BlockchainTest.CHUCK.getRsAccount())
                .asset(shufflingAsset)
                .quantityQNT(100000)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        Logger.logMessage("transferAssetResponse: " + response.toJSONString());
        response = TransferAssetCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .recipient(BlockchainTest.DAVE.getRsAccount())
                .asset(shufflingAsset)
                .quantityQNT(100000)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        Logger.logMessage("transferAssetResponse: " + response.toJSONString());
        BlockchainTest.generateBlock();
        response = ShufflingCreateCall.create(IGNIS.getId()).
                secretPhrase(creator.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                amount(defaultHoldingShufflingAmount).
                participantCount((byte) 4).
                registrationPeriod(10).
                holding(shufflingAsset).
                holdingType(HoldingType.ASSET.getCode()).
                callNoError();
        Logger.logMessage("shufflingCreateResponse: " + response.toJSONString());
        return response;
    }

    static JO createCurrencyShuffling(Tester creator) {
        JO response = IssueCurrencyCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .name("shfle")
                .code("SHFL")
                .description("phased transaction testing")
                .type(1)
                .initialSupplyQNT(10000000)
                .maxSupplyQNT(10000000)
                .decimals(2)
                .feeNQT(1000 * IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        shufflingCurrency = Long.parseUnsignedLong(Tester.responseToStringId(response));
        BlockchainTest.generateBlock();
        response = TransferCurrencyCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .recipient(BlockchainTest.BOB.getRsAccount())
                .currency(shufflingCurrency)
                .unitsQNT(100000)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        Logger.logMessage("transferCurrencyResponse: " + response.toJSONString());
        response = TransferCurrencyCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .recipient(BlockchainTest.CHUCK.getRsAccount())
                .currency(shufflingCurrency)
                .unitsQNT(100000)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        Logger.logMessage("transferCurrencyResponse: " + response.toJSONString());
        response = TransferCurrencyCall.create(IGNIS.getId())
                .secretPhrase(creator.getSecretPhrase())
                .recipient(BlockchainTest.DAVE.getRsAccount())
                .currency(shufflingCurrency)
                .unitsQNT(100000)
                .feeNQT(IGNIS.ONE_COIN)
                .deadline(1440)
                .callNoError();
        Logger.logMessage("transferCurrencyResponse: " + response.toJSONString());
        BlockchainTest.generateBlock();
        response = ShufflingCreateCall.create(IGNIS.getId()).
                secretPhrase(creator.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                amount(defaultHoldingShufflingAmount).
                participantCount((byte) 4).
                registrationPeriod(10).
                holding(shufflingCurrency).
                holdingType(HoldingType.CURRENCY.getCode()).
                callNoError();
        Logger.logMessage("shufflingCreateResponse: " + response.toJSONString());
        return response;
    }

    static JO register(String shufflingFullHash, Tester tester) {
        JO response = ShufflingRegisterCall.create(IGNIS.getId()).
                secretPhrase(tester.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                shufflingFullHash(shufflingFullHash).
                call();
        Logger.logMessage("shufflingRegisterResponse: " + response.toJSONString());
        return response;
    }

    public static JO getShuffling(String shufflingFullHash) {
        JO getShufflingResponse = GetShufflingCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                callNoError();
        Logger.logMessage("getShufflingResponse: " + getShufflingResponse.toJSONString());
        return getShufflingResponse;
    }

    public static JO getShufflingParticipants(String shufflingFullHash) {
        JO getParticipantsResponse = GetShufflingParticipantsCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                callNoError();
        Logger.logMessage("getShufflingParticipantsResponse: " + getParticipantsResponse.toJSONString());
        return getParticipantsResponse;
    }

    static JO process(String shufflingFullHash, Tester tester, Tester recipient) {
        return process(shufflingFullHash, tester, recipient, true);
    }

    static JO process(String shufflingFullHash, Tester tester, Tester recipient, boolean broadcast) {
        ShufflingProcessCall builder = ShufflingProcessCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                secretPhrase(tester.getSecretPhrase()).
                recipientSecretPhrase(recipient.getSecretPhrase()).
                feeRateNQTPerFXT(IGNIS.ONE_COIN);
        if (!broadcast) {
            builder.broadcast(false);
        }
        JO shufflingProcessResponse = builder.call();
        Logger.logMessage("shufflingProcessResponse: " + shufflingProcessResponse.toJSONString());
        return shufflingProcessResponse;
    }

    static JO verify(String shufflingFullHash, Tester tester, String shufflingStateHash) {
        JO response = ShufflingVerifyCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                secretPhrase(tester.getSecretPhrase()).
                shufflingStateHash(shufflingStateHash).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                call();
        Logger.logDebugMessage("shufflingVerifyResponse:" + response);
        return response;
    }

    static JO cancel(String shufflingFullHash, Tester tester, String shufflingStateHash, long cancellingAccountId) {
        return cancel(shufflingFullHash, tester, shufflingStateHash, cancellingAccountId, true);
    }

    static JO cancel(String shufflingFullHash, Tester tester, String shufflingStateHash, long cancellingAccountId, boolean broadcast) {
        ShufflingCancelCall builder = ShufflingCancelCall.create(IGNIS.getId()).
                shufflingFullHash(shufflingFullHash).
                secretPhrase(tester.getSecretPhrase()).
                shufflingStateHash(shufflingStateHash).
                feeRateNQTPerFXT(IGNIS.ONE_COIN);
        if (cancellingAccountId != 0) {
            builder.cancellingAccount(cancellingAccountId);
        }
        if (!broadcast) {
            builder.broadcast(false);
        }
        JO response = builder.call();
        Logger.logDebugMessage("shufflingCancelResponse:" + response);
        return response;
    }

    static JO broadcast(JO transaction, Tester tester) {
        transaction.remove("signature");
        JO response = SignTransactionCall.create()
                .unsignedTransactionJSON(transaction.toJSONString())
                .validate(false)
                .secretPhrase(tester.getSecretPhrase())
                .call();
        if (response.get("transactionJSON") == null) {
            return response;
        }
        response = BroadcastTransactionCall.create().
                transactionJSON(response.getJo("transactionJSON").toJSONString()).
                call();
        Logger.logDebugMessage("broadcastTransactionResponse:" + response);
        return response;
    }

    public static JO startShuffler(Tester tester, Tester recipient, String shufflingFullHash) {
        JO response = StartShufflerCall.create(IGNIS.getId()).
                secretPhrase(tester.getSecretPhrase()).
                recipientPublicKey(recipient.getPublicKey()).
                shufflingFullHash(shufflingFullHash).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                call();
        Logger.logMessage("startShufflerResponse: " + response.toJSONString());
        return response;
    }

    static JO stopShuffler(Tester tester, String shufflingFullHash) {
        JO response = StopShufflerCall.create().
                secretPhrase(tester.getSecretPhrase()).
                shufflingFullHash(shufflingFullHash).
                call();
        Logger.logMessage("stopShufflerResponse: " + response.toJSONString());
        return response;
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    static JO sendMoney(Tester sender, Tester recipient, long amountNXT) {
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(sender.getSecretPhrase()).
                recipient(recipient.getStrId()).
                amountNQT(amountNXT * IGNIS.ONE_COIN).
                feeRateNQTPerFXT(IGNIS.ONE_COIN).
                call();
        Logger.logMessage("sendMoneyResponse: " + response.toJSONString());
        return response;
    }

    private ShufflingUtil() {}

}
