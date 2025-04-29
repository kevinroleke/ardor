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

package com.jelurida.ardor.contracts;

import nxt.addons.AbstractContract;
import nxt.addons.JO;
import nxt.addons.TransactionContext;
import nxt.blockchain.ChildChain;
import nxt.http.callers.TransferAssetCall;

public class AssetTransferQty0Contract extends AbstractContract<Void,Void> {
    @Override
    public JO processTransaction(TransactionContext context) {
        long assetId = context.getRuntimeParams().getLong("assetId");
        context.createTransaction(TransferAssetCall.create(ChildChain.IGNIS.getId()).asset(assetId).quantityQNT(0).recipient(
                context.getSenderId()));
        return context.getResponse();
    }
}
