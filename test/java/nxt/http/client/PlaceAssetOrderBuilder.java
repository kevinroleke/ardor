/*
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

package nxt.http.client;

import nxt.Tester;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.PlaceAskOrderCall;
import nxt.http.callers.PlaceBidOrderCall;

import static nxt.blockchain.ChildChain.IGNIS;

public class PlaceAssetOrderBuilder {
    private final Tester sender;
    private final long assetId;
    private final long quantityQNT;
    private final long price;
    private long feeNQT;

    public PlaceAssetOrderBuilder(Tester sender, String assetId, long quantityQNT, long price) {
        this(sender, Long.parseUnsignedLong(assetId), quantityQNT, price);
    }

    public PlaceAssetOrderBuilder(Tester sender, long assetId, long quantityQNT, long price) {
        this.sender = sender;
        this.assetId = assetId;
        this.quantityQNT = quantityQNT;
        this.price = price;
    }

    public PlaceAssetOrderBuilder setFeeNQT(long feeNQT) {
        this.feeNQT = feeNQT;
        return this;
    }

    private PlaceBidOrderCall buildBid() {
        return PlaceBidOrderCall.create(IGNIS.getId())
                .secretPhrase(sender.getSecretPhrase())
                .asset(assetId)
                .quantityQNT(quantityQNT)
                .priceNQTPerShare(price)
                .feeNQT(feeNQT);
    }

    private PlaceAskOrderCall buildAsk() {
        return PlaceAskOrderCall.create(IGNIS.getId())
                .secretPhrase(sender.getSecretPhrase())
                .asset(assetId)
                .quantityQNT(quantityQNT)
                .priceNQTPerShare(price)
                .feeNQT(feeNQT);
    }

    public JO placeBidOrder() {
        return buildBid().callNoError();
    }

    public JO placeAskOrder() {
        return buildAsk().callNoError();
    }

    public APICall.InvocationError placeBidOrderWithError() {
        return buildBid().build().invokeWithError();
    }

    public APICall.InvocationError placeAskOrderWithError() {
        return buildAsk().build().invokeWithError();
    }
}
