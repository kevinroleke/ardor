/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
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

package nxt.migration;

import nxt.Constants;
import nxt.Nxt;
import nxt.account.Account;
import nxt.account.HoldingType;
import nxt.blockchain.Block;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Genesis;
import nxt.db.DbIterator;
import nxt.dbschema.Db;
import nxt.freeze.FreezeMonitor;
import nxt.util.Convert;
import nxt.util.Listener;
import nxt.util.Logger;
import nxt.util.ResourceLookup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class HoldingMigrateBlockEventHandler implements Listener<Block> {
    private final HoldingSnapshot holdingSnapshot;

    public HoldingMigrateBlockEventHandler(HoldingSnapshot holdingSnapshot) {
        this.holdingSnapshot = holdingSnapshot;
    }

    @Override
    public void notify(Block block) {
        try (DbIterator<HoldingMigration> targets = HoldingMigration.getMigrations(block.getHeight())) {
            for (HoldingMigration target : targets) {
                Db.db.runInDbTransaction(() -> handle(target));
            }
        }
    }

    private void handle(HoldingMigration target) {
        HoldingType holdingType = target.getHoldingType();
        long holdingId = target.getHoldingId();
        if (holdingType != holdingSnapshot.getHoldingType()) {
            return;
        }
        if (holdingType != HoldingType.COIN) {
            if (!FreezeMonitor.isFrozen(holdingType, holdingId)) {
                Logger.logErrorMessage(String.format("%s %s is not frozen, will not migrate to a child chain!!!",
                        holdingType.name(), Long.toUnsignedString(holdingId)));
                return;
            }
        }
        Map<String, Long> snapshot = holdingSnapshot.getSnapshot(target);
        ChildChain childChain = target.getChildChain();
        Set<Long> conflictingAccounts = loadPublicKeys(childChain);
        redirectConflictingAccounts(snapshot, conflictingAccounts, childChain);
        Genesis.loadBalances(childChain, snapshot);
        loadAliases(childChain, conflictingAccounts);
        if (ChildChain.NXT == childChain) {
            loadNxtCurrencies(conflictingAccounts);
        }
    }

    private void redirectConflictingAccounts(Map<String, Long> snapshot, Set<Long> conflictingAccounts, ChildChain childChain) {
        Logger.logDebugMessage("Redirecting holdings for %d conflicting accounts", conflictingAccounts.size());
        long totalRedirected = 0;
        
        // Use iterator to traverse and remove conflicting accounts
        Iterator<Map.Entry<String, Long>> iterator = snapshot.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String key = entry.getKey();
            long accountId;
            
            // Determine if key is a public key (hex, 64 chars) or account ID
            if (key.length() == 64) {
                byte[] publicKey = Convert.parseHexString(key);
                accountId = Account.getId(publicKey);
            } else {
                accountId = Long.parseUnsignedLong(key);
            }
            
            if (conflictingAccounts.contains(accountId)) {
                long quantity = entry.getValue();
                totalRedirected += quantity;
                iterator.remove();
                Logger.logDebugMessage("Redirecting %.8f %s from conflicting account %s to collisions redirect account",
                        BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(childChain.ONE_COIN)), childChain.getName(), Long.toUnsignedString(accountId));
            }
        }
        
        // Add accumulated balance to collisions redirect account
        if (totalRedirected > 0) {
            String redirectAccountKey = Long.toUnsignedString(Constants.COLLISIONS_REDIRECT_ACCOUNT_ID);
            snapshot.merge(redirectAccountKey, totalRedirected, Long::sum);
            Logger.logDebugMessage("Total redirected: %.8f %s to account %s", BigDecimal.valueOf(totalRedirected).divide(BigDecimal.valueOf(childChain.ONE_COIN)),
                    childChain.getName(), Long.toUnsignedString(Constants.COLLISIONS_REDIRECT_ACCOUNT_ID));
        }
    }

    private Set<Long> loadPublicKeys(ChildChain childChain) {
        String filePath = "data/" + childChain.getName() + "_PUBLIC_KEY" + (Constants.isTestnet ? "-testnet.json" : ".json");
        try ( InputStream is = ResourceLookup.getSystemResourceAsStream(filePath) ) {
            if (is == null) {
                Logger.logDebugMessage("No " + childChain.getName() + " public keys found");
                return Collections.emptySet();
            } else {
                try ( InputStreamReader isr = new InputStreamReader(is) ) {
                    return loadPublicKeys(childChain, isr);
                }
            }
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Failed to process child chain " + childChain.getName() + " public keys", e);
        }
    }

    private Set<Long> loadPublicKeys(ChildChain childChain, InputStreamReader is) throws IOException, ParseException {
        JSONArray json = (JSONArray) JSONValue.parseWithException(is);
        Logger.logDebugMessage(String.format("Loading %d public keys for %s", json.size(), childChain.getName()));
        int newCount = 0;
        int existingCount = 0;
        Set<Long> conflictingAccounts = new HashSet<>();
        for (Object jsonPublicKey : json) {
            byte[] publicKey = Convert.parseHexString((String)jsonPublicKey);
            long accountId = Account.getId(publicKey);

            // Check if account already has a public key
            byte[] existingPublicKey = Account.getPublicKey(accountId);
            if (existingPublicKey != null) {
                if (Arrays.equals(existingPublicKey, publicKey)) {
                    // Public key already exists and matches
                    existingCount++;
                } else {
                    // Public key conflict - account exists with different public key
                    Logger.logWarningMessage(String.format(
                        "Public key conflict for account %s: existing key %s differs from imported key %s. Redirecting holdings.",
                        Long.toUnsignedString(accountId),
                        Convert.toHexString(existingPublicKey),
                        Convert.toHexString(publicKey)
                    ));
                    conflictingAccounts.add(accountId);
                }
            } else {
                // New public key, safe to apply
                Account account = Account.addOrGetAccount(accountId);
                account.apply(publicKey);
                if (++newCount % Constants.BATCH_COMMIT_SIZE == 0) {
                    Db.db.commitTransaction();
                    Db.db.clearCache();
                }
            }
        }
        Logger.logDebugMessage("Loaded %d public keys for %s (%d already existed, %d conflicts redirected)",
                               newCount, childChain.getName(), existingCount, conflictingAccounts.size());
        return conflictingAccounts;
    }

    private void loadAliases(ChildChain childChain, Set<Long> conflictingAccounts) {
        String filePath = "data/" + childChain.getName() + "_ALIASES" + (Constants.isTestnet ? "-testnet.json" : ".json");
        try ( InputStream is = ResourceLookup.getSystemResourceAsStream(filePath) ) {
            if (is == null) {
                Logger.logDebugMessage("No " + childChain.getName() + " aliases found");
            } else {
                try ( InputStreamReader isr = new InputStreamReader(is) ) {
                    JSONObject aliases = (JSONObject) JSONValue.parseWithException(isr);
                    Genesis.loadAliases(childChain, aliases, conflictingAccounts);
                }
            }
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Failed to process child chain " + childChain.getName() + " aliases", e);
        }
    }

    private void loadNxtCurrencies(Set<Long> conflictingAccounts) {
        try (InputStreamReader is = new InputStreamReader(
                ResourceLookup.getSystemResourceAsStream("data/NXT_CURRENCIES" + (Constants.isTestnet ? "-testnet.json" : ".json")), StandardCharsets.UTF_8)) {
            JSONObject currencies = (JSONObject) JSONValue.parseWithException(is);
            long nextCurrencyId = Nxt.getBlockchain().getLastBlock().getId() + 1;
            Genesis.loadCurrencies(ChildChain.NXT, currencies, nextCurrencyId, conflictingAccounts);
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process currencies", e);
        }
    }
}
