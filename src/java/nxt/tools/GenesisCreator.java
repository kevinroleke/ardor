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
package nxt.tools;

import nxt.Constants;
import nxt.Nxt;
import nxt.account.Account;
import nxt.account.BalanceHome;
import nxt.blockchain.Block;
import nxt.blockchain.BlockchainProcessor;
import nxt.blockchain.Chain;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.configuration.Setup;
import nxt.dbschema.Db;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Listener;
import nxt.util.Logger;
import nxt.util.ThreadPool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Usage:
 * - Update or pop-off the blockchain to the height at which a snapshot is needed
 * - Start this script
 * - The JSONs will be saved in the "snapshot" directory in project root
 */
public class GenesisCreator {
    private static final Object sync = new Object();

    public static void main(String[] args) throws InterruptedException {
        ThreadPool.runAfterStart(() -> {
            int height = Nxt.getBlockchain().getHeight();
            GenesisCreator.SnapshotListener listener = new GenesisCreator.SnapshotListener(height);

            Nxt.getBlockchainProcessor().addListener(listener, BlockchainProcessor.Event.AFTER_BLOCK_ACCEPT);
            Nxt.getBlockchainProcessor().scan(height - 1, false);
            Nxt.getBlockchainProcessor().removeListener(listener, BlockchainProcessor.Event.AFTER_BLOCK_ACCEPT);

            Nxt.shutdown();

            // Let the program end
            synchronized (sync) {
                sync.notify();
            }
        });


        Nxt.init(Setup.COMMAND_LINE_TOOL);

        // Wait for the getConstants thread to finish
        synchronized (sync) {
            sync.wait();
        }
    }

    private static class PublicKey {
        private long accountId;
        private byte[] publicKey;
    }

    private static class SnapshotListener implements Listener<Block> {
        private final int height;

        public SnapshotListener(int height) {
            this.height = height;
        }

        @Override
        public void notify(Block block) {
            if (block.getHeight() == height) {
                List<PublicKey> publicKeys = getPublicKeys();
                File destination = new File("snapshot");
                destination.mkdirs();
                snapshotBalances(publicKeys, destination, FxtChain.FXT);
                for (Chain chain : ChildChain.getAll()) {
                    snapshotBalances(publicKeys, destination, chain);
                }
            }
        }
    }

    private static void snapshotBalances(List<PublicKey> publicKeys, File destination, Chain chain) {
        SortedMap<String, Long> balances = new TreeMap<>();
        for (PublicKey publicKey : publicKeys) {
            BalanceHome.Balance balance = chain.getBalanceHome().getBalance(publicKey.accountId);
            if (balance.getBalance() != 0) {
                String key = publicKey.publicKey == null ? Long.toUnsignedString(publicKey.accountId) :
                        Convert.toHexString(publicKey.publicKey);
                balances.put(key, balance.getBalance());
            }
        }
        File file = new File(destination, chain.getName() + "-testnet.json");
        Logger.logInfoMessage("Will save " + balances.size() + " entries to " + file);
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)), true)) {
            JSON.encodeObject(balances, writer);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        Logger.logInfoMessage("Done");
    }

    private static List<PublicKey> getPublicKeys() {
        List<PublicKey> result = new ArrayList<>(Account.getCount());
        try (Connection con = Db.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT account_id, public_key FROM public.public_key WHERE latest = TRUE" +
                     " AND account_id <> " + Constants.BURN_ACCOUNT_ID)) {
            while (rs.next()) {
                PublicKey publicKey = new PublicKey();
                publicKey.accountId = rs.getLong("account_id");
                publicKey.publicKey = rs.getBytes("public_key");
                result.add(publicKey);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }
}
