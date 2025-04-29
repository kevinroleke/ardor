/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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
package nxt.ae;

import nxt.NxtException;
import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import nxt.util.FixedPrecisionPercentage;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public class SetAssetTradeRoyaltiesAttachment extends Attachment.AbstractAttachment {

    private final long assetId;
    private final FixedPrecisionPercentage royaltiesPercentage;

    SetAssetTradeRoyaltiesAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        this.assetId = buffer.getLong();
        this.royaltiesPercentage = FixedPrecisionPercentage.RW.readFromBuffer(buffer);
    }

    SetAssetTradeRoyaltiesAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.assetId = Convert.parseUnsignedLong((String)attachmentData.get("asset"));
        this.royaltiesPercentage = FixedPrecisionPercentage.read(attachmentData, "percentage");
    }

    public SetAssetTradeRoyaltiesAttachment(long assetId, FixedPrecisionPercentage percentage) {
        this.assetId = assetId;
        this.royaltiesPercentage = percentage;
    }

    @Override
    protected int getMySize() {
        return 8 + FixedPrecisionPercentage.RW.getSize(royaltiesPercentage);
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(assetId);
        FixedPrecisionPercentage.RW.writeToBuffer(royaltiesPercentage, buffer);
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("asset", Long.toUnsignedString(assetId));
        royaltiesPercentage.write(json, "percentage");
    }

    @Override
    public TransactionType getTransactionType() {
        return AssetExchangeTransactionType.SET_ASSET_TRADE_ROYALTIES;
    }

    public long getAssetId() {
        return assetId;
    }

    public FixedPrecisionPercentage getRoyaltiesPercentage() {
        return royaltiesPercentage;
    }
}
