/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.http.alias;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.GetAliasCall;
import nxt.http.callers.SetAliasCall;
import nxt.http.callers.SignTransactionCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class CreateAliasTest extends BlockchainTest {
    @Test
    public void testAliasLen258() {
        String char3Byte = "€";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 86; i++) {
            sb.append(char3Byte);
        }
        String name = sb.toString();
        String uri = "nxt://test " + name + name;
        JO response = SetAliasCall.create(IGNIS.getId()).
                publicKey(ALICE.getPublicKeyStr()).feeNQT(IGNIS.ONE_COIN * 20).
                broadcast(false).
                aliasName("153307605").aliasURI(uri).callNoError();

        JO unsignedTransactionJSON = response.getJo("transactionJSON");
        JO attachment = unsignedTransactionJSON.getJo("attachment");
        attachment.put("alias", name);

        JO signResult = SignTransactionCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                unsignedTransactionJSON(unsignedTransactionJSON.toJSONString()).call();

        Assert.assertEquals(4L, signResult.get("errorCode"));
        BlockchainTest.generateBlock();

        String fixedName = "153307605";
        attachment.put("alias", fixedName);

        signResult = SignTransactionCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                unsignedTransactionJSON(unsignedTransactionJSON.toJSONString()).callNoError();

        response = BroadcastTransactionCall.create().
                transactionBytes(signResult.getString("transactionBytes")).
                callNoError();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        response = GetAliasCall.create(IGNIS.getId()).aliasName(fixedName).callNoError();
        Assert.assertEquals(uri, response.get("aliasURI"));
    }
}
