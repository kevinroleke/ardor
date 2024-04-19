/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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
package com.jelurida.ardor.client.api;

import nxt.account.Account;
import nxt.account.HoldingType;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.http.callers.GetAllShufflingsCall;
import nxt.shuffling.ShufflingStage;
import nxt.util.Convert;

import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * List all accounts funded using shuffling
 */
public class ShufflingRecipients {
    private static final String LINE_FORMAT = "%-65s| %-26s | %s\n";

    public static void main(String[] args) throws MalformedURLException {
        URL serverUrl = new URL("http://localhost:27876/nxt"); // Point to any Ardor full node with open API support
        String adminPassword = ""; // If not specified, limits output to 100 results
        JO result = GetAllShufflingsCall.create(ChildChain.IGNIS.getId()).finishedOnly("true").adminPassword(adminPassword).remote(serverUrl).call();
        List<JO> shufflings = result.getArray("shufflings").objects();

        PrintStream out = System.out;
        out.println("Shuffling recipients");
        out.printf(LINE_FORMAT, "Transaction Id", "Recipient", "Amount");
        shufflings.forEach(s -> {
            String amountNQT = s.getString("amount");
            String amountIgnis;
            if (amountNQT.length() > 8) {
                amountIgnis = amountNQT.substring(0, amountNQT.length() - 8);
            } else {
                amountIgnis = "0";
            }
            long amount = Long.parseLong(amountIgnis);
            if (s.getByte("holdingType") == HoldingType.COIN.getCode()
                    && s.getString("holding").equals("2") // Ignis only
                    && amount > 10 // Ignore dust amounts
                    && s.getByte("stage") == ShufflingStage.DONE.getCode()) {
                String shufflingFullHash = s.getString("shufflingFullHash"); // This is the transaction full hash that you can lookup on block explorers
                s.getArray("recipientPublicKeys").values().forEach(recipientPublicKey -> {
                    long accountId = Account.getId(Convert.parseHexString(recipientPublicKey));
                    out.printf(LINE_FORMAT, shufflingFullHash, Convert.rsAccount(accountId), amount);
                });
            }
        });
    }
}