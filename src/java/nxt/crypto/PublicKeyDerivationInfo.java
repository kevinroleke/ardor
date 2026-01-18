/*
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

package nxt.crypto;

import java.util.Objects;

public class PublicKeyDerivationInfo {
    private final byte[] masterPublicKey;
    private final byte[] chainCode;
    private final int childIndex;

    public PublicKeyDerivationInfo(SerializedMasterPublicKey serializedMasterPublicKey, int childIndex) {
        this(serializedMasterPublicKey.getMasterPublicKey(), serializedMasterPublicKey.getChainCode(), childIndex);
    }

    public PublicKeyDerivationInfo(byte[] masterPublicKey, byte[] chainCode, int childIndex) {
        this.masterPublicKey = Objects.requireNonNull(masterPublicKey);
        this.chainCode = Objects.requireNonNull(chainCode);
        this.childIndex = childIndex;
        if (childIndex < 0) {
            throw new IllegalStateException("Invalid negative child index.");
        }
    }

    public PublicKeyDerivationInfo withNextChild() {
        if (childIndex == Integer.MAX_VALUE) {
            throw new IllegalStateException("Integer overflow");
        }
        return new PublicKeyDerivationInfo(masterPublicKey, chainCode, childIndex + 1);
    }

    public byte[] getMasterPublicKey() {
        return masterPublicKey;
    }

    public byte[] getChainCode() {
        return chainCode;
    }

    public int getChildIndex() {
        return childIndex;
    }
}
