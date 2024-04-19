/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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

package com.jelurida.ardor.contracts;

import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.callers.IssueAssetCall;
import nxt.http.responses.TransactionResponse;
import nxt.util.Convert;
import org.junit.Test;

public class ContractRunnerTest extends AbstractContractTest {
    @Test
    public void testTransferAssetQty0() {
        String contractName = ContractTestHelper.deployContract(AssetTransferQty0Contract.class);
        TransactionResponse issueAssetTransaction = IssueAssetCall.create(2).name("myAsset").description("...").
                quantityQNT(123456789).decimals(4).privateKey(ALICE.getPrivateKey()).feeNQT(100 * ChildChain.IGNIS.ONE_COIN).
                getCreatedTransaction();

        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        JO params = new JO();
        params.put("assetId", Convert.fullHashToId(issueAssetTransaction.getFullHash()));
        messageJson.put("params", params);

        String message = messageJson.toJSONString();
        ContractTestHelper.messageTriggerContract(message);
        generateBlock();

    }
}
