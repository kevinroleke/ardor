/*
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

package nxt.util.security;

import java.security.Security;
import java.security.cert.CertPathBuilder;

/**
 * Convert the transaction certificate into a CertPath object.
 * Currently there is no real path, just a single certificate
 */
@SuppressWarnings("unused")
public class BlockchainCertPathBuilder extends CertPathBuilder {

    public BlockchainCertPathBuilder() {
        super(new BlockchainCertPathBuilderSpi(), Security.getProvider("Jelurida"), "Blockchain");
    }
}
