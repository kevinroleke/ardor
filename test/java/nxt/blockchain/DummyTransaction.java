/*
 * Copyright © 2024-2026 Jelurida Swiss SA
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
package nxt.blockchain;

import nxt.NxtException;
import nxt.messaging.PrunableEncryptedMessageAppendix;
import nxt.messaging.PrunablePlainMessageAppendix;
import nxt.util.Filter;
import org.json.simple.JSONObject;

import java.util.Collections;
import java.util.List;

public interface DummyTransaction extends Transaction {

    @Override
    default long getId() {
        return 0;
    }

    @Override
    default int getHeight() {
        return 0;
    }

    @Override
    default long getFee() {
        return 0;
    }

    @Override
    default short getDeadline() {
        return 0;
    }

    @Override
    default Chain getChain() {
        return null;
    }

    @Override
    default TransactionType getType() {
        return null;
    }

    @Override
    default String getStringId() {
        return "0";
    }

    @Override
    default long getSenderId() {
        return 0;
    }

    @Override
    default byte[] getSenderPublicKey() {
        return new byte[0];
    }

    @Override
    default long getRecipientId() {
        return 0;
    }

    @Override
    default long getBlockId() {
        return 0;
    }

    @Override
    default Block getBlock() {
        return null;
    }

    @Override
    default int getTimestamp() {
        return 0;
    }

    @Override
    default int getBlockTimestamp() {
        return 0;
    }

    @Override
    default int getExpiration() {
        return 0;
    }

    @Override
    default long getAmount() {
        return 0;
    }

    @Override
    default long getMinimumFeeFQT() {
        return 0;
    }

    @Override
    default byte[] getSignature() {
        return new byte[0];
    }

    @Override
    default Attachment getAttachment() {
        return null;
    }

    @Override
    default boolean verifySignature() {
        return true;
    }

    @Override
    default void validate() throws NxtException.ValidationException {

    }

    @Override
    default byte[] getBytes() {
        return new byte[0];
    }

    @Override
    default byte[] getUnsignedBytes() {
        return new byte[0];
    }

    @Override
    default byte[] getPrunableBytes() {
        return new byte[0];
    }

    @Override
    default JSONObject getJSONObject() {
        return new JSONObject();
    }

    @Override
    default JSONObject getPrunableAttachmentJSON() {
        return new JSONObject();
    }

    @Override
    default byte getVersion() {
        return 1;
    }

    @Override
    default int getFullSize() {
        return 0;
    }

    @Override
    default List<? extends Appendix> getAppendages() {
        return Collections.emptyList();
    }

    @Override
    default List<? extends Appendix> getAppendages(boolean includeExpiredPrunable) {
        return Collections.emptyList();
    }

    @Override
    default List<? extends Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable) {
        return Collections.emptyList();
    }

    @Override
    default int getECBlockHeight() {
        return 0;
    }

    @Override
    default long getECBlockId() {
        return 0;
    }

    @Override
    default short getIndex() {
        return 0;
    }

    @Override
    default boolean isPhased() {
        return false;
    }

    @Override
    default byte[] getFullHash() {
        return new byte[0];
    }

    @Override
    default PrunablePlainMessageAppendix getPrunablePlainMessage() {
        return null;
    }

    @Override
    default PrunableEncryptedMessageAppendix getPrunableEncryptedMessage() {
        return null;
    }
}
