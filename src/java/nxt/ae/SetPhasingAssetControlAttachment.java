/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
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
 
 package nxt.ae;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import nxt.util.Convert;
import nxt.util.bbh.ByteArrayRw;
import nxt.voting.PhasingParams;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class SetPhasingAssetControlAttachment extends Attachment.AbstractAttachment {
    private static final ByteArrayRw TYPES_BITMASK_RW = new ByteArrayRw(255);
    private final long assetId;
    private final Set<AssetControlTxTypesEnum> transactionTypes;
    private byte[] cachedTransactionTypesBytes;
    private final PhasingParams phasingParams;

    SetPhasingAssetControlAttachment(ByteBuffer buffer) throws NxtException.NotValidException {
        super(buffer);
        this.assetId = buffer.getLong();
        if (getVersion() > 1) {
            byte[] bytes = TYPES_BITMASK_RW.readFromBuffer(buffer);
            this.transactionTypes = Collections.unmodifiableSet(AssetControlTxTypesEnum.bitSetToSet(BitSet.valueOf(bytes)));
        } else {
            this.transactionTypes = AssetControlTxTypesEnum.DEFAULT_TYPES;
        }
        this.phasingParams = new PhasingParams(buffer);
    }

    SetPhasingAssetControlAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.assetId = Convert.parseUnsignedLong((String)attachmentData.get("asset"));
        if (getVersion() > 1) {
            JSONArray transactionTypes = (JSONArray) attachmentData.get("transactionTypes");
            EnumSet<AssetControlTxTypesEnum> types = EnumSet.noneOf(AssetControlTxTypesEnum.class);
            for (Object o : transactionTypes) {
                types.add(AssetControlTxTypesEnum.valueOf((String) o));
            }
            this.transactionTypes = Collections.unmodifiableSet(types);
        } else {
            this.transactionTypes = AssetControlTxTypesEnum.DEFAULT_TYPES;
        }
        JSONObject phasingControlParams = (JSONObject) attachmentData.get("phasingControlParams");
        phasingParams = new PhasingParams(phasingControlParams);
    }

    public SetPhasingAssetControlAttachment(long assetId, PhasingParams phasingParams) {
        this.assetId = assetId;
        this.transactionTypes = AssetControlTxTypesEnum.DEFAULT_TYPES;
        this.phasingParams = phasingParams;
    }

    public SetPhasingAssetControlAttachment(long assetId, Set<AssetControlTxTypesEnum> transactionTypes, PhasingParams phasingParams) {
        //Version 2 of the attachment - requires blockchain height => Constants.TRANSACTION_TYPE_SPECIFIC_ASSET_CONTROL
        super(2);
        this.assetId = assetId;
        this.transactionTypes = transactionTypes;
        this.phasingParams = phasingParams;
    }
    @Override
    protected int getMySize() {
        int result = 8 + phasingParams.getMySize();
        if (getVersion() > 1) {
            result += TYPES_BITMASK_RW.getSize(getTransactionTypesBytes());
        }
        return result;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        buffer.putLong(assetId);
        if (getVersion() > 1) {
            TYPES_BITMASK_RW.writeToBuffer(getTransactionTypesBytes(), buffer);
        }
        phasingParams.putMyBytes(buffer);
    }

    private byte[] getTransactionTypesBytes() {
        if (cachedTransactionTypesBytes != null) {
            return cachedTransactionTypesBytes;
        }
        cachedTransactionTypesBytes = AssetControlTxTypesEnum.bitSetFromSet(this.transactionTypes).toByteArray();
        return cachedTransactionTypesBytes;
    }

    @Override
    protected void putMyJSON(JSONObject json) {
        json.put("asset", Long.toUnsignedString(assetId));
        if (getVersion() > 1) {
            json.put("transactionTypes", AssetControlTxTypesEnum.jsonArrayFromSet(transactionTypes));
        }
        JSONObject phasingControlParams = new JSONObject();
        phasingParams.putMyJSON(phasingControlParams);
        json.put("phasingControlParams", phasingControlParams);
    }

    @Override
    public boolean verifyVersion() {
        return getVersion() == (Nxt.getBlockchain().getHeight() < Constants.TRANSACTION_TYPE_SPECIFIC_ASSET_CONTROL ? 1 : 2);
    }

    @Override
    public TransactionType getTransactionType() {
        return AssetExchangeTransactionType.SET_PHASING_CONTROL;
    }

    public long getAssetId() {
        return assetId;
    }

    public Set<AssetControlTxTypesEnum> getTransactionTypes() {
        return transactionTypes;
    }

    public PhasingParams getPhasingParams() {
        return phasingParams;
    }
}
