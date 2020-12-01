/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.http.monetarysystem;

import nxt.BlockchainTest;
import nxt.addons.JO;
import nxt.crypto.HashFunction;
import nxt.http.APICall;
import nxt.http.callers.CurrencyMintCall;
import nxt.http.callers.GetCurrencyCall;
import nxt.http.callers.GetMintingTargetCall;
import nxt.ms.CurrencyMinting;
import nxt.ms.CurrencyType;
import nxt.util.Convert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class TestCurrencyMint extends BlockchainTest {

    @Test
    public void mint() {
        APICall apiCall = TestCurrencyIssuance.builder().
                type(CurrencyType.MINTABLE.getCode() | CurrencyType.EXCHANGEABLE.getCode()).
                maxSupplyQNT(10000000).
                initialSupplyQNT(0).
                issuanceHeight(0).
                minDifficulty((byte)2).
                maxDifficulty((byte)8).
                algorithm(HashFunction.SHA256.getId()).
                build();

        String currencyId = TestCurrencyIssuance.issueCurrencyApi(apiCall);
        mintCurrency(currencyId);
    }

    public void mintCurrency(String currencyId) {
        // Failed attempt to mint
        JO mintResponse = CurrencyMintCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                currency( currencyId).
                nonce("123456").
                unitsQNT(1000).
                counter(1).
                call();
        Logger.logDebugMessage("mintResponse: " + mintResponse);
        generateBlock();
        JO getCurrencyResponse = GetCurrencyCall.create().currency(currencyId).callNoError();
        Logger.logDebugMessage("getCurrencyResponse: " + getCurrencyResponse);
        Assert.assertEquals("0", getCurrencyResponse.get("currentSupplyQNT"));

        // Successful attempt
        long units = 10;
        long algorithm = getCurrencyResponse.getLong("algorithm");
        long nonce;
        for (nonce=0; nonce < Long.MAX_VALUE; nonce++) {
            if (CurrencyMinting.meetsTarget(CurrencyMinting.getHash((byte) algorithm, nonce, Convert.parseUnsignedLong(currencyId), units, 1, ALICE.getId()),
                    CurrencyMinting.getTarget(2, 8, units, 0, 100000))) {
                break;
            }
        }
        Logger.logDebugMessage("nonce: " + nonce);
        mintResponse = CurrencyMintCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                feeNQT(IGNIS.ONE_COIN).
                currency(currencyId).
                nonce(Long.toString(nonce)).
                unitsQNT( units).
                counter(1).
                callNoError();
        Logger.logDebugMessage("mintResponse: " + mintResponse);
        generateBlock();
        getCurrencyResponse = GetCurrencyCall.create().currency(currencyId).callNoError();
        Logger.logDebugMessage("getCurrencyResponse: " + getCurrencyResponse);
        Assert.assertEquals("" + units, getCurrencyResponse.get("currentSupplyQNT"));

        JO getMintingTargetResponse = GetMintingTargetCall.create().
                currency(currencyId).
                account(ALICE.getId()).
                unitsQNT(1000).
                callNoError();
        Logger.logDebugMessage("getMintingTargetResponse: " + getMintingTargetResponse);
        Assert.assertEquals("4000", getMintingTargetResponse.get("difficulty"));
        Assert.assertEquals("a9f1d24d62105839b4c876be9f1a2fdd24068195438b6ce7fba9f1d24d621000", getMintingTargetResponse.get("targetBytes"));
    }
}
