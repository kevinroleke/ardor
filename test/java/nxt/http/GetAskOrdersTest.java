/*
 * Copyright © 2016-2022 Jelurida IP B.V.
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

package nxt.http;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.callers.CancelAskOrderCall;
import nxt.http.callers.GetAskOrdersCall;
import nxt.http.callers.IssueAssetCall;
import nxt.http.callers.PlaceAskOrderCall;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static nxt.Tester.hexFullHashToStringId;
import static nxt.blockchain.ChildChain.IGNIS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GetAskOrdersTest extends BlockchainTest {
    private final Tester tester = ALICE;
    private final ChildChain chain = IGNIS;
    private int assetNumber = 0;

    @Test
    public void testGetAskOrdersConfirmedAndNot() {
        long assetId = createAsset();
        generateBlock();

        String order = placeAskOrder(assetId);

        generateBlock();
        String unconfirmedOrder = placeAskOrder(assetId);

        JO result = getGetAskOrdersCall().asset(assetId).callNoError();

        assertContainsOrder("order", order, result);
        assertExcludesOrder("unconfirmedOrder", unconfirmedOrder, result);
    }

    @Test
    public void testGetAskOrdersWithExpectedCancellations() {
        long assetId = createAsset();
        generateBlock();

        String order = placeAskOrder(assetId);

        generateBlock();
        cancelOrder(order);

        JO result = getGetAskOrdersCall().asset(assetId).showExpectedCancellations("true").callNoError();

        assertContainsOrderWithCancellation("order", order, result);
    }

    private void cancelOrder(String order) {
        CancelAskOrderCall.create(chain.getId())
                .order(hexFullHashToStringId(order))
                .secretPhrase(tester.getSecretPhrase())
                .feeNQT(chain.ONE_COIN)
                .callNoError();
    }

    @Test
    public void testGetAskOrdersOneAsset() {
        long asset = createAsset();
        long assetAnother = createAsset();
        generateBlock();

        String order = placeAskOrder(asset);
        String orderAnother = placeAskOrder(assetAnother);

        generateBlock();

        JO result = getGetAskOrdersCall().asset(asset).callNoError();

        assertContainsOrder("order from asset", order, result);
        assertExcludesOrder("order from another asset", orderAnother, result);
    }

    private GetAskOrdersCall getGetAskOrdersCall() {
        return GetAskOrdersCall.create(chain.getId());
    }

    Map<String, JO> getOrders(JO response) {
        return response
                .getJoList("askOrders")
                .stream()
                .collect(Collectors.toMap(j -> j.getString("orderFullHash"), Function.identity()));

    }

    private void assertContainsOrder(String comment, String orderHash, JO actual) {
        final Set<String> actualOrderHashes = getOrders(actual).keySet();
        assertTrue(comment + " is missing. Expected hash " + orderHash + ", actual hashes: " + actualOrderHashes, actualOrderHashes.contains(orderHash));
    }

    @SuppressWarnings("SameParameterValue")
    private void assertContainsOrderWithCancellation(String comment, String orderHash, JO actual) {
        final Map<String, JO> orders = getOrders(actual);
        final JO order = orders.get(orderHash);
        assertNotNull(comment + " is missing. Expected hash " + orderHash + ", actual orders: " + orders, order);
        assertTrue(comment + " cancellation is missing. Actual order: " + order, order.getBoolean("expectedCancellation"));
    }

    private void assertExcludesOrder(String comment, String orderHash, JO actual) {
        final Set<String> actualOrderHashes = getOrders(actual).keySet();
        assertFalse(comment + " is present. Expected hash " + orderHash + " to be not present, actual hashes: " + actualOrderHashes, actualOrderHashes.contains(orderHash));
    }

    private String placeAskOrder(long assetId) {
        return PlaceAskOrderCall.create(chain.getId())
                .asset(assetId)
                .secretPhrase(tester.getSecretPhrase())
                .priceNQTPerShare(1)
                .quantityQNT(1)
                .feeNQT(chain.ONE_COIN)
                .callNoError()
                .getString("fullHash");
    }

    private long createAsset() {
        JO callResult = IssueAssetCall.create(chain.getId())
                .name("Asks" + ++assetNumber)
                .decimals(0)
                .quantityQNT(100000)
                .secretPhrase(tester.getSecretPhrase())
                .feeNQT(10 * chain.ONE_COIN)
                .callNoError();
        final long result = Long.parseUnsignedLong(Tester.responseToStringId(callResult));
        generateBlock();
        return result;
    }
}