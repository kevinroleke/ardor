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
 
 package nxt.ae;

import nxt.Nxt;
import nxt.NxtException;
import nxt.NxtException.AccountControlException;
import nxt.account.HoldingType;
import nxt.blockchain.ChildChain;
import nxt.blockchain.ChildTransaction;
import nxt.blockchain.ChildTransactionImpl;
import nxt.blockchain.Transaction;
import nxt.blockchain.TransactionType;
import nxt.db.DbClause;
import nxt.db.DbIterator;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.VersionedEntityDbTable;
import nxt.db.VersionedValuesDbTable;
import nxt.dbschema.Db;
import nxt.shuffling.ShufflingCreationAttachment;
import nxt.shuffling.ShufflingTransactionType;
import nxt.util.Convert;
import nxt.util.Logger;
import nxt.voting.PhasingAppendix;
import nxt.voting.PhasingControl;
import nxt.voting.PhasingParams;
import nxt.voting.VoteWeighting;
import nxt.voting.VoteWeighting.VotingModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

public class AssetControl {
    public static final class PhasingOnly extends PhasingControl {

        public static final String DEFAULT_ASSET_CONTROL_VARIABLE = "ASC";

        public static PhasingOnly get(long assetId, AssetControlTxTypesEnum transactionType) {
            return phasingControlTable.getBy(new DbClause.LongClause("asset_id", assetId).
                    and(new DbClause.BitmaskCheckClause("transaction_types_bitmask", transactionType.toLongBitmask())));
        }

        public static PhasingOnly getById(long id) {
            return phasingControlTable.get(phasingControlDbKeyFactory.newKey(id));
        }

        public static List<PhasingOnly> getAllPerAsset(long assetId) {
            List<PhasingOnly> result = new ArrayList<>();
            try (DbIterator<PhasingOnly> it = phasingControlTable.getManyBy(new DbClause.LongClause("asset_id", assetId).
                    and(new DbClause.LongClause("transaction_types_bitmask", DbClause.Op.NE, 0)), 0, -1)) {
                it.forEach(result::add);
                return result;
            }
        }

        public static int getCount() {
            return phasingControlTable.getCount();
        }

        public static DbIterator<PhasingOnly> getAll(int from, int to) {
            return phasingControlTable.getAll(from, to);
        }

        static void set(ChildTransactionImpl transaction, SetPhasingAssetControlAttachment attachment) {
            PhasingParams phasingParams = attachment.getPhasingParams();
            long assetId = attachment.getAssetId();
            Asset asset = Asset.getAsset(assetId);

            for (AssetControlTxTypesEnum t : attachment.getTransactionTypes()) {
                PhasingOnly old = get(assetId, t);
                if (old != null) {
                    old.transactionTypesBitmask &= ~t.toLongBitmask();
                    if (old.transactionTypesBitmask == 0) {
                        phasingControlTable.delete(old);
                        if (!old.params.getSubPolls().isEmpty()) {
                            phasingControlSubPollTable.delete(old);
                        }
                    } else {
                        phasingControlTable.insert(old);
                    }
                }
            }

            if (phasingParams.getVoteWeighting().getVotingModel() == VotingModel.NONE) {
                if (getAllPerAsset(assetId).isEmpty()) {
                    asset.setHasPhasingControl(false);
                }
            } else {
                asset.setHasPhasingControl(true);
                PhasingOnly phasingOnly = new PhasingOnly(assetId, transaction, attachment.getTransactionTypes(), phasingParams);
                phasingControlTable.insert(phasingOnly);

                SortedMap<String, PhasingParams> subPolls = phasingOnly.params.getSubPolls();
                if (!subPolls.isEmpty()) {
                    phasingControlSubPollTable.insert(phasingOnly, subPolls.entrySet().stream().map(
                            entry -> new PhasingOnlySubPoll(transaction, entry.getKey(), entry.getValue())).collect(Collectors.toList()));
                }
            }
        }

        private final DbKey dbKey;
        private final long id;
        private final long assetId;
        private long transactionTypesBitmask;

        private PhasingOnly(long assetId, ChildTransactionImpl transaction, Set<AssetControlTxTypesEnum> transactionTypes, PhasingParams params) {
            super(params);
            this.id = transaction.getId();
            this.assetId = assetId;
            this.transactionTypesBitmask = AssetControlTxTypesEnum.longBitmaskFromSet(transactionTypes);
            dbKey = phasingControlDbKeyFactory.newKey(this.id);
        }

        private PhasingOnly(ResultSet rs, DbKey dbKey) throws SQLException {
            this.id = rs.getLong("id");
            this.assetId = rs.getLong("asset_id");
            this.transactionTypesBitmask = rs.getLong("transaction_types_bitmask");
            this.dbKey = dbKey;

            init(rs, () -> phasingControlSubPollTable.get(phasingControlSubPollDbKeyFactory.newKey(this)));
        }

        public long getId() {
            return id;
        }

        public long getAssetId() {
            return assetId;
        }

        public Set<AssetControlTxTypesEnum> getTransactionTypes() {
            return AssetControlTxTypesEnum.longBitmaskToSet(transactionTypesBitmask);
        }

        @Override
        public final String getControlType() {
            return "asset";
        }

        @Override
        public final String getDefaultControlVariable() {
            return DEFAULT_ASSET_CONTROL_VARIABLE;
        }

        @Override
        protected void checkTransaction(ChildTransaction transaction) throws AccountControlException {

            try {
                params.checkApprovable();
            } catch (NxtException.NotCurrentlyValidException e) {
                Logger.logDebugMessage("Asset control no longer valid: " + e.getMessage());
                return;
            }
            PhasingAppendix phasingAppendix = transaction.getPhasing();
            checkPhasing(phasingAppendix);
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO asset_control_phasing "
                    + "(id, asset_id, transaction_types_bitmask, whitelist, expression, " + PhasingParams.COMMON_COLUMN_NAMES
                    + ", height, latest) KEY (id, height) VALUES (?, ?, ?, ?, ?, " + PhasingParams.COMMON_COLUMN_PARAMETER_MARKERS + ", ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.assetId);
                pstmt.setLong(++i, this.transactionTypesBitmask);
                DbUtils.setArrayEmptyToNull(pstmt, ++i, Convert.toArray(params.getWhitelist()));

                pstmt.setString(++i, params.getExpressionStr());

                i = PhasingParams.setCommonColumnValues(params, pstmt, i);

                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    }

    static final class PhasingOnlySubPoll extends PhasingControl.SubPoll {
        private final long id;

        private PhasingOnlySubPoll(ChildTransactionImpl transaction, String variableName, PhasingParams params) {
            super(variableName, params);
            this.id = transaction.getId();
        }

        private PhasingOnlySubPoll(ResultSet rs) throws SQLException {
            super(rs);
            this.id = rs.getLong("id");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO asset_control_phasing_sub_poll (id, " +
                    "name, whitelist, " + PhasingParams.COMMON_COLUMN_NAMES + ", height, latest) " +
                    "VALUES (?, ?, ?, " + PhasingParams.COMMON_COLUMN_PARAMETER_MARKERS + ", ?, TRUE)")) {
                int i = 0;

                pstmt.setLong(++i, id);
                pstmt.setString(++i, variableName);
                DbUtils.setArray(pstmt, ++i, Convert.toArray(params.getWhitelist()));

                i = PhasingParams.setCommonColumnValues(params, pstmt, i);

                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    }

    private static final DbKey.LongKeyFactory<PhasingOnly> phasingControlDbKeyFactory =
            new DbKey.LongKeyFactory<PhasingOnly>("id") {
        @Override
        public DbKey newKey(PhasingOnly rule) {
            return rule.dbKey;
        }
    };

    private static final VersionedEntityDbTable<PhasingOnly> phasingControlTable = new VersionedEntityDbTable<PhasingOnly>(
            "public.asset_control_phasing", phasingControlDbKeyFactory) {

        @Override
        protected PhasingOnly load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PhasingOnly(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PhasingOnly phasingOnly) throws SQLException {
            phasingOnly.save(con);
        }
    };

    private static final DbKey.LongKeyFactory<PhasingOnly> phasingControlSubPollDbKeyFactory =
            new DbKey.LongKeyFactory<PhasingOnly>("id") {
        @Override
        public DbKey newKey(PhasingOnly rule) {
            return rule.dbKey == null ? newKey(rule.id) : rule.dbKey;
        }
    };

    private static final VersionedValuesDbTable<PhasingOnly, PhasingOnlySubPoll> phasingControlSubPollTable = new VersionedValuesDbTable<PhasingOnly, PhasingOnlySubPoll>(
            "public.asset_control_phasing_sub_poll", phasingControlSubPollDbKeyFactory) {
        @Override
        protected PhasingOnlySubPoll load(Connection con, ResultSet rs) throws SQLException {
            return new PhasingOnlySubPoll(rs);
        }

        @Override
        protected void save(Connection con, PhasingOnly phasingOnly, PhasingOnlySubPoll phasingOnlySubPoll) throws SQLException {
            phasingOnlySubPoll.save(con);
        }
    };

    public static void init() {
    }

    public static void checkTransaction(ChildTransaction transaction) throws NxtException.NotCurrentlyValidException {
        TransactionType transactionType = transaction.getType();
        if (transactionType != ShufflingTransactionType.SHUFFLING_CREATION
                && AssetControlTxTypesEnum.getByTransactionType(transactionType) == null) {
            return;
        }
        long assetId = 0;

        if (transactionType instanceof AssetExchangeTransactionType) {
            AssetExchangeTransactionType<?> aeType = (AssetExchangeTransactionType<?>) transactionType;
            assetId = aeType.getAssetId(transaction);
        } else if (transactionType == ShufflingTransactionType.SHUFFLING_CREATION) {
            ShufflingCreationAttachment attachment = (ShufflingCreationAttachment) transaction.getAttachment();
            if (attachment.getHoldingType() == HoldingType.ASSET) {
                assetId = attachment.getHoldingId();
            }
        }

        if (assetId == 0) {
            return;
        }
        Asset asset = Asset.getAsset(assetId);
        if (asset == null) {
            throw new NxtException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(assetId) + " does not exist yet");
        }
        if (asset.hasPhasingControl()) {
            if (transactionType == ShufflingTransactionType.SHUFFLING_CREATION) {
                throw new NxtException.NotCurrentlyValidException("Shuffling of asset under asset control is not supported");
            }

            PhasingOnly phasingOnly = PhasingOnly.get(assetId, AssetControlTxTypesEnum.getByTransactionType(transaction.getType()));
            if (phasingOnly != null) {
                phasingOnly.checkTransaction(transaction);

                PhasingParams phasingParams = phasingOnly.getPhasingParams();
                if (AssetExchangeTransactionType.DIVIDEND_PAYMENT.equals(transaction.getType())
                        && phasingParams.getVoteWeighting().getVotingModel() == VotingModel.PROPERTY
                        && phasingParams.getRecipientPropertyVoting().getSetterId() != 0) {
                    throw new NxtException.NotCurrentlyValidException("Dividend payment with asset under by-recipient property control is not supported");
                }
            }
        }
    }

    public static void setIdsForDbUpdate() {
        if (PhasingOnly.getCount() > 0) {
            Set<Long> previousIds = new HashSet<>();
            Map<Long, byte[]> fullHashes = getLastSetPhasingTransactionFullHashPerAssetId();
            fullHashes.forEach((assetId, fullHash) -> {
                long id = Convert.fullHashToId(fullHash);
                if (!previousIds.add(id)) {
                    throw new RuntimeException("Transaction hash " + Convert.toHexString(fullHash) + " converts to " +
                            Long.toUnsignedString(id) + " which is already known");
                }
                try (Connection con = phasingControlTable.getConnection();
                     PreparedStatement pstmt = con.prepareStatement("UPDATE asset_control_phasing " +
                             " SET id = ? WHERE asset_id = ?");
                     PreparedStatement pstmtSubPolls = con.prepareStatement("UPDATE asset_control_phasing_sub_poll " +
                             " SET id = ? WHERE asset_id = ?")) {
                    int i = 0;
                    pstmt.setLong(++i, id);
                    pstmt.setLong(++i, assetId);
                    int updatesCount = pstmt.executeUpdate();
                    if (updatesCount == 0) {
                        throw new RuntimeException("Asset control not found for asset ID " + assetId);
                    }
                    if (updatesCount > 1) {
                        Logger.logDebugMessage("" + updatesCount + " asset_control_phasing records updated for " + assetId);
                    }
                    i = 0;
                    pstmtSubPolls.setLong(++i, id);
                    pstmtSubPolls.setLong(++i, assetId);
                    updatesCount = pstmtSubPolls.executeUpdate();
                    if (updatesCount > 0) {
                        Logger.logDebugMessage("" + updatesCount + " asset_control_phasing_sub_poll records updated for " + assetId);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
            });
        }
    }

    private static Map<Long, byte[]> getLastSetPhasingTransactionFullHashPerAssetId() {
        try (Connection con = Db.db.getConnection(ChildChain.IGNIS.getDbSchema());
             PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* from transaction " +
                     "  LEFT JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id" +
                     "  AND transaction.full_hash = phasing_poll_result.full_hash " +
                     " WHERE type = ? AND subtype = ? AND (phased = FALSE OR approved = TRUE) " +
                     " ORDER BY height ASC, transaction_index ASC")) {
            int i = 0;
            pstmt.setByte(++i, AssetExchangeTransactionType.SET_PHASING_CONTROL.getType());
            pstmt.setByte(++i, AssetExchangeTransactionType.SET_PHASING_CONTROL.getSubtype());
            Map<Long, byte[]> result = new HashMap<>();
            try (DbIterator<? extends Transaction> it = Nxt.getBlockchain().getTransactions(ChildChain.IGNIS, con, pstmt)) {
                for (Transaction t : it) {
                    SetPhasingAssetControlAttachment attachment = (SetPhasingAssetControlAttachment) t.getAttachment();
                    if (attachment.getPhasingParams().getVoteWeighting().getVotingModel() == VotingModel.NONE) {
                        result.remove(attachment.getAssetId());
                    } else {
                        result.put(attachment.getAssetId(), t.getFullHash());
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }
}
