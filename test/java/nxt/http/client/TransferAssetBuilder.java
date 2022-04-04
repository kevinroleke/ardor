/*
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

package nxt.http.client;

import nxt.Tester;
import nxt.blockchain.ChildChain;
import nxt.http.APICall.InvocationError;
import nxt.http.callers.TransferAssetCall;

public class TransferAssetBuilder {
    private final long assetId;
    private final Tester from;
    private final Tester to;
    private long quantityQNT = 1;
    private long fee = ChildChain.IGNIS.ONE_COIN;

    public TransferAssetBuilder(String assetId, Tester from, Tester to) {
        this(Long.parseUnsignedLong(assetId), from, to);
    }

    public TransferAssetBuilder(long assetId, Tester from, Tester to) {
        this.assetId = assetId;
        this.from = from;
        this.to = to;
    }

    public TransferAssetBuilder setQuantityQNT(long quantityQNT) {
        this.quantityQNT = quantityQNT;
        return this;
    }

    public TransferAssetBuilder feeNQT(long fee) {
        this.fee = fee;
        return this;
    }

    private TransferAssetCall caller() {
        return TransferAssetCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(from.getSecretPhrase())
                .recipient(to.getRsAccount())
                .asset(assetId)
                .quantityQNT(quantityQNT)
                .feeNQT(fee);
    }

    public String transfer() {
        return caller().callNoError().getString("fullHash");
    }

    public InvocationError transferWithError() {
        return caller().build().invokeWithError();
    }
}
