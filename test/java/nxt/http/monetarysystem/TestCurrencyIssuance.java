/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.http.monetarysystem;

import nxt.BlockchainTest;
import nxt.Tester;
import nxt.addons.JO;
import nxt.http.APICall;
import nxt.http.callers.GetCurrencyCall;
import nxt.http.callers.IssueCurrencyCall;
import nxt.ms.CurrencyType;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCurrencyIssuance extends BlockchainTest {

    public static final Tester CREATOR = ALICE;
    public static final int INITIAL_SUPPLY_QNT = 100000;
    private static final int[] FEE_STEPS = new int[] { 0, 0, 0, 25000, 1000, 40};

    public static IssueCurrencyCall builder() {
        return builder("Test1", "TSXXX", "Test Currency 1");
    }

    public static IssueCurrencyCall builder(String name, String code, String description) {
        return IssueCurrencyCall.create(IGNIS.getId())
                .secretPhrase(CREATOR.getSecretPhrase())
                .name(name)
                .code(code)
                .description(description)
                .type(CurrencyType.EXCHANGEABLE.getCode())
                .maxSupplyQNT(100000)
                .initialSupplyQNT(INITIAL_SUPPLY_QNT)
                .issuanceHeight(0)
                .algorithm((byte)0)
                .feeNQT(FEE_STEPS[code.length()] * IGNIS.ONE_COIN);
    }

    @Test
    public void issueCurrency() {
        issueCurrencyImpl();
    }

    @Test
    public void issueCurrencyNoBroadcast() {
        JO issueCurrencyResponse = builder().broadcast(false).secretPhrase(null).publicKey(ALICE.getPublicKey()).callNoError();
    }

    public String issueCurrencyImpl() {
        APICall apiCall = builder().build();
        return issueCurrencyApi(apiCall);
    }

    @Test
    public void issueMultipleCurrencies() {
        APICall apiCall = builder("axcc", "AXCC", "Currency A").build();
        issueCurrencyApi(apiCall);
        apiCall = builder("bXbx", "BXBX", "Currency B").feeNQT(1000 * IGNIS.ONE_COIN).build();
        issueCurrencyApi(apiCall);
        apiCall = builder("ccXcc", "CCCXC", "Currency C").feeNQT(40 * IGNIS.ONE_COIN).build();
        issueCurrencyApi(apiCall);
        JO response = GetCurrencyCall.create(IGNIS.getId()).code("BXBX").callNoError();
        Assert.assertEquals("bXbx", response.get("name"));
    }

    static String issueCurrencyApi(APICall apiCall) {
        JO issueCurrencyResponse = apiCall.getJsonResponse();
        String currencyId = Tester.responseToStringId(issueCurrencyResponse);
        generateBlock();

        JO getCurrencyResponse = GetCurrencyCall.create(IGNIS.getId()).currency(currencyId).callNoError();
        Assert.assertEquals(currencyId, getCurrencyResponse.get("currency"));
        return currencyId;
    }
}
