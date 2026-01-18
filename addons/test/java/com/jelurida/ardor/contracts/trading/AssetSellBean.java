/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
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

package com.jelurida.ardor.contracts.trading;

import nxt.BlockchainTest;
import nxt.http.callers.PlaceAskOrderCall;
import org.json.simple.JSONObject;

import static nxt.blockchain.ChildChain.IGNIS;

class AssetSellBean extends AssetOrderBean {
    public AssetSellBean(long price, long quantity, long asset) {
        super(price, quantity, asset);
    }

    @Override
    void placeOrder() {
        PlaceAskOrderCall.create(IGNIS.getId())
                .quantityQNT(quantity)
                .priceNQTPerShare(price)
                .asset(asset)
                .secretPhrase(BlockchainTest.DAVE.getSecretPhrase())
                .feeNQT(IGNIS.ONE_COIN)
                .callNoError();
    }

    public static AssetSellBean fromJSONObject(JSONObject o) {
        long asset = Long.parseUnsignedLong((String) o.get("asset"));
        long quantityQNT = Long.parseLong((String) o.get("quantityQNT"));
        long price = Long.parseLong((String) o.get("priceNQTPerShare"));
        return new AssetSellBean(price, quantityQNT, asset);
    }
}
