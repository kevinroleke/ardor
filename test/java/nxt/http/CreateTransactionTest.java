/*
 * Copyright © 2016-2020 Jelurida IP B.V.
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

import nxt.ae.AssetExchangeTransactionType;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONStreamAware;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateTransactionTest {

    private static final APITag[] TAG = {APITag.CREATE_TRANSACTION};

    private static class DummyCreateTransaction extends CreateTransaction {
        DummyCreateTransaction(TransactionType transactionType, APITag[] apiTags, String... parameters) {
            super(transactionType, apiTags, parameters);
        }

        DummyCreateTransaction(List<TransactionType> transactionTypes, APITag[] apiTags, String... parameters) {
            super(transactionTypes, apiTags, parameters);
        }

        protected DummyCreateTransaction(List<TransactionType> transactionTypes,
                                         List<String> fileParameters,
                                         APITag[] apiTags, String... parameters) {
            super(transactionTypes, fileParameters, apiTags, parameters);
        }

        @Override
        protected JSONStreamAware processRequest(HttpServletRequest request) {
            return null; // no op
        }
    }
    @Test(expected = IllegalArgumentException.class)
    public void emptyTransactionTypeList() {
        new DummyCreateTransaction(Collections.emptyList(), TAG);
    }

    @Test
    public void testCommonParametersNoExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(AssetExchangeTransactionType.ASSET_ISSUANCE, TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonParameters());
        expectedParameters.add("chain");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getParameters()));
    }

    @Test
    public void testCommonParametersOneExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(AssetExchangeTransactionType.ASSET_ISSUANCE, TAG,
                "dummy1");
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonParameters());
        expectedParameters.add("chain");
        expectedParameters.add("dummy1");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getParameters()));
    }

    @Test
    public void testCommonParametersThreeExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(AssetExchangeTransactionType.ASSET_ISSUANCE, TAG,
                "dummy1", "dummy2", "dummy3");
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonParameters());
        expectedParameters.add("chain");
        expectedParameters.add("dummy1");
        expectedParameters.add("dummy2");
        expectedParameters.add("dummy3");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getParameters()));
    }

    @Test
    public void testRecipientParametersNoExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(AssetExchangeTransactionType.ASSET_TRANSFER, TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonParameters());
        expectedParameters.addAll(CreateTransaction.getRecipientParameters());
        expectedParameters.add("chain");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getParameters()));
    }

    @Test
    public void testRecipientParametersOneExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(AssetExchangeTransactionType.ASSET_TRANSFER, TAG,
                "dummy1");
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonParameters());
        expectedParameters.addAll(CreateTransaction.getRecipientParameters());
        expectedParameters.add("chain");
        expectedParameters.add("dummy1");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getParameters()));
    }

    @Test
    public void testRecipientParametersThreeExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(AssetExchangeTransactionType.ASSET_TRANSFER, TAG,
                "dummy1", "dummy2", "dummy3");
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonParameters());
        expectedParameters.addAll(CreateTransaction.getRecipientParameters());
        expectedParameters.add("chain");
        expectedParameters.add("dummy1");
        expectedParameters.add("dummy2");
        expectedParameters.add("dummy3");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getParameters()));
    }

    @Test
    public void testCommonFileParametersNoExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(
                Collections.singletonList(AssetExchangeTransactionType.ASSET_ISSUANCE), Collections.emptyList(), TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonFileParameters());
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getFileParameters()));
    }

    @Test
    public void testCommonFileParametersOneExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(
                Collections.singletonList(AssetExchangeTransactionType.ASSET_ISSUANCE),
                Collections.singletonList("dummyFile1"), TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonFileParameters());
        expectedParameters.add("dummyFile1");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getFileParameters()));
    }

    @Test
    public void testCommonFileParametersThreeExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(
                Collections.singletonList(AssetExchangeTransactionType.ASSET_ISSUANCE),
                Arrays.asList("dummyFile1", "dummyFile2", "dummyFile3"), TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonFileParameters());
        expectedParameters.add("dummyFile1");
        expectedParameters.add("dummyFile2");
        expectedParameters.add("dummyFile3");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getFileParameters()));
    }

    @Test
    public void testRecipientFileParametersNoExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(
                Collections.singletonList(AssetExchangeTransactionType.ASSET_TRANSFER), Collections.emptyList(), TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonFileParameters());
        expectedParameters.addAll(CreateTransaction.getRecipientFileParameters());
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getFileParameters()));
    }

    @Test
    public void testRecipientFileParametersOneExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(
                Collections.singletonList(AssetExchangeTransactionType.ASSET_TRANSFER),
                Collections.singletonList("dummyFile1"), TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonFileParameters());
        expectedParameters.addAll(CreateTransaction.getRecipientFileParameters());
        expectedParameters.add("dummyFile1");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getFileParameters()));
    }

    @Test
    public void testRecipientFileParametersThreeExtra() {
        DummyCreateTransaction handler = new DummyCreateTransaction(
                Collections.singletonList(AssetExchangeTransactionType.ASSET_TRANSFER),
                Arrays.asList("dummyFile1", "dummyFile2", "dummyFile3"), TAG);
        Set<String> expectedParameters = new HashSet<>(CreateTransaction.getCommonFileParameters());
        expectedParameters.addAll(CreateTransaction.getRecipientFileParameters());
        expectedParameters.add("dummyFile1");
        expectedParameters.add("dummyFile2");
        expectedParameters.add("dummyFile3");
        Assert.assertEquals(expectedParameters, new HashSet<>(handler.getFileParameters()));
    }
}
