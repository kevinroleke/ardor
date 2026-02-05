/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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

package nxt.http.responses;

import nxt.addons.JO;
import org.json.simple.JSONObject;

import java.math.BigDecimal;

public interface AssetExchangeTradeResponse {

    static AssetExchangeTradeResponse create(JO object) {
        return new AssetExchangeTradeResponseImpl(object);
    }

    static AssetExchangeTradeResponse create(JSONObject object) {
        return new AssetExchangeTradeResponseImpl(object);
    }

    int getTimeStamp();

    long getQuantityQNT();

    BigDecimal getQuantity();

    long getPriceNQTPerCoin();

    BigDecimal getPricePerCoin();

    long getAsset();

    byte[] getAskOrderFullHash();

    byte[] getBidOrderFullHash();

    int getAskOrderHeight();

    int getBidOrderHeight();

    long getSeller();

    long getBuyer();

    long getBlock();

    int getHeight();

    boolean isBuy();

    AssetEntityResponse getAssetInfo();
}
