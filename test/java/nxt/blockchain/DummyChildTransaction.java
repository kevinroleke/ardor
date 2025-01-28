/*
 * Copyright © 2024 Jelurida Swiss SA
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

import nxt.messaging.EncryptToSelfMessageAppendix;
import nxt.messaging.EncryptedMessageAppendix;
import nxt.messaging.MessageAppendix;
import nxt.voting.PhasingAppendix;

interface DummyChildTransaction extends DummyTransaction, ChildTransaction {
    @Override
    default long getFxtTransactionId() {
        return 0;
    }

    @Override
    default ChainTransactionId getReferencedTransactionId() {
        return null;
    }

    @Override
    default MessageAppendix getMessage() {
        return null;
    }

    @Override
    default EncryptedMessageAppendix getEncryptedMessage() {
        return null;
    }

    @Override
    default EncryptToSelfMessageAppendix getEncryptToSelfMessage() {
        return null;
    }

    @Override
    default PhasingAppendix getPhasing() {
        return null;
    }

    @Override
    default ChildChain getChain() {
        return null;
    }
}
