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

public class ChainInfoResponseImpl implements ChainInfoResponse {

    private final String name;
    private final byte decimals;

    ChainInfoResponseImpl(JSONObject response) {
        this(new JO(response));
    }

    ChainInfoResponseImpl(JO chainJson) {
        name = chainJson.getString("name");
        decimals = chainJson.getByte("decimals");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte getDecimals() {
        return decimals;
    }
}
