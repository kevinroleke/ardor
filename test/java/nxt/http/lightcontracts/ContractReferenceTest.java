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

package nxt.http.lightcontracts;

import com.jelurida.ardor.contracts.HelloWorld;
import nxt.BlockchainTest;
import nxt.blockchain.ChildChain;
import nxt.http.callers.SetContractReferenceCall;
import nxt.http.callers.UploadTaggedDataCall;
import nxt.util.JSONAssert;
import org.junit.Assert;
import org.junit.Test;

public class ContractReferenceTest extends BlockchainTest {

    @Test
    public void testDeployLongParams() {
        String contractName = HelloWorld.class.getSimpleName();
        StringBuilder sb = new StringBuilder("{\"a\": \"");
        for (int i = 0; i < 82; i++) {
            sb.append("€");
        }
        String longestValidParams = sb.toString();
        JSONAssert result = deployContract(contractName, longestValidParams + "a\"}");

        Assert.assertTrue(result.str("errorDescription").contains("Invalid light contract announcement"));

        result = deployContract(contractName, longestValidParams + "\"}");
        result.fullHash();

        generateBlock();
    }

    private JSONAssert deployContract(String contractName, String params) {
        JSONAssert result = new JSONAssert(UploadTaggedDataCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(ChildChain.IGNIS.ONE_COIN * 5).
                name(contractName).
                data("Not real contract").call());

        return new JSONAssert(SetContractReferenceCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(ChildChain.IGNIS.ONE_COIN * 5).
                contractName(contractName).
                contractParams(params).
                contract(ChildChain.IGNIS.getId() + ":" + result.fullHash()).call());
    }
}
