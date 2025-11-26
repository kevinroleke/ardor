/*
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

package nxt.addons;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Block;
import nxt.blockchain.BlockchainProcessor;
import nxt.blockchain.ChildChain;
import nxt.dbschema.Db;
import nxt.http.APIServlet;
import nxt.http.APITag;
import nxt.http.JSONResponses;
import nxt.http.ParameterParser;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Listener;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

public final class JPLSnapshot implements AddOn {

    public APIServlet.APIRequestHandler getAPIRequestHandler() {
        return new JPLSnapshotAPI("newGenesisAccounts", new APITag[] {APITag.ADDONS}, "height", "isNxtSnapshot");
    }

    public String getAPIRequestType() {
        return "downloadJPLSnapshot";
    }


    /**
     * <p>The downloadJPLSnapshot API can be used to generate a genesis block JSON for a clone to satisfy the JPL 10% sharedrop
     * requirement to existing Ignis or NXT holders.</p>
     *
     * <p>This utility takes a snapshot of account balances and public keys on the Ignis child chain (by default) or the NXT child
     * chain (if isNxtSnapshot=true) as of the specified height, scales down the balance of each account proportionately so that the
     * total of balances of sharedrop accounts is equal to 10% of the total of all balances, and merges this data with the supplied
     * new genesis accounts and balances. (If the total of new genesis account balances is zero, the balance of sharedrop accounts is
     * left unchanged.)</p>
     *
     * <p>Note that using a height more than 800 blocks in the past will normally require a blockchain rescan, which takes a
     * few hours to complete. Do not interrupt this process.</p>
     *
     * <p>Request parameters</p>
     * <ul><li>newGenesisAccounts - a JSON formatted file containing all new account public keys and balances to be included
     * in the clone genesis block</li>
     * <li>height - the Ardor blockchain height at which to take the snapshot</li>
     * <li>isNxtSnapshot - (optional) boolean, set to true to snapshot NXT child chain instead of Ignis; defaults to false</li>
     * </ul>
     *
     * <p>Response</p>
     * <ul><li>For Ignis snapshots: A JSON formatted file, FXT.json, containing a mapping of public keys to their initial balances,
     * for both new accounts and sharedrop accounts, which should be placed in the conf/data directory of the clone blockchain,
     * renamed to the clone parent chain token.</li>
     * <li>For NXT snapshots: A JSON formatted file, genesisAccounts.json, containing separate "balances" and "publicKeys" sections,
     * which should be placed in the conf/data directory of the clone blockchain.</li>
     * </ul>
     *
     * <p>Input file format</p>
     * The input file should contain a mapping of public keys to their initial balances, for the accounts to which the remaining
     * 90% of the tokens will be distributed, i.e. the non-sharedrop accounts. If an existing account is included here, it will
     * receive the specified balance in addition to the sharedrop. If no input file is provided, or the total balance of new
     * accounts in the input file is zero, no adjustment of existing account balances is done.
     *
     * Here is an example input file, which allocates 300M each to the accounts with passwords "0", "1" and "2",
     * for a total of 900M to new accounts, resulting in 100M automatically allocated to existing Ignis holders:
     * <pre>
     * {
     *      "bf0ced0472d8ba3df9e21808e98e61b34404aad737e2bae1778cebc698b40f37": 30000000000000000,
     *      "39dc2e813bb45ff063a376e316b10cd0addd7306555ca0dd2890194d37960152": 30000000000000000,
     *      "011889a0988ccbed7f488878c62c020587de23ebbbae9ba56dd67fd9f432f808": 30000000000000000
     * }
     * </pre>
     *
     * The example below, suitable to use for generating a testnet genesis block, distributes 900M tokens to 10 developers testnet
     * accounts, and 100M to existing Ignis holders:
     * <pre>
     * {
     *   "8d84ea81a017e8d87cc3f7f908a5c3dab027f460ca131ee726216d257120656c":9000000000000000,
     *   "930d01153ba45a794a896d5fae118ee8edcddbcd860c2d3ccc5f717e8c8b433e":9000000000000000,
     *   "584486d2ba4dbd7eaeadd071f9f8c3593cee620e1e374033551147d68899b529":9000000000000000,
     *   "5962b0c12905dc5448367a676a668a076606d921497447e0f61fc329c2da1f21":9000000000000000,
     *   "cc045e5f29aaa5a8464ff3b284e6763a9a37f72cccad8f5a671a73ee61733830":9000000000000000,
     *   "a172465db52bd3f19067527e4ae05679b9b6155e1533037ea4097a56d1b56a33":9000000000000000,
     *   "622d84aacd2edf1aac0c2c068486c144ea6b0f4cb7671d4b00f7b6aa528d5b2b":9000000000000000,
     *   "5b70849aa5e480e2737dce4a439e0893e909229eeade3d2d973cf150be8dea2e":9000000000000000,
     *   "27c4fa81aaf58d41775287d86ed560b54e5426e7e9f6c686834b3eaa9f43db3b":9000000000000000,
     *   "bfbce25fe31e375c1784ef932cdd9daf8f4635e25092ea4cb60d15e220d06a1d":9000000000000000
     * }
     * </pre>
     *
     */
    public static class JPLSnapshotAPI extends APIServlet.APIRequestHandler {

        private JPLSnapshotAPI(String fileParameter, APITag[] apiTags, String... origParameters) {
            super(fileParameter, apiTags, origParameters);
        }

        @Override
        protected JSONStreamAware processRequest(HttpServletRequest request, HttpServletResponse response) throws NxtException {
            int height = ParameterParser.getHeight(request);
            if (height <= 0 || height > Nxt.getBlockchain().getHeight()) {
                return JSONResponses.INCORRECT_HEIGHT;
            }
            boolean isNxtSnapshot = "true".equalsIgnoreCase(request.getParameter("isNxtSnapshot"));
            JSONObject inputJSON = new JSONObject();
            ParameterParser.FileData fileData = ParameterParser.getFileData(request, "newGenesisAccounts", false);
            if (fileData != null) {
                String input = Convert.toString(fileData.getData());
                if (!input.trim().isEmpty()) {
                    try {
                        inputJSON = (JSONObject) JSONValue.parseWithException(input);
                    } catch (ParseException e) {
                        return JSONResponses.INCORRECT_FILE;
                    }
                }
            }
            JPLSnapshotListener listener = new JPLSnapshotListener(height, inputJSON, isNxtSnapshot);
            new Thread(() -> {
                Nxt.getBlockchainProcessor().addListener(listener, BlockchainProcessor.Event.AFTER_BLOCK_ACCEPT);
                Nxt.getBlockchainProcessor().scan(height - 1, false);
                Nxt.getBlockchainProcessor().removeListener(listener, BlockchainProcessor.Event.AFTER_BLOCK_ACCEPT);
            }).start();
            StringBuilder sb = new StringBuilder(1024);
            JSON.encodeObject(listener.getSnapshot(), sb);
            String filename = isNxtSnapshot ? "genesisAccounts.json" : "FXT.json";
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            response.setContentLength(sb.length());
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter writer = response.getWriter()) {
                writer.write(sb.toString());
            } catch (IOException e) {
                return JSONResponses.RESPONSE_WRITE_ERROR;
            }
            return null;
        }

        @Override
        protected JSONStreamAware processRequest(HttpServletRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean requirePost() {
            return true;
        }

        @Override
        protected boolean requirePassword() {
            return true;
        }

        @Override
        protected boolean requireFullClient() {
            return true;
        }

        @Override
        protected boolean allowRequiredBlockParameters() {
            return false;
        }

        @Override
        protected boolean isChainSpecific() {
            return false;
        }
    }

    private static class JPLSnapshotListener implements Listener<Block> {

        private final int height;
        private final JSONObject inputJSON;
        private final boolean isNxtSnapshot;
        private final SortedMap<String, Object> snapshot = new TreeMap<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        private JPLSnapshotListener(int height, JSONObject inputJSON, boolean isNxtSnapshot) {
            this.height = height;
            this.inputJSON = inputJSON;
            this.isNxtSnapshot = isNxtSnapshot;
        }

        @Override
        public void notify(Block block) {
            if (block.getHeight() == height) {
                SortedMap<String, Long> snapshotBalances = snapshotBalances();
                Logger.logInfoMessage("Snapshot contains %d %s balances", snapshotBalances.size(), isNxtSnapshot ? "NXT" : "Ignis");
                BigInteger snapshotTotal = BigInteger.valueOf(snapshotBalances.values().stream().mapToLong(Long::longValue).sum());
                Logger.logInfoMessage("Snapshot total is %d", Convert.longValueExact(snapshotTotal));
                BigInteger inputTotal = BigInteger.valueOf(inputJSON.values().stream().mapToLong(value -> (Long) value).sum());
                if (!inputTotal.equals(BigInteger.ZERO)) {
                    snapshotBalances.entrySet().forEach(entry -> {
                        long snapshotBalance = entry.getValue();
                        long adjustedBalance = Convert.longValueExact(BigInteger.valueOf(snapshotBalance).multiply(inputTotal)
                                .divide(snapshotTotal).divide(BigInteger.valueOf(9)));
                        entry.setValue(adjustedBalance);
                    });
                }
                SortedMap<String, String> snapshotPublicKeys = snapshotPublicKeys();
                Logger.logInfoMessage("Snapshot contains " + snapshotPublicKeys.size() + " account public keys");
                Logger.logInfoMessage("Adding " + inputJSON.size() + " input accounts");
                inputJSON.forEach((key, value) -> {
                    String inputPublicKey = (String)key;
                    long inputBalance = (Long)value;
                    String account = Long.toUnsignedString(Account.getId(Convert.parseHexString(inputPublicKey)));
                    String snapshotPublicKey = snapshotPublicKeys.putIfAbsent(account, inputPublicKey);
                    if (snapshotPublicKey != null && !snapshotPublicKey.equals(inputPublicKey)) {
                        throw new RuntimeException("Public key collision, input " + inputPublicKey + ", snapshot contains " + snapshotPublicKey);
                    }
                    snapshotBalances.merge(account, inputBalance, (a, b) -> a + b);
                });

                if (isNxtSnapshot) {
                    // Assemble NXT format output (dual-section)
                    JSONArray publicKeys = new JSONArray();
                    publicKeys.addAll(snapshotPublicKeys.values());
                    snapshot.put("publicKeys", publicKeys);
                    snapshot.put("balances", snapshotBalances);
                } else {
                    snapshotBalances.forEach((key, value) -> {
                        String publicKey = snapshotPublicKeys.get(key);
                        snapshot.put(publicKey != null ? publicKey : key, value);
                    });
                }

                latch.countDown();
            }
        }

        private SortedMap<String, Object> getSnapshot() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e.getMessage(), e);
            }
            return snapshot;
        }

        private SortedMap<String, String> snapshotPublicKeys() {
            SortedMap<String, String> map = new TreeMap<>();
            try (Connection con = Db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT public_key FROM public_key WHERE public_key IS NOT NULL "
                         + "AND height <= ? ORDER by account_id")) {
                pstmt.setInt(1, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        byte[] publicKey = rs.getBytes("public_key");
                        long accountId = Account.getId(publicKey);
                        map.put(Long.toUnsignedString(accountId), Convert.toHexString(publicKey));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            return map;
        }

        private SortedMap<String, Long> snapshotBalances() {
            SortedMap<String, Long> map = new TreeMap<>();
            ChildChain childChain = isNxtSnapshot ? ChildChain.NXT : ChildChain.IGNIS;
            try (Connection con = Db.db.getConnection(childChain.getDbSchema());
                 PreparedStatement pstmt = con.prepareStatement("SELECT account_id, balance FROM balance WHERE " +
                         "balance > 0 AND LATEST=true AND account_id <> " + Constants.BURN_ACCOUNT_ID)) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        long accountId = rs.getLong("account_id");
                        long balance = rs.getLong("balance");
                        String account = Long.toUnsignedString(accountId);
                        map.put(account, balance);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            return map;
        }
    }
}
