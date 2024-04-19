/*
 * Copyright © 2021-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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

public interface MonetarySystemTradeResponse {
    static MonetarySystemTradeResponse create(JO object) {
        return new MonetarySystemTradeResponseImpl(object);
    }

    static MonetarySystemTradeResponse create(JSONObject object) {
        return new MonetarySystemTradeResponseImpl(object);
    }

    byte[] getTransactionFullHash();

    int getTimestamp();

    long getUnitsQNT();

    BigDecimal getUnits();

    long getRateNQTPerUnit();

    BigDecimal getRatePerUnit();

    long getCurrency();

    long getOffer();

    byte[] getOfferFullHash();

    long getSeller();

    long getBuyer();

    long getBlock();

    int getHeight();

    CurrencyEntityResponse getCurrencyInfo();
}
