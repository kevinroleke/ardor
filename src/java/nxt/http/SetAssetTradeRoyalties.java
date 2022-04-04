/*
 * Copyright © 2021-2022 Jelurida IP B.V.
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

package nxt.http;

import nxt.Constants;
import nxt.NxtException;
import nxt.account.Account;
import nxt.ae.Asset;
import nxt.ae.AssetExchangeTransactionType;
import nxt.ae.SetAssetTradeRoyaltiesAttachment;
import nxt.util.FixedPrecisionPercentage;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;

public final class SetAssetTradeRoyalties extends CreateTransaction {

    static final SetAssetTradeRoyalties instance = new SetAssetTradeRoyalties();

    private SetAssetTradeRoyalties() {
        super(AssetExchangeTransactionType.SET_ASSET_TRADE_ROYALTIES, new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION},
                "asset", "royaltiesPercentage");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws NxtException {
        Account account = ParameterParser.getSenderAccount(request);

        Asset asset = ParameterParser.getAsset(request);

        FixedPrecisionPercentage royaltiesPercentage = ParameterParser.getPercentage(request, "royaltiesPercentage",
                BigDecimal.ZERO, BigDecimal.valueOf(Constants.MAX_ASSET_TRADE_ROYALTIES_PERCENTAGE), true);

        SetAssetTradeRoyaltiesAttachment attachment = new SetAssetTradeRoyaltiesAttachment(asset.getId(), royaltiesPercentage);
        return createTransaction(request, account, attachment);
    }

    @Override
    public boolean isIgnisOnly() {
        return true;
    }

}
