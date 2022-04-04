/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.http;

import nxt.ae.Asset;
import nxt.ae.AssetControl;
import nxt.ae.AssetControlTxTypesEnum;
import nxt.util.Convert;
import nxt.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Returns the phasing control for given asset, if set. The result contains the following entries similar to the control* parameters of {@link SetPhasingAssetControl}
 * 
 * <p>
 * Parameters
 * <ul>
 * <li>asset - the asset ID for which the phasing control is queried</li>
 * </ul>
 * 
 * 
 * @see SetPhasingAssetControl
 * 
 */
public final class GetPhasingAssetControl extends APIServlet.APIRequestHandler {

    static final GetPhasingAssetControl instance = new GetPhasingAssetControl();

    private GetPhasingAssetControl() {
        super(new APITag[] {APITag.AE}, "asset");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        Asset asset = ParameterParser.getAsset(req);

        if (asset.hasPhasingControl()) {
            JSONObject result = new JSONObject();
            result.put("asset", Long.toUnsignedString(asset.getId()));
            result.put("assetName", asset.getName());
            List<AssetControl.PhasingOnly> controls = AssetControl.PhasingOnly.getAllPerAsset(asset.getId());
            //WARNING: API change since asset control became transaction type specific. Return an array of controls
            // instead of single controlParams object
            JSONArray controlsArray = new JSONArray();
            for (AssetControl.PhasingOnly control : controls) {
                JSONObject controlJson = new JSONObject();
                controlJson.put("id", Long.toUnsignedString(control.getId()));
                controlJson.put("transactionTypes", AssetControlTxTypesEnum.jsonArrayFromSet(control.getTransactionTypes()));
                control.getPhasingParams().putMyJSON(controlJson);
                controlsArray.add(controlJson);
            }
            result.put("controls", controlsArray);
            return result;
        } else {
            return JSON.emptyJSON;
        }
    }

    @Override
    protected boolean isChainSpecific() {
        return false;
    }

}
