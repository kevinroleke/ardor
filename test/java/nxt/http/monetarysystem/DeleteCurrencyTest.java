/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2021 Jelurida IP B.V.
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

package nxt.http.monetarysystem;

import nxt.BlockchainTest;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.DeleteCurrencyCall;
import nxt.http.callers.GetAllCurrenciesCall;
import nxt.http.callers.GetCurrencyCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class DeleteCurrencyTest extends BlockchainTest {

    @Test
    public void deleteByIssuer() {
        APICall apiCall = TestCurrencyIssuance.builder("yjwcv", "YJWCV", "test1").build();
        TestCurrencyIssuance.issueCurrencyApi(apiCall);
        generateBlock();
        JO response = GetCurrencyCall.create().code("YJWCV").callNoError();
        String currencyId = response.getString("currency");
        String code = response.getString("code");

        // Delete the currency
        response = DeleteCurrencyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                callNoError();
        Logger.logDebugMessage("deleteCurrencyResponse:" + response);
        generateBlock();
        response = GetCurrencyCall.create().code(code).call();
        Logger.logDebugMessage("getCurrencyResponse:" + response);
        Assert.assertEquals(5, response.getLong("errorCode"));
        Assert.assertEquals("Unknown currency", response.get("errorDescription"));

        // Issue the same currency code again
        apiCall = TestCurrencyIssuance.builder("yjwcv", "YJWCV", "test1").build();
        TestCurrencyIssuance.issueCurrencyApi(apiCall);
        generateBlock();
        response = GetCurrencyCall.create().code("YJWCV").callNoError();
        String newCurrencyId = response.getString("currency");
        String newCode = response.getString("code");
        Assert.assertNotEquals(currencyId, newCurrencyId); // this check may fail once in 2^64 tests
        Assert.assertEquals(code, newCode);
    }

    @Test
    public void deleteByNonOwnerNotAllowed() {
        APICall apiCall = TestCurrencyIssuance.builder("yjwcv", "YJWCV", "test1").build();
        TestCurrencyIssuance.issueCurrencyApi(apiCall);
        generateBlock();
        JO response = GetAllCurrenciesCall.create().callNoError();
        JA currencies = response.getArray("currencies");
        String currencyId = currencies.get(0).getString("currency");
        String code = currencies.get(0).getString("code");

        // Delete the currency
        response = DeleteCurrencyCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                call();
        Logger.logDebugMessage("deleteCurrencyResponse:" + response);
        Assert.assertEquals(8, response.getLong("errorCode"));

        // Verify that currency still exists
        response = GetCurrencyCall.create().code(code).callNoError();
        Assert.assertEquals(currencyId, response.get("currency"));
    }

    @Test
    public void deleteByOwnerNonIssuer() {
        APICall apiCall = TestCurrencyIssuance.builder("yjwcv", "YJWCV", "test1").build();
        TestCurrencyIssuance.issueCurrencyApi(apiCall);
        generateBlock();

        JO response = GetCurrencyCall.create().code("YJWCV").callNoError();
        String currencyId = response.getString("currency");
        String code = response.getString("code");

        // Transfer all units
        response = TransferCurrencyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).feeNQT(IGNIS.ONE_COIN).
                recipient(BOB.getId()).
                currency(currencyId).
                unitsQNT(response.getLong("maxSupplyQNT")).
                callNoError();
        Logger.logDebugMessage("transferCurrencyResponse:" + response);
        generateBlock();

        // Delete the currency
        response = DeleteCurrencyCall.create(IGNIS.getId()).
                secretPhrase(BOB.getSecretPhrase()).feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                callNoError();
        Logger.logDebugMessage("deleteCurrencyResponse:" + response);
        generateBlock();
        response = GetCurrencyCall.create().code(code).call();
        Assert.assertEquals(5, response.getLong("errorCode"));
        Assert.assertEquals("Unknown currency", response.get("errorDescription"));

        // Issue the same currency code again by the original issuer
        apiCall = TestCurrencyIssuance.builder("yjwcv", "YJWCV", "test1").build();
        TestCurrencyIssuance.issueCurrencyApi(apiCall);
        generateBlock();
        response = GetCurrencyCall.create().code("YJWCV").callNoError();
        String newCurrencyId = response.getString("currency");
        String newCode = response.getString("code");
        Assert.assertNotEquals(currencyId, newCurrencyId); // this check may fail once in 2^64 tests
        Assert.assertEquals(code, newCode);
    }

}
