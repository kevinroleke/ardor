/*
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

package com.jelurida.ardor.contracts;

import nxt.addons.JA;
import nxt.addons.JO;
import nxt.http.callers.GetAccountPropertiesCall;
import nxt.http.callers.SetAccountPropertyCall;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static nxt.blockchain.ChildChain.IGNIS;

public class PropertyBasedLotteryTest extends AbstractContractTest {

    @Test
    public void lotteryTest() {
        String contractName = ContractTestHelper.deployContract(PropertyBasedLottery.class);
        String propertyKey = "lottery1";
        JO response = SetAccountPropertyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getRsAccount()).
                property(propertyKey).
                value("").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("setAccountProperty: " + response);

        response = SetAccountPropertyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(CHUCK.getRsAccount()).
                property(propertyKey).
                value("").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("setAccountProperty: " + response);

        response = SetAccountPropertyCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(DAVE.getRsAccount()).
                property(propertyKey).
                value("").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("setAccountProperty: " + response);
        generateBlock();

        // Send a message to trigger the contract execution
        JO messageJson = new JO();
        messageJson.put("contract", contractName);
        JO params = new JO();
        params.put("property", propertyKey);
        messageJson.put("params", params);
        String message = messageJson.toJSONString();
        ContractTestHelper.messageTriggerContract(message, ALICE.getSecretPhrase());
        generateBlock(); // And now the reward transaction is processed

        response = GetAccountPropertiesCall.create().
                setter(ALICE.getRsAccount()).
                property(propertyKey).
                callNoError();
        List<JO> properties = new JA(response.get("properties")).objects();
        int h1 = getHeight() - 1;
        int winners = 0;
        int losers = 0;
        for (JO property : properties) {
            if (("winner:" + h1).equals(property.getString("value"))) {
                winners++;
            } else {
                losers++;
            }
        }
        Assert.assertEquals(1, winners);
        Assert.assertEquals(2, losers);

        // Send another message to trigger another contract execution
        ContractTestHelper.messageTriggerContract(message, ALICE.getSecretPhrase());
        generateBlock(); // And now the reward transaction is processed

        response = GetAccountPropertiesCall.create().
                setter(ALICE.getRsAccount()).
                property(propertyKey).
                callNoError();
        properties = new JA(response.get("properties")).objects();
        winners = 0;
        losers = 0;
        for (JO property : properties) {
            String value = property.getString("value");
            if (value != null && value.startsWith("winner:")) {
                winners++;
            } else {
                losers++;
            }
        }
        Assert.assertEquals(2, winners);
        Assert.assertEquals(1, losers);
    }

}
