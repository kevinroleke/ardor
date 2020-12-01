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

package com.jelurida.ardor.contracts;

import nxt.Tester;
import nxt.addons.JO;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.TriggerContractByRequestCall;
import nxt.http.callers.TriggerContractByVoucherCall;
import nxt.util.Convert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class NewAccountFaucetTest extends AbstractContractTest {

    @Test
    public void fundNewAccountFromVoucher() {
        String contractName = setupContract();
        generateBlock();
        Tester newGuy = new Tester("rule chase pound passion whistle odd tumble joy howl reason crack turn");
        // newGuy generates a voucher
        JO voucher = new JO(getVoucher(newGuy));

        // newGuy requests funding from the contract
        JO response = TriggerContractByVoucherCall.create().
                parts("voucher", Convert.toBytes(voucher.toJSONString())).
                contractName(contractName).callNoError();
        Logger.logDebugMessage("triggerContractByVoucher: " + response);
        generateBlock();

        // new Guy account is funded
        Assert.assertEquals(-500000000 - (100000000 + 100000000 + 202000000), ALICE.getChainBalanceDiff(2)); // SendMoney + fee + new account fee + deploy contract fee
        Assert.assertEquals(500000000, newGuy.getChainBalanceDiff(2)); // Faucet received
        Assert.assertEquals(402000000, FORGY.getChainBalanceDiff(2)); // Forging reward

        // newGuy requests funding from the contract again
        response = TriggerContractByVoucherCall.create().
                parts("voucher", Convert.toBytes(voucher.toJSONString())).
                contractName(contractName).call();
        Logger.logDebugMessage("triggerContractByVoucher: " + response);

        // No luck since the account already has a public key
        Assert.assertEquals(10001L, response.get("errorCode"));
    }

    @Test
    public void fundNewAccountFromRequest() {
        String contractName = setupContract();
        generateBlock();
        Tester newGuy = new Tester("rule chase pound passion whistle odd tumble joy howl reason crack turn");

        // newGuy requests funding from the contract
        JO response = TriggerContractByRequestCall.create()
                .contractName("NewAccountFaucet")
                .setParamValidation(false)
                .param("recipientPublicKey", newGuy.getPublicKeyStr())
                .param("chain", IGNIS.getId())
                .callNoError();
        Logger.logDebugMessage("triggerContractByRequest: " + response);
        generateBlock();

        // new Guy account is funded
        Assert.assertEquals(-500000000 - (100000000 + 100000000 + 202000000), ALICE.getChainBalanceDiff(2)); // SendMoney + fee + new account fee + deploy contract fee
        Assert.assertEquals(500000000, newGuy.getChainBalanceDiff(2)); // Faucet received
        Assert.assertEquals(402000000, FORGY.getChainBalanceDiff(2)); // Forging reward

        // newGuy requests funding from the contract again
        response = TriggerContractByRequestCall.create()
                .contractName("NewAccountFaucet")
                .setParamValidation(false)
                .param("recipientPublicKey", newGuy.getPublicKeyStr())
                .param("chain", IGNIS.getId())
                .call();
        Logger.logDebugMessage("triggerContractByVoucher: " + response);

        // No luck since the account already has a public key
        Assert.assertEquals(10001L, response.get("errorCode"));
    }

    @Test
    public void failToFundExistingAccount() {
        String contractName = setupContract();

        // BOB generates a voucher
        JO response = getVoucher(BOB);

        // Bob requests funding from the contract
        response = TriggerContractByVoucherCall.create().
                parts("voucher", Convert.toBytes(response.toJSONString())).
                contractName(contractName).call();

        // Funding failed since Bob's account already has a public key
        Logger.logDebugMessage("triggerContractByVoucher: " + response);
        Assert.assertEquals(10001L, response.get("errorCode"));
    }

    @Test
    public void enforceThreshold() {
        String contractName = setupContract();
        generateBlock();

        // contracts.json sets threshold of 11 IGNIS over 1440 blocks
        String baseSecretPhrase = "rule chase pound passion whistle odd tumble joy howl reason crack turn";
        JO paymentFromFaucet = getPaymentFromFaucet(contractName, baseSecretPhrase + "1");
        Assert.assertNotNull(paymentFromFaucet.get("transactions"));
        paymentFromFaucet = getPaymentFromFaucet(contractName, baseSecretPhrase + "2");
        Assert.assertNotNull(paymentFromFaucet.get("transactions"));
        paymentFromFaucet = getPaymentFromFaucet(contractName, baseSecretPhrase + "3");
        Assert.assertNotNull(paymentFromFaucet.get("transactions"));
        paymentFromFaucet = getPaymentFromFaucet(contractName, baseSecretPhrase + "4");
        Assert.assertEquals(10001L, paymentFromFaucet.get("errorCode"));

        // Now wait for the faucet to replenish its supply
        generateBlocks(10);
        paymentFromFaucet = getPaymentFromFaucet(contractName, baseSecretPhrase + "5");
        Assert.assertNotNull(paymentFromFaucet.get("transactions"));
    }

    private String setupContract() {
        JO setupParams = new JO();
        setupParams.put("chain", 2);
        setupParams.put("thresholdAmountNQT", 1100000000);
        setupParams.put("thresholdBlocks", 10);
        setupParams.put("faucetAmountNQT", 500000000);
        String contractName = NewAccountFaucet.class.getSimpleName();
        ContractTestHelper.deployContract(NewAccountFaucet.class, setupParams);
        return contractName;
    }

    private JO getPaymentFromFaucet(String contractName, String baseSecretPhrase) {
        Tester newGuy = new Tester(baseSecretPhrase);
        JO voucher = getVoucher(newGuy);
        JO response = TriggerContractByVoucherCall.create().
                parts("voucher", Convert.toBytes(voucher.toJSONString())).
                contractName(contractName).callNoError();
        Logger.logDebugMessage("triggerContractByVoucher: " + response);
        generateBlock();
        return response;
    }

    private JO getVoucher(Tester newGuy) {
        JO response = SendMoneyCall.create(IGNIS.getId()).
                secretPhrase(newGuy.getSecretPhrase()).
                publicKey(ALICE.getPublicKeyStr()).
                recipient(newGuy.getRsAccount()).
                amountNQT(100 * IGNIS.ONE_COIN). // ignored by the contract
                voucher(true).
                feeNQT(0).
                callNoError();
        Logger.logDebugMessage("sendMoney voucher: " + response);
        return response;
    }


}
