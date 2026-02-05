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

package nxt.http.assetexchange;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.callers.DeleteAssetPropertyCall;
import nxt.http.callers.SetAssetPropertyCall;
import nxt.http.client.GetAssetPropertiesBuilder;
import nxt.util.JSONAssert;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.ComparisonFailure;
import org.junit.Test;

import java.util.List;

public class AssetPropertiesTest extends BlockchainTest {

    @Test
    public void testSetGetProperty() {
        testSetGetProperty("some value");
    }

    @Test
    public void testDeleteProperty() {
        long assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetId();

        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop1")
                .value("value1").callNoError();

        generateBlock();

        createDeletePropertyBuilder(ALICE, assetId, "prop1").callNoError();

        generateBlock();

        JO actual = new GetAssetPropertiesBuilder(assetId).callNoError();

        List<JSONObject> properties = new JSONAssert(actual).array("properties", JSONObject.class);
        Assert.assertEquals(0, properties.size());
    }

    @Test
    public void testDeletePropertyOtherAccount() {
        long assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetId();

        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop")
                .value("value1").callNoError();

        generateBlock();

        createDeletePropertyBuilder(ALICE, assetId, "prop")
                .setter(BOB.getStrId())
                .callNoError();

        generateBlock();

        JO actual = new GetAssetPropertiesBuilder(assetId).callNoError();

        List<JSONObject> properties = new JSONAssert(actual).array("properties", JSONObject.class);
        Assert.assertEquals(0, properties.size());
    }

    @Test
    public void testDeletePropertyOtherAccountRejected() {
        long assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetId();

        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("some prop")
                .value("value1").callNoError();

        generateBlock();

        JO deleteResult = createDeletePropertyBuilder(BOB, assetId, "some prop")
                .setter(ALICE.getStrId())
                .call();

        String errorDescription = new JSONAssert(deleteResult).str("errorDescription");
        Assert.assertEquals("Incorrect \"property\" (cannot be deleted by this account)", errorDescription);

        generateBlock();
        JO properties = new GetAssetPropertiesBuilder(assetId).callNoError();

        assertOnlyProperty("some prop", "value1", properties);
        assertPropertySetter(ALICE, properties.getArray("properties").get(0));
    }

    private DeleteAssetPropertyCall createDeletePropertyBuilder(Tester requester, long assetId, String property) {
        return DeleteAssetPropertyCall.create(ChildChain.IGNIS.getId()).
                secretPhrase(requester.getSecretPhrase()).
                asset(assetId).
                feeNQT(3 * ChildChain.IGNIS.ONE_COIN).
                property(property);
    }

    @Test
    public void testSetGetPropertyNullValue() {
        testSetGetProperty(null);
    }

    @Test
    public void testSetGetPropertyMultipleAccounts() {
        long assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetId();

        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop1")
                .value("some value").callNoError();
        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop1")
                .value("some other value").callNoError();

        generateBlock();

        JO actual = new GetAssetPropertiesBuilder(assetId).callNoError();

        assertContainsProperty(ALICE, "prop1", "some value", actual);
        assertContainsProperty(BOB, "prop1", "some other value", actual);
    }

    @Test
    public void testSetGetPropertyOfSingleAccount() {
        long assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetId();

        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop1")
                .value("some value").callNoError();
        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop1")
                .value("some other value").callNoError();

        generateBlock();

        JO actual = new GetAssetPropertiesBuilder(assetId).setter(ALICE).callNoError();

        assertOnlyProperty("prop1", "some value", actual);
        assertPropertySetter(ALICE, actual);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertContainsProperty(Tester expectedSetter, String expectedName, String expectedValue, JO response) {
        List<JSONObject> properties = new JSONAssert(response).array("properties", JSONObject.class);
        for (JSONObject actualProperty : properties) {
            try {
                assertPropertyNameValue(expectedName, expectedValue, actualProperty);
                assertPropertySetter(expectedSetter, new JO(actualProperty));
                return;
            } catch (ComparisonFailure ignored) {
            }
        }
        Assert.fail(String.format("Not found property (%s, %s, %s) in response: %s",
                expectedSetter.getAccount(),
                expectedName,
                expectedValue,
                response));
    }

    private void testSetGetProperty(String value) {
        long assetId = AssetExchangeTest.issueAsset(ALICE, "AssetC").getAssetId();

        SetAssetPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .asset(assetId)
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property("prop")
                .value(value).callNoError();

        generateBlock();

        JO actual = new GetAssetPropertiesBuilder(assetId).callNoError();

        assertOnlyProperty("prop", value, actual);
        assertPropertySetter(ALICE, actual.getArray("properties").get(0));
    }

    private void assertOnlyProperty(String expectedName, String expectedValue, JO response) {
        List<JSONObject> properties = new JSONAssert(response).array("properties", JSONObject.class);
        JSONObject actualProperty = properties.get(0);
        assertPropertyNameValue(expectedName, expectedValue, actualProperty);
        Assert.assertEquals(1, properties.size());
    }

    private static void assertPropertyNameValue(String expectedName, String expectedValue, JSONObject actualProperty) {
        Assert.assertEquals(expectedName, actualProperty.get("property"));
        Assert.assertEquals(expectedValue, actualProperty.get("value"));
    }

    private static void assertPropertySetter(Tester expectedSetter, JO json) {
        Assert.assertEquals(Long.toUnsignedString(expectedSetter.getId()), json.get("setter"));
    }
}
