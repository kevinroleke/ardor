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

import nxt.addons.JO;
import nxt.http.callers.GetAccountCurrentAskOrderIdsCall;
import nxt.http.callers.GetAccountCurrentBidOrderIdsCall;
import nxt.util.JSONAssert;

import java.util.List;

import static nxt.blockchain.ChildChain.IGNIS;

public class GetAccountCurrentOrderIdsBuilder {
    private final long accountId;
    private long assetId = 0;

    public GetAccountCurrentOrderIdsBuilder(long accountId) {
        this.accountId = accountId;
    }

    public GetAccountCurrentOrderIdsBuilder setAssetId(String assetId) {
        return setAssetId(Long.parseUnsignedLong(assetId));
    }

    public GetAccountCurrentOrderIdsBuilder setAssetId(long assetId) {
        this.assetId = assetId;
        return this;
    }

    public List<String> getAskOrders() {
        JO result = GetAccountCurrentAskOrderIdsCall.create(IGNIS.getId())
                .account(accountId)
                .asset(assetId)
                .callNoError();
        return new JSONAssert(result).array("askOrderIds", String.class);
    }

    public List<String> getBidOrders() {
        JO result = GetAccountCurrentBidOrderIdsCall.create(IGNIS.getId())
                .account(accountId)
                .asset(assetId)
                .callNoError();
        return new JSONAssert(result).array("bidOrderIds", String.class);
    }

}
