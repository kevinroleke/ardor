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

package nxt.http.accountproperties;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.http.callers.SetAccountPropertyCall;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class AccountPropertiesTest extends BlockchainTest {

    public static final String VALUE1 =
            "01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";

    public static final String KEY1 = "key1";

    @Test
    public void accountProperty1() {
        JO response = setAccountProperty(KEY1, VALUE1);
        Assert.assertEquals(4L, response.get("errorCode"));
        Assert.assertTrue(((String)response.get("errorDescription")).contains("Invalid account property"));
        BlockchainTest.generateBlock();
    }

    @Test
    public void accountProperty2() {
        char[] fourBytesChar = Character.toChars(0x1F701);
        String specialChar = new String(fourBytesChar);
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < 44; i++) {
            sb.append(specialChar);
        }
        String value = sb.toString();
        JO response = setAccountProperty(KEY1, value);
        Assert.assertEquals(4L, response.get("errorCode"));
        Assert.assertTrue(((String)response.get("errorDescription")).contains("Invalid account property"));
        BlockchainTest.generateBlock();
    }

    @Test
    public void accountProperty3() {
        char[] fourBytesChar = Character.toChars(0x1F701);
        String specialChar = new String(fourBytesChar);
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < 80; i++) {
            sb.append(specialChar);
        }
        String value = sb.toString();
        JO response = setAccountProperty(KEY1, value);
        Assert.assertEquals(4L, response.get("errorCode"));
        Assert.assertTrue(((String)response.get("errorDescription")).contains("Invalid account property"));
        BlockchainTest.generateBlock();
    }

    @Test
    public void accountPropertyName() {
        String specialChar = "€";
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < 32; i++) {
            sb.append(specialChar);
        }
        String name = sb.toString();
        JO response = setAccountProperty(name, "");

        Assert.assertNull(response.get("errorCode"));
        BlockchainTest.generateBlock();
    }

    private JO setAccountProperty(String name, String value) {
        return SetAccountPropertyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN * 20).
                recipient(BOB.getStrId()).
                property(name).
                value(value).
                call();
    }

}
