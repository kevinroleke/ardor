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

public interface CoinExchangeOrderResponse {

    static CoinExchangeOrderResponse create(JSONObject object) {
        return new CoinExchangeOrderResponseImpl(object);
    }

    static CoinExchangeOrderResponse create(JO object) {
        return new CoinExchangeOrderResponseImpl(object);
    }

    long getOrder();

    byte[] getOrderFullHash();

    int getChain();

    int getExchangeChain();

    long getAccount();

    long getQuantityQNT();

    long getExchangeQNT();

    long getBidNQTPerCoin();

    long getAskNQTPerCoin();

    BigDecimal getQuantity(byte decimals);

    BigDecimal getExchangeQuantity(byte decimals);

    BigDecimal getBidPerCoin(byte decimals);

    BigDecimal getAskPerCoin(byte decimals);
}
