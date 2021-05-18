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
import nxt.account.Account;
import nxt.addons.JA;
import nxt.addons.JO;
import nxt.crypto.Crypto;
import nxt.http.APICall;
import nxt.http.callers.CurrencyReserveIncreaseCall;
import nxt.http.callers.GetCurrencyCall;
import nxt.http.callers.GetCurrencyFoundersCall;
import nxt.ms.CurrencyType;
import nxt.util.Convert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCurrencyReserveAndClaim extends BlockchainTest {

    @Test
    public void reserveIncrease() {
        APICall apiCall = TestCurrencyIssuance.builder().
                type(CurrencyType.RESERVABLE.getCode() | CurrencyType.EXCHANGEABLE.getCode()).
                issuanceHeight(baseHeight + 5).
                minReservePerUnitNQT(1).
                initialSupplyQNT(0).
                reserveSupplyQNT(100000).
                build();
        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall);
        reserveIncreaseImpl(currencyId, ALICE.getSecretPhrase(), BOB.getSecretPhrase());
    }

    @Test
    public void cancelCrowdFunding() {
        APICall apiCall1 = TestCurrencyIssuance.builder().
                type(CurrencyType.RESERVABLE.getCode() | CurrencyType.EXCHANGEABLE.getCode()).
                issuanceHeight(baseHeight + 4).
                minReservePerUnitNQT(11).
                initialSupplyQNT(0).
                reserveSupplyQNT(100000).
                build();
        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall1);
        long balanceNQT1 = ALICE.getChainBalance(IGNIS.getId());
        long balanceNQT2 = BOB.getChainBalance(IGNIS.getId());
        reserveIncreaseImpl(currencyId, ALICE.getSecretPhrase(), BOB.getSecretPhrase());
        generateBlockWithDescription("cancellation of crowd funding because of insufficient funds");
        JO getFoundersResponse = GetCurrencyFoundersCall.create().currency(currencyId).callNoError();
        Logger.logMessage("getFoundersResponse: " + getFoundersResponse);
        Assert.assertEquals(0, getFoundersResponse.getArray("founders").size());
        Assert.assertEquals(balanceNQT1 - IGNIS.ONE_COIN, ALICE.getChainBalance(IGNIS.getId()));
        Assert.assertEquals(balanceNQT2 - 2* IGNIS.ONE_COIN, BOB.getChainBalance(IGNIS.getId()));
    }

    @Test
    public void crowdFundingDistribution() {
        APICall apiCall = TestCurrencyIssuance.builder().
                type(CurrencyType.RESERVABLE.getCode() | CurrencyType.EXCHANGEABLE.getCode()).
                initialSupplyQNT(0).
                reserveSupplyQNT(100000).
                issuanceHeight(baseHeight + 4).
                minReservePerUnitNQT(10).
                build();

        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall);
        long balanceNQT1 = ALICE.getChainBalance(IGNIS.getId());
        long balanceNQT2 = BOB.getChainBalance(IGNIS.getId());
        reserveIncreaseImpl(currencyId, ALICE.getSecretPhrase(), BOB.getSecretPhrase());
        generateBlockWithDescription("distribution of currency to founders");
        Assert.assertEquals(20000, ALICE.getCurrencyUnits(Convert.parseAccountId(currencyId)));
        Assert.assertEquals(80000, BOB.getCurrencyUnits(Convert.parseAccountId(currencyId)));
        Assert.assertEquals(balanceNQT1 - IGNIS.ONE_COIN - 200000 + (100000*10), ALICE.getChainBalance(IGNIS.getId()));
        Assert.assertEquals(balanceNQT2 - 2* IGNIS.ONE_COIN - 800000, BOB.getChainBalance(IGNIS.getId()));
    }

    @Test
    public void crowdFundingDistributionRounding() {
        APICall apiCall = TestCurrencyIssuance.builder().
                type(CurrencyType.RESERVABLE.getCode() | CurrencyType.EXCHANGEABLE.getCode()).
                initialSupplyQNT(0).
                reserveSupplyQNT(24).
                maxSupplyQNT(24).
                issuanceHeight(baseHeight + 4).
                minReservePerUnitNQT(10).
                build();

        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall);
        long balanceNQT1 = ALICE.getChainBalance(IGNIS.getId());
        long balanceNQT2 = BOB.getChainBalance(IGNIS.getId());
        long balanceNQT3 = CHUCK.getChainBalance(IGNIS.getId());
        reserveIncreaseImpl(currencyId, BOB.getSecretPhrase(), CHUCK.getSecretPhrase());
        generateBlockWithDescription("distribution of currency to founders");

        // account 2 balance round(24 * 0.2) = round(4.8) = 4
        // account 3 balance round(24 * 0.8) = round(19.2) = 19
        // issuer receives the leftover of 1
        Assert.assertEquals(4, BOB.getCurrencyUnits(Convert.parseAccountId(currencyId)));
        Assert.assertEquals(19, CHUCK.getCurrencyUnits(Convert.parseAccountId(currencyId)));
        Assert.assertEquals(1, ALICE.getCurrencyUnits(Convert.parseAccountId(currencyId)));
        Assert.assertEquals(balanceNQT1 + 24 * 10, ALICE.getChainBalance(IGNIS.getId()));
        Assert.assertEquals(balanceNQT2 - IGNIS.ONE_COIN - 24 * 2, BOB.getChainBalance(IGNIS.getId()));
        Assert.assertEquals(balanceNQT3 - 2 * IGNIS.ONE_COIN - 24 * 8, CHUCK.getChainBalance(IGNIS.getId()));

        JO response = GetCurrencyCall.create().currency(currencyId).callNoError();
        Assert.assertEquals("24", response.get("currentSupplyQNT"));
    }

    private void reserveIncreaseImpl(String currencyId, String secret1, String secret2) {
        JO reserveIncreaseResponse = CurrencyReserveIncreaseCall.create(IGNIS.getId()).
                secretPhrase(secret1).
                feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                amountPerUnitNQT(2).
                callNoError();
        Logger.logMessage("reserveIncreaseResponse: " + reserveIncreaseResponse);
        generateBlock();

        // Two increase reserve transactions in the same block
        reserveIncreaseResponse = CurrencyReserveIncreaseCall.create(IGNIS.getId()).
                secretPhrase(secret2).
                feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                amountPerUnitNQT(3).
                callNoError();
        Logger.logMessage("reserveIncreaseResponse: " + reserveIncreaseResponse);

        reserveIncreaseResponse = CurrencyReserveIncreaseCall.create(IGNIS.getId()).
                secretPhrase(secret2).
                feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                amountPerUnitNQT(5).
                callNoError();
        Logger.logMessage("reserveIncreaseResponse: " + reserveIncreaseResponse);

        generateBlock();

        JO getFoundersResponse = GetCurrencyFoundersCall.create().currency(currencyId).callNoError();
        Logger.logMessage("getFoundersResponse: " + getFoundersResponse);

        JA founders = getFoundersResponse.getArray("founders");
        JO founder1 = founders.get(0);
        Assert.assertTrue(Long.toUnsignedString(Account.getId(Crypto.getPublicKey(Crypto.getPrivateKey(secret1)))).equals(founder1.get("account")) ||
                Long.toUnsignedString(Account.getId(Crypto.getPublicKey(Crypto.getPrivateKey(secret2)))).equals(founder1.get("account")));
        Assert.assertTrue(String.valueOf(3L + 5L).equals(founder1.get("amountPerUnitNQT")) || String.valueOf(2L).equals(founder1.get("amountPerUnitNQT")));

        JO founder2 = founders.get(1);
        Assert.assertTrue(Long.toUnsignedString(Account.getId(Crypto.getPublicKey(Crypto.getPrivateKey(secret1)))).equals(founder2.get("account")) ||
                Long.toUnsignedString(Account.getId(Crypto.getPublicKey(Crypto.getPrivateKey(secret2)))).equals(founder2.get("account")));
        Assert.assertTrue(String.valueOf(3L + 5L).equals(founder2.get("amountPerUnitNQT")) || String.valueOf(2L).equals(founder2.get("amountPerUnitNQT")));
    }

}
