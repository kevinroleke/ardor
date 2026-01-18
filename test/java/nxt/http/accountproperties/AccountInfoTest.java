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

package nxt.http.accountproperties;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.http.callers.GetAccountCall;
import nxt.http.callers.SetAccountInfoCall;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class AccountInfoTest extends BlockchainTest {
    @Test
    public void testNameLen160() {
        char[] fourBytesChar = Character.toChars(0x1F701);
        String specialChar = new String(fourBytesChar);
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < 40; i++) {
            sb.append(specialChar);
        }
        String name = sb.toString();
        SetAccountInfoCall builder = SetAccountInfoCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN * 20).
                name(name);
        JO response = builder.call();
        Assert.assertEquals(4L, response.get("errorCode"));
        Assert.assertTrue(response.getString("errorDescription").contains("Invalid account info issuance"));
        BlockchainTest.generateBlock();

        String fixedName = name.substring(0, 40); //the specialChar is 2 characters long
        String description = name + name;
        builder.name(fixedName).description(description).callNoError();
        BlockchainTest.generateBlock();

        response = GetAccountCall.create().account(ALICE.getRsAccount()).callNoError();
        Assert.assertEquals(fixedName, response.get("name"));
        Assert.assertEquals(description, response.get("description"));
    }

    @Test
    public void testNameLen258() {
        String char3Byte = "€";
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < 86; i++) {
            sb.append(char3Byte);
        }
        String name = sb.toString();
        SetAccountInfoCall builder = SetAccountInfoCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN * 20).
                name(name);
        JO response = builder.call();
        Assert.assertEquals(4L, response.get("errorCode"));
        Assert.assertTrue(response.getString("errorDescription").contains("Invalid account info issuance"));
        BlockchainTest.generateBlock();

        String fixedName = name.substring(0, 33);
        builder.name(fixedName).callNoError();
        BlockchainTest.generateBlock();

        response = GetAccountCall.create().account(ALICE.getRsAccount()).callNoError();
        Assert.assertEquals(fixedName, response.get("name"));
    }
}
