/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
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

package nxt.http;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.ae.Asset;
import nxt.ae.AssetControlTxTypesEnum;
import nxt.ae.AssetExchangeTransactionType;
import nxt.ae.SetPhasingAssetControlAttachment;
import nxt.voting.PhasingParams;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sets a restriction on certain asset that blocks transactions operating with this asset unless they are phased with certain parameters.
 * If no control is currently set for the specified asset, the sender must the be asset issuer.
 * 
 * <p>
 * Parameters
 * <ul>
 * <li>assetId - The asset</li>
 * <li>transactionType - One or more transaction types to which the asset control is applied (or from which
 * the asset control is removed if the voting model is NONE). Possible values:
 * <ul>
 * <li>ASSET_TRANSFER</li>
 * <li>ASSET_DELETE</li>
 * <li>ASK_ORDER_PLACEMENT</li>
 * <li>BID_ORDER_PLACEMENT</li>
 * <li>ASK_ORDER_CANCELLATION</li>
 * <li>BID_ORDER_CANCELLATION</li>
 * <li>DIVIDEND_PAYMENT</li>
 * <li>ASSET_PROPERTY_SET</li>
 * <li>ASSET_PROPERTY_DELETE</li>
 * <li>SET_ASSET_CONTROL</li>
 * </ul>
 * At any moment, there is only one asset control per asset and transaction type.
 * If no types are specified, defaults to {@link nxt.ae.AssetControlTxTypesEnum#DEFAULT_TYPES}
 * </li>
 * <li>control* - If controlParams is missing, the asset control parameters can be specified one-by-one, without using JSON,
 * with prefix "control" (to differentiate from the parameters used to phase the transaction)</li>
 * <li>controlParams - A JSON with all phasing parameters. Use the parsePhasingParams API to get this JSON.
 * If provided, all control* parameters are ignored</li>
 * </ul>
 */
public final class SetPhasingAssetControl extends CreateTransaction {

    static final SetPhasingAssetControl instance = new SetPhasingAssetControl();

    private SetPhasingAssetControl() {
        super(AssetExchangeTransactionType.SET_PHASING_CONTROL, new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION},
                "asset", "transactionType", "transactionType", "transactionType", "transactionType",
                "transactionType", "transactionType", "transactionType", "transactionType", "transactionType",
                "controlVotingModel", "controlQuorum", "controlMinBalance",
                "controlMinBalanceModel", "controlHolding", "controlWhitelisted", "controlWhitelisted", "controlWhitelisted",
                "controlSenderPropertySetter", "controlSenderPropertyName",
                "controlSenderPropertyValue", "controlRecipientPropertySetter",
                "controlRecipientPropertyName", "controlRecipientPropertyValue", "controlExpression",
                "controlParams");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws NxtException {
        Account account = ParameterParser.getSenderAccount(request);
        JSONObject controlParamsJson = ParameterParser.getJson(request, "controlParams");
        PhasingParams phasingParams;
        if (controlParamsJson != null) {
            phasingParams = new PhasingParams(controlParamsJson);
        } else {
            phasingParams = ParameterParser.parsePhasingParams(request, "control");
        }

        Asset asset = ParameterParser.getAsset(request);

        SetPhasingAssetControlAttachment attachment;
        if (Nxt.getBlockchain().getHeight() >= Constants.TRANSACTION_TYPE_SPECIFIC_ASSET_CONTROL) {
            String[] transactionTypesStrings = request.getParameterValues("transactionType");
            Set<AssetControlTxTypesEnum> typesSet;
            if (transactionTypesStrings == null) {
                typesSet = AssetControlTxTypesEnum.DEFAULT_TYPES;
            } else {
                typesSet = Arrays.stream(transactionTypesStrings).
                        map(AssetControlTxTypesEnum::valueOf).
                        collect(Collectors.toCollection(() -> EnumSet.noneOf(AssetControlTxTypesEnum.class)));
            }
            attachment = new SetPhasingAssetControlAttachment(asset.getId(), typesSet, phasingParams);
        } else {
            attachment = new SetPhasingAssetControlAttachment(asset.getId(), phasingParams);
        }
        return createTransaction(request, account, attachment);
    }

    @Override
    public boolean isIgnisOnly() {
        return true;
    }

}
