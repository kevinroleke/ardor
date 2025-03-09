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

package nxt.blockchain;

import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.db.DbIterator;
import nxt.db.DbUtils;
import nxt.dbschema.Db;
import nxt.util.Convert;
import nxt.util.Filter;
import nxt.util.ReadWriteUpdateLock;
import nxt.util.security.BlockchainPermission;
import nxt.voting.PhasingPollHome;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class BlockchainImpl implements Blockchain {

    private static final BlockchainImpl instance = new BlockchainImpl();
    private static final BlockchainPermission blockchainPermission = new BlockchainPermission("getBlockchain");

    public static BlockchainImpl getInstance() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(blockchainPermission);
        }
        return instance;
    }

    private BlockchainImpl() {}

    private final ReadWriteUpdateLock lock = new ReadWriteUpdateLock();
    private final AtomicReference<BlockImpl> lastBlock = new AtomicReference<>();

    @Override
    public void readLock() {
        lock.readLock().lock();
    }

    @Override
    public void readUnlock() {
        lock.readLock().unlock();
    }

    @Override
    public void updateLock() {
        lock.updateLock().lock();
    }

    @Override
    public void updateUnlock() {
        lock.updateLock().unlock();
    }

    public void writeLock() {
        lock.writeLock().lock();
    }

    public void writeUnlock() {
        lock.writeLock().unlock();
    }

    @Override
    public BlockImpl getLastBlock() {
        return lastBlock.get();
    }

    void setLastBlock(BlockImpl block) {
        lastBlock.set(block);
    }

    @Override
    public int getHeight() {
        BlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getHeight();
    }

    @Override
    public int getLastBlockTimestamp() {
        BlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getTimestamp();
    }

    @Override
    public BlockImpl getLastBlock(int timestamp) {
        BlockImpl block = lastBlock.get();
        if (timestamp >= block.getTimestamp()) {
            return block;
        }
        return BlockDb.findLastBlock(timestamp);
    }

    @Override
    public BlockImpl getBlock(long blockId) {
        return getBlock(blockId, false);
    }

    @Override
    public BlockImpl getBlock(long blockId, boolean loadTransactions) {
        BlockImpl block = lastBlock.get();
        if (block.getId() == blockId) {
            return block;
        }
        return BlockDb.findBlock(blockId, loadTransactions);
    }

    @Override
    public boolean hasBlock(long blockId) {
        return lastBlock.get().getId() == blockId || BlockDb.hasBlock(blockId);
    }

    @Override
    public DbIterator<BlockImpl> getAllBlocks() {
        Connection con = null;
        readLock();
        try {
            con = BlockDb.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block ORDER BY db_id ASC");
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        } finally {
            readUnlock();
        }
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(int from, int to) {
        Connection con = null;
        readLock();
        try {
            con = BlockDb.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height <= ? AND height >= ? ORDER BY height DESC");
            int blockchainHeight = getHeight();
            pstmt.setInt(1, blockchainHeight - from);
            pstmt.setInt(2, blockchainHeight - to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        } finally {
            readUnlock();
        }
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(long accountId, int timestamp) {
        return getBlocks(accountId, timestamp, 0, -1);
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(long accountId, int timestamp, int from, int to) {
        Connection con = null;
        readLock();
        try {
            con = BlockDb.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE generator_id = ? "
                    + (timestamp > 0 ? " AND timestamp >= ? " : " ") + "ORDER BY height DESC"
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            if (timestamp > 0) {
                pstmt.setInt(++i, timestamp);
            }
            DbUtils.setLimits(++i, pstmt, from, to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        } finally {
            readUnlock();
        }
    }

    @Override
    public int getBlockCount(long accountId) {
        try (Connection con = BlockDb.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM block WHERE generator_id = ?")) {
            pstmt.setLong(1, accountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(Connection con, PreparedStatement pstmt) {
        return new DbIterator<>(con, pstmt, BlockDb::loadBlock);
    }

    @Override
    public List<Long> getBlockIdsAfter(long blockId, int limit) {
        List<Long> result = new ArrayList<>();
        try (Connection con = BlockDb.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block "
                            + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                            + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<BlockImpl> getBlocksAfter(long blockId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<BlockImpl> result = new ArrayList<>();
        readLock();
        try (Connection con = BlockDb.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                        + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                        + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(BlockDb.loadBlock(con, rs, true));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            readUnlock();
        }
        return result;
    }

    @Override
    public List<BlockImpl> getBlocksAfter(long blockId, List<Long> blockList) {
        if (blockList.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlockImpl> result = new ArrayList<>();
        readLock();
        try (Connection con = BlockDb.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                        + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                        + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, blockList.size());
            try (ResultSet rs = pstmt.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    BlockImpl block = BlockDb.loadBlock(con, rs, true);
                    if (block.getId() != blockList.get(index++)) {
                        break;
                    }
                    result.add(block);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            readUnlock();
        }
        return result;
    }

    @Override
    public long getBlockIdAtHeight(int height) {
        Block block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block.getId();
        }
        return BlockDb.findBlockIdAtHeight(height);
    }

    @Override
    public BlockImpl getBlockAtHeight(int height) {
        BlockImpl block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block;
        }
        return BlockDb.findBlockAtHeight(height);
    }

    @Override
    public BlockImpl getECBlock(int timestamp) {
        Block block = getLastBlock(timestamp);
        if (block == null) {
            return getBlockAtHeight(0);
        }
        return BlockDb.findBlockAtHeight(Math.max(block.getHeight() - 720, 0));
    }

    @Override
    public TransactionImpl getTransaction(Chain chain, byte[] fullHash) {
        return chain.getTransactionHome().findTransaction(fullHash);
    }

    @Override
    public boolean hasTransaction(Chain chain, byte[] fullHash) {
        return chain.getTransactionHome().hasTransaction(fullHash);
    }

    @Override
    public FxtTransactionImpl getFxtTransaction(long transactionId) {
        return TransactionHome.findFxtTransaction(transactionId);
    }

    @Override
    public boolean hasFxtTransaction(long transactionId) {
        return TransactionHome.hasFxtTransaction(transactionId);
    }

    @Override
    public int getTransactionCount(Chain chain) {
        return chain.getTransactionHome().getTransactionCount();
    }

    @Override
    public DbIterator<ChildTransactionImpl> getTransactions(ChildChain childChain, long accountId, byte type, byte subtype, int blockTimestamp,
                                                            boolean includeExpiredPrunable) {
        return getTransactions(childChain, accountId, 0, type, subtype, blockTimestamp, false, false, false, 0, -1, includeExpiredPrunable, false);
    }

    @Override
    public DbIterator<ChildTransactionImpl> getTransactions(ChildChain childChain, int numberOfConfirmations, byte type, byte subtype,
                                                       int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                       int from, int to, boolean includeExpiredPrunable, boolean executedOnly) {
        if (phasedOnly && nonPhasedOnly) {
            throw new IllegalArgumentException("At least one of phasedOnly or nonPhasedOnly must be false");
        }
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current blockchain height " + getHeight());
        }
        int lastBlockTimestamp = height < Integer.MAX_VALUE ? getBlockTimestampAtHeight(height) : Integer.MAX_VALUE;
        int subQueryLimit = to <= 0 || to < from || to == Integer.MAX_VALUE ? Integer.MAX_VALUE : to + 1;

        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buildTransactionsSubQuery(true, buf, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, phasedOnly, nonPhasedOnly, executedOnly, subQueryLimit, true);
            buf.append("UNION ALL ");
            buildTransactionsSubQuery(false, buf, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, phasedOnly, nonPhasedOnly, executedOnly, subQueryLimit, true);

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection(childChain.getDbSchema());
            int i = 0;
            PreparedStatement pstmt = con.prepareStatement(buf.toString());
            int prunableExpiration = Math.max(0, Constants.INCLUDE_EXPIRED_PRUNABLE && includeExpiredPrunable ?
                    Nxt.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME :
                    Nxt.getEpochTime() - Constants.MIN_PRUNABLE_LIFETIME);
            i = setTransactionsSubQueryParams(true, pstmt, i, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, prunableExpiration, subQueryLimit);
            i = setTransactionsSubQueryParams(false, pstmt, i, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, prunableExpiration, subQueryLimit);
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(childChain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<FxtTransactionImpl> getTransactions(FxtChain chain, int from, int to) {
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT transaction_fxt.* FROM transaction_fxt ");
            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC ");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection(FxtChain.FXT.getDbSchema());
            PreparedStatement pstmt = con.prepareStatement(buf.toString());
            DbUtils.setLimits(1, pstmt, from, to);
            return getTransactions(chain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<ChildTransactionImpl> getTransactions(ChildChain chain, int from, int to) {
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT transaction.* FROM transaction ");
            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC ");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection(chain.getDbSchema());
            PreparedStatement pstmt = con.prepareStatement(buf.toString());
            DbUtils.setLimits(1, pstmt, from, to);
            return getTransactions(chain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<ChildTransactionImpl> getTransactions(ChildChain childChain, long accountId, int numberOfConfirmations, byte type, byte subtype,
                                                       int blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                       int from, int to, boolean includeExpiredPrunable, boolean executedOnly) {
        if (phasedOnly && nonPhasedOnly) {
            throw new IllegalArgumentException("At least one of phasedOnly or nonPhasedOnly must be false");
        }
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current blockchain height " + getHeight());
        }
        int lastBlockTimestamp = height < Integer.MAX_VALUE ? getBlockTimestampAtHeight(height) : Integer.MAX_VALUE;
        int subQueryLimit = to <= 0 || to < from || to == Integer.MAX_VALUE ? Integer.MAX_VALUE : to + 1;

        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buildTransactionsSubQuery(true, buf, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, phasedOnly, nonPhasedOnly, executedOnly, subQueryLimit, false);
            buf.append("UNION ALL ");
            buildTransactionsSubQuery(false, buf, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, phasedOnly, nonPhasedOnly, executedOnly, subQueryLimit, false);

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection(childChain.getDbSchema());
            int i = 0;
            PreparedStatement pstmt = con.prepareStatement(buf.toString());
            int prunableExpiration = Math.max(0, Constants.INCLUDE_EXPIRED_PRUNABLE && includeExpiredPrunable ?
                    Nxt.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME :
                    Nxt.getEpochTime() - Constants.MIN_PRUNABLE_LIFETIME);
            i = setTransactionsSubQueryParams(true, pstmt, i, accountId, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, prunableExpiration, subQueryLimit);
            i = setTransactionsSubQueryParams(false, pstmt, i, accountId, blockTimestamp, lastBlockTimestamp, type, subtype,
                    withMessage, prunableExpiration, subQueryLimit);
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(childChain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void buildTransactionsSubQuery(boolean isReceivedSubQuery, StringBuilder buf, int firstTimestamp, int lastTimestamp,
                                           byte type, byte subtype, boolean withMessage, boolean phasedOnly,
                                           boolean nonPhasedOnly, boolean executedOnly, int subQueryLimit, boolean noSender) {
        buf.append("(SELECT transaction.* FROM transaction ");
        if (executedOnly && !nonPhasedOnly) {
            buf.append(" LEFT JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
            buf.append(" AND transaction.full_hash = phasing_poll_result.full_hash ");
        }
        if (noSender) {
            buf.append("WHERE 1=1 ");
        } else {
            if (isReceivedSubQuery) {
                buf.append("WHERE recipient_id = ? AND sender_id <> ? ");
            } else {
                buf.append("WHERE sender_id = ? ");
            }
        }
        if (firstTimestamp > 0) {
            buf.append("AND block_timestamp >= ? ");
        }
        if (lastTimestamp < Integer.MAX_VALUE) {
            buf.append("AND block_timestamp <= ? ");
        }
        if (type >= 0) {
            buf.append("AND type = ? ");
            if (subtype >= 0) {
                buf.append("AND subtype = ? ");
            }
        }
        if (withMessage) {
            if (isReceivedSubQuery) {
                buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE ");
            } else {
                buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE OR has_encrypttoself_message = TRUE ");
            }
            buf.append("OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
        }
        if (phasedOnly) {
            buf.append("AND phased = TRUE ");
        } else if (nonPhasedOnly) {
            buf.append("AND phased = FALSE ");
        }
        if (executedOnly && !nonPhasedOnly) {
            buf.append("AND (phased = FALSE OR approved = TRUE)");
        }
        if (noSender) {
            buf.append(" ORDER BY block_timestamp DESC, transaction_index DESC ");
        }
        if (subQueryLimit < Integer.MAX_VALUE) {
            if (noSender) {
                if (isReceivedSubQuery) {
                    buf.append("LIMIT ? ");
                } else {
                    buf.append("LIMIT ? ");
                }
            } else {
                if (isReceivedSubQuery) {
                    buf.append(" ORDER BY recipient_id, block_timestamp DESC LIMIT ? ");
                } else {
                    buf.append(" ORDER BY sender_id, block_timestamp DESC LIMIT ? ");
                }
            }
        }
        buf.append(") ");
    }

    private int setTransactionsSubQueryParams(boolean isReceivedSubQuery, PreparedStatement pstmt, int i,
                                              int firstTimestamp, int lastTimestamp,
                                              byte type, byte subtype, boolean withMessage,
                                              int prunableExpiration, int subQueryLimit) throws SQLException {
        if (firstTimestamp > 0) {
            pstmt.setInt(++i, firstTimestamp);
        }
        if (lastTimestamp < Integer.MAX_VALUE) {
            pstmt.setInt(++i, lastTimestamp);
        }
        if (type >= 0) {
            pstmt.setByte(++i, type);
            if (subtype >= 0) {
                pstmt.setByte(++i, subtype);
            }
        }
        if (withMessage) {
            pstmt.setInt(++i, prunableExpiration);
        }
        if (subQueryLimit < Integer.MAX_VALUE) {
            pstmt.setInt(++i, subQueryLimit);
        }
        return i;
    }

    private int setTransactionsSubQueryParams(boolean isReceivedSubQuery, PreparedStatement pstmt, int i,
                                              long accountId, int firstTimestamp, int lastTimestamp,
                                              byte type, byte subtype, boolean withMessage,
                                              int prunableExpiration, int subQueryLimit) throws SQLException {
        if (isReceivedSubQuery) {
            pstmt.setLong(++i, accountId);
        }
        pstmt.setLong(++i, accountId);
        if (firstTimestamp > 0) {
            pstmt.setInt(++i, firstTimestamp);
        }
        if (lastTimestamp < Integer.MAX_VALUE) {
            pstmt.setInt(++i, lastTimestamp);
        }
        if (type >= 0) {
            pstmt.setByte(++i, type);
            if (subtype >= 0) {
                pstmt.setByte(++i, subtype);
            }
        }
        if (withMessage) {
            pstmt.setInt(++i, prunableExpiration);
        }
        if (subQueryLimit < Integer.MAX_VALUE) {
            pstmt.setInt(++i, subQueryLimit);
        }
        return i;
    }

    private int getBlockTimestampAtHeight(int height) {
        try (Connection con = BlockDb.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT timestamp FROM block WHERE height = ? LIMIT 1")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new RuntimeException("Can't get block timestamp at height " + height + ". Block is missing.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<ChildTransactionImpl> getReferencingTransactions(ChildChain childChain, byte[] referencedTransactionFullHash, int from, int to) {
        Connection con = null;
        try {
            con = Db.db.getConnection(childChain.getDbSchema());
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* FROM transaction "
                    + "WHERE referenced_transaction_id = ? "
                    + "AND referenced_transaction_full_hash = ? "
                    + "ORDER BY block_timestamp DESC, transaction_index DESC "
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, Convert.fullHashToId(referencedTransactionFullHash));
            pstmt.setBytes(++i, referencedTransactionFullHash);
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(childChain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<? extends FxtTransaction> getTransactions(FxtChain chain,
                int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to) {
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current blockchain height " + getHeight());
        }
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT * FROM transaction_fxt WHERE 1=1 ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type < 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND height <= ? ");
            }

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection(FxtChain.FXT.getDbSchema());
            PreparedStatement pstmt;
            int i = 0;
            pstmt = con.prepareStatement(buf.toString());
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type < 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(chain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<? extends FxtTransaction> getTransactions(FxtChain chain, long accountId,
                int numberOfConfirmations, byte type, byte subtype, int blockTimestamp, int from, int to) {
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current blockchain height " + getHeight());
        }
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT * FROM transaction_fxt WHERE recipient_id = ? AND sender_id <> ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type < 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND height <= ? ");
            }
            buf.append("UNION ALL SELECT * FROM transaction_fxt WHERE sender_id = ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type < 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND height <= ? ");
            }

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection(FxtChain.FXT.getDbSchema());
            PreparedStatement pstmt;
            int i = 0;
            pstmt = con.prepareStatement(buf.toString());
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type < 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setInt(++i, blockTimestamp);
            }
            if (type < 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(chain, con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<FxtTransactionImpl> getTransactions(FxtChain chain, Connection con, PreparedStatement pstmt) {
        return new DbIterator<>(con, pstmt, new DbIterator.ResultSetReader<FxtTransactionImpl>() {
            @Override
            public FxtTransactionImpl get(Connection con, ResultSet rs) throws Exception {
                return (FxtTransactionImpl)TransactionImpl.loadTransaction(chain, rs);
            }
        });
    }

    public DbIterator<ChildTransactionImpl> getTransactions(ChildChain childChain, Connection con, PreparedStatement pstmt) {
        return new DbIterator<>(con, pstmt, new DbIterator.ResultSetReader<ChildTransactionImpl>() {
            @Override
            public ChildTransactionImpl get(Connection con, ResultSet rs) throws Exception {
                return (ChildTransactionImpl)TransactionImpl.loadTransaction(childChain, rs);
            }
        });
    }

    @Override
    public List<Transaction> getExpectedTransactions(Filter<Transaction> filter) {
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        List<Transaction> result = new ArrayList<>();
        readLock();
        try {
            for (ChildTransaction phasedTransaction : PhasingPollHome.getFinishingTransactions(getHeight() + 1)) {
                try {
                    phasedTransaction.validate();
                    if (!((ChildTransactionImpl) phasedTransaction).attachmentIsDuplicate(duplicates, false) && filter.ok(phasedTransaction)) {
                        result.add(phasedTransaction);
                    }
                } catch (NxtException.ValidationException ignore) {
                }
            }
            TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions()
                    .stream()
                    .map(UnconfirmedTransaction::getTransaction)
                    .filter(filter::ok)
                    .collect(Collectors.toCollection(() -> result));
        } finally {
            readUnlock();
        }
        return result;
    }

    @Override
    public DbIterator<? extends Transaction> getExecutedTransactions(Chain chain, long senderId, long recipientId,
                                                              byte type, byte subtype,
                                                              int height, int numberOfConfirmations,
                                                              int from, int to) {
        Connection con = null;
        try {
            boolean isChildChain = chain instanceof ChildChain;

            String heightFilter;
            String phasingResultHeightFilter;
            if (height > 0) {
                //use the block_timestamp index because there is no index on transaction.height
                heightFilter = " AND transaction.block_timestamp = (SELECT timestamp FROM block WHERE height = ? LIMIT 1) ";
                phasingResultHeightFilter = " AND phasing_poll_result.height = ? ";
            } else {
                if (senderId == 0 && recipientId == 0) {
                    throw new IllegalArgumentException("Sender or recipient expected");
                }
                if (numberOfConfirmations > 0) {
                    height = getHeight() - numberOfConfirmations;
                    if (height < 0) {
                        throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                                + " exceeds current blockchain height " + getHeight());
                    }
                    heightFilter = " AND transaction.height <= ? ";
                    phasingResultHeightFilter = " AND phasing_poll_result.height <= ? ";
                } else {
                    heightFilter = null;
                    phasingResultHeightFilter = null;
                }
            }

            boolean hasTypeFilter = isChildChain && type >= 0 || !isChildChain && type < 0;
            StringBuilder accountAndTypeFilter = new StringBuilder();
            if (senderId != 0) {
                accountAndTypeFilter.append(" AND transaction.sender_id = ? ");
            }
            if (recipientId != 0) {
                accountAndTypeFilter.append(" AND transaction.recipient_id = ? ");
            }

            if (hasTypeFilter) {
                accountAndTypeFilter.append(" AND transaction.type = ? ");
                if (subtype >= 0) {
                    accountAndTypeFilter.append(" AND transaction.subtype = ? ");
                }
            }

            StringBuilder buf = new StringBuilder();
            if (isChildChain) {
                buf.append("SELECT transaction.*, transaction.height AS execution_height FROM transaction WHERE transaction.phased = FALSE ").append(accountAndTypeFilter);
            } else {
                buf.append("SELECT * FROM transaction_fxt AS transaction WHERE TRUE ").append(accountAndTypeFilter);
            }

            if (heightFilter != null) {
                buf.append(heightFilter);
            }

            if (isChildChain) {
                buf.append("UNION ALL SELECT transaction.*, phasing_poll_result.height AS execution_height FROM transaction ");
                buf.append(" JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
                buf.append("  AND transaction.full_hash = phasing_poll_result.full_hash ");
                buf.append(" WHERE transaction.phased = TRUE AND phasing_poll_result.approved = TRUE ");
                buf.append(accountAndTypeFilter);

                if (heightFilter != null) {
                    buf.append(phasingResultHeightFilter);
                }
                buf.append("ORDER BY execution_height DESC, transaction_index DESC");
            } else {
                buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            }

            buf.append(DbUtils.limitsClause(from, to));

            con = Db.db.getConnection(chain.getDbSchema());
            PreparedStatement pstmt;

            int i = 0;
            pstmt = con.prepareStatement(buf.toString());

            boolean setPhasedTransactionsParameters = false;
            do {
                //loop is executed twice for child chain and once for FXT chain

                if (senderId != 0) {
                    pstmt.setLong(++i, senderId);
                }
                if (recipientId != 0) {
                    pstmt.setLong(++i, recipientId);
                }

                if (hasTypeFilter) {
                    pstmt.setByte(++i, type);
                    if (subtype >= 0) {
                        pstmt.setByte(++i, subtype);
                    }
                }

                if (heightFilter != null) {
                    pstmt.setInt(++i, height);
                }

                if (isChildChain) {
                    setPhasedTransactionsParameters = !setPhasedTransactionsParameters;
                }
            } while (setPhasedTransactionsParameters);

            DbUtils.setLimits(++i, pstmt, from, to);

            if (isChildChain) {
                return getTransactions((ChildChain)chain, con, pstmt);
            } else {
                return getTransactions((FxtChain)chain, con, pstmt);
            }
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }
}
