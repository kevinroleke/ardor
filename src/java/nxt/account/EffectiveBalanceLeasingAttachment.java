/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
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

package nxt.account;

import nxt.Constants;
import nxt.Nxt;
import nxt.blockchain.Attachment;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;

public final class EffectiveBalanceLeasingAttachment extends Attachment.AbstractAttachment {

    private final int period;

    EffectiveBalanceLeasingAttachment(ByteBuffer buffer) {
        super(buffer);
        if (getVersion() == 1) {
            this.period = Short.toUnsignedInt(buffer.getShort());
        } else {
            this.period = buffer.getInt();
        }
    }

    EffectiveBalanceLeasingAttachment(JSONObject attachmentData) {
        super(attachmentData);
        this.period = ((Long) attachmentData.get("period")).intValue();
    }

    public EffectiveBalanceLeasingAttachment(int period, boolean isShortPeriod) {
        super(isShortPeriod ? 1 : 2);
        this.period = period;
    }

    @Override
    protected int getMySize() {
        return getVersion() == 1 ? 2 : 4;
    }

    @Override
    protected void putMyBytes(ByteBuffer buffer) {
        if (getVersion() == 1) {
            buffer.putShort((short)period);
        } else {
            buffer.putInt(period);
        }
    }

    @Override
    protected void putMyJSON(JSONObject attachment) {
        attachment.put("period", period);
    }

    @Override
    public boolean verifyVersion() {
        return getVersion() == (Nxt.getBlockchain().getHeight() < Constants.LEASING_PERIOD_INCREASE ? 1 : 2);
    }

    @Override
    public TransactionType getTransactionType() {
        return AccountControlFxtTransactionType.EFFECTIVE_BALANCE_LEASING;
    }

    public int getPeriod() {
        return period;
    }
}
