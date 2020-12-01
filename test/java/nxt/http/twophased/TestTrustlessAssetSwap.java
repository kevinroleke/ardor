/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2020 Jelurida IP B.V.
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

package nxt.http.twophased;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.account.Account;
import nxt.addons.JO;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.GetTransactionCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.SignTransactionCall;
import nxt.http.callers.TransferAssetCall;
import nxt.util.Convert;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestTrustlessAssetSwap extends BlockchainTest {

    @Test
    public void assetSwap() {
        // Alice and Bob each has its own asset
        JO aliceAsset = IssueAssetCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                name("AliceAsset").
                description("AliceAssetDescription").
                quantityQNT(1000).
                decimals(0).
                feeNQT(1000 * IGNIS.ONE_COIN).
                callNoError();
        generateBlock();
        JO bobAsset = IssueAssetCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                name("BobAsset").
                description("BobAssetDescription").
                quantityQNT(1000).
                decimals(0).
                feeNQT(2000 * IGNIS.ONE_COIN).
                callNoError();
        generateBlock();

        // Alice prepares and signs a transaction #1, an asset transfer to Bob.
        // She does not broadcast it, but sends to Bob the unsigned bytes, the
        // full transaction hash, and the signature hash.
        String aliceAssetId = Tester.responseToStringId(aliceAsset);
        JO aliceUnsignedTransfer = TransferAssetCall.create(IGNIS.getId()).
                publicKey(ALICE.getPublicKeyStr()).
                recipient(BOB.getStrId()).
                asset(aliceAssetId).
                quantityQNT(100).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();

        JO aliceSignedTransfer = SignTransactionCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                unsignedTransactionBytes(aliceUnsignedTransfer.getString("unsignedTransactionBytes")).
                callNoError();

        String aliceTransferFullHash = (String)aliceSignedTransfer.get("fullHash");
        Assert.assertEquals(64, aliceTransferFullHash.length());
        String aliceTransferTransactionBytes = (String)aliceSignedTransfer.get("transactionBytes");

        // Bob submits transaction #2, an asset transfer to Alice, making it phased using a by-transaction voting model
        // with a quorum of 1 and just the full hash of #1 in the phasing transaction full hashes list.
        String bobAssetId = Tester.responseToStringId(bobAsset);
        JO bobTransfer = TransferAssetCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).
                recipient(ALICE.getStrId()).
                asset(bobAssetId).
                quantityQNT(200).
                feeNQT(3 * IGNIS.ONE_COIN).
                phased(true).
                phasingFinishHeight(baseHeight + 5).
                phasingVotingModel((byte) 4).
                phasingLinkedTransaction(IGNIS.getId() + ":" + aliceTransferFullHash).
                phasingQuorum(1).
                callNoError();
        generateBlock();

        // Alice sees Bob's transaction #2 in the blockchain, waits to make sure it is confirmed irreversibly.
        JO bobTransferValidation = GetTransactionCall.create().
                fullHash(bobTransfer.getString("fullHash")).
                callNoError();
        Assert.assertEquals(bobTransfer.get("fullHash"), bobTransferValidation.get("fullHash"));

        // She then submits her transaction #1.
        BroadcastTransactionCall.create().
                transactionBytes(aliceTransferTransactionBytes).
                callNoError();
        generateBlock();

        // Both transactions have executed
        Assert.assertEquals(200, Account.getAssetBalanceQNT(ALICE.getId(), Convert.parseUnsignedLong(bobAssetId)));
        Assert.assertEquals(100, Account.getAssetBalanceQNT(BOB.getId(), Convert.parseUnsignedLong(aliceAssetId)));
    }

}
