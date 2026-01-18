/*
 * Copyright © 2022-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
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
package nxt.db;

import nxt.BlockchainTest;
import nxt.Helper;
import nxt.Nxt;
import nxt.blockchain.BlockchainImpl;
import nxt.blockchain.BlockchainProcessorImpl;
import nxt.configuration.Setup;
import nxt.configuration.SubSystem;
import nxt.dbschema.Db;
import nxt.util.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static nxt.configuration.SubSystem.ADDONS;
import static nxt.configuration.SubSystem.API_SERVER;
import static nxt.configuration.SubSystem.BLOCKCHAIN;
import static nxt.configuration.SubSystem.DB;
import static nxt.configuration.SubSystem.LOGGER;
import static nxt.configuration.SubSystem.PEER_NETWORKING;
import static nxt.configuration.SubSystem.RANDOMIZATION;
import static nxt.configuration.SubSystem.THREAD_POOL;

public class BaseDerivedTablesTest extends BlockchainTest {
    public static final String SCHEMA = "AUTO_TEST";
    public static final String TABLE = "test_values";
    public static final String INDEX = "test_values_id_height_idx";


    static class Value {
        long id;
        final int value;
        int height;

        Value(int value) {
            this.value = value;
        }

        Value(long id, int value) {
            this.id = id;
            this.value = value;
        }

        private Value(ResultSet rs) throws SQLException {
            this.id = rs.getLong("id");
            this.value = rs.getInt("value");
            this.height = rs.getInt("height");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Value value1 = (Value) o;
            return id == value1.id && value == value1.value && height == value1.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, value, height);
        }
    }

    final static DbKey.LongKeyFactory<Value> entityKeyFactory = new DbKey.LongKeyFactory<Value>("id") {
        @Override
        public DbKey newKey(Value value) {
            return newKey(value.id);
        }
    };

    final static DbKey.LongKeyFactory<Long> valuesKeyFactory = new DbKey.LongKeyFactory<Long>("id") {
        @Override
        public DbKey newKey(Long id) {
            return newKey(id.longValue());
        }
    };

    static VersionedEntityDbTable<Value> fastTrimVersionedEntityDbTable;
    static VersionedEntityDbTable<Value> nonFastTrimVersionedEntityDbTable;
    static VersionedValuesDbTable<Long, Value> fastTrimVersionedValuesTable;
    static VersionedValuesDbTable<Long, Value> nonFastTrimVersionedValuesTable;

    static class TestVersionedEntityDbTable extends VersionedEntityDbTable<Value> {
        protected TestVersionedEntityDbTable() {
            super(SCHEMA + "." + TABLE, entityKeyFactory);
        }

        @Override
        protected Value load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Value(rs);
        }

        @Override
        protected void save(Connection con, Value value) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO " + SCHEMA + "." + TABLE +
                    " (id, value, height, latest) KEY (id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;

                pstmt.setLong(++i, value.id);
                pstmt.setInt(++i, value.value);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    }
    static class TestVersionedValuesDbTable extends VersionedValuesDbTable<Long, Value> {
        protected TestVersionedValuesDbTable() {
            super(SCHEMA + "." + TABLE, valuesKeyFactory);
        }

        @Override
        protected Value load(Connection con, ResultSet rs) throws SQLException {
            return new Value(rs);
        }

        @Override
        protected void save(Connection con, Long id, Value value) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO " + SCHEMA + "." + TABLE +
                    " (id, value, height, latest) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;

                pstmt.setLong(++i, id);
                pstmt.setInt(++i, value.value);
                pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }
    }

    private final static SubSystem TEST_DB = new SubSystem() {
        @Override
        public void init() {
            try (Connection con = Db.getConnection();
                 Statement stmt = con.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
                stmt.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA + "." + TABLE +
                        " (db_id IDENTITY, id BIGINT NOT NULL, value INT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
                stmt.execute("CREATE INDEX IF NOT EXISTS " + SCHEMA + "." + INDEX + " ON " + SCHEMA + "." + TABLE + " (id, height DESC)");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            fastTrimVersionedEntityDbTable = new TestVersionedEntityDbTable();
            nonFastTrimVersionedEntityDbTable = new TestVersionedEntityDbTable();
            nonFastTrimVersionedEntityDbTable.isFastTrimEnabled = false;

            fastTrimVersionedValuesTable = new TestVersionedValuesDbTable();
            nonFastTrimVersionedValuesTable = new TestVersionedValuesDbTable();
            nonFastTrimVersionedValuesTable.isFastTrimEnabled = false;
        }

        @Override
        public void shutdown() {
            try (Connection con = Db.getConnection();
                 Statement stmt = con.createStatement()) {
                stmt.execute("DROP INDEX IF EXISTS " + SCHEMA + "." + INDEX);
                stmt.execute("DROP TABLE IF EXISTS " + SCHEMA + "." + TABLE);
                stmt.execute("DROP SCHEMA IF EXISTS " + SCHEMA);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    };
    private final static Setup DB_UNIT_TEST = new Setup() {
        private final List<SubSystem> initSequence = Arrays.asList(LOGGER, DB, TEST_DB, BLOCKCHAIN, PEER_NETWORKING, ADDONS, THREAD_POOL, API_SERVER, RANDOMIZATION);
        private final List<SubSystem> shutdownSequence = Arrays.asList(ADDONS, RANDOMIZATION, API_SERVER, THREAD_POOL, BLOCKCHAIN, PEER_NETWORKING, DB, LOGGER);

        @Override
        public List<SubSystem> initSequence() {
            return initSequence;
        }

        @Override
        public List<SubSystem> shutdownSequence() {
            return shutdownSequence;
        }
    };

    @BeforeClass
    public static void init() {
        Properties properties = BlockchainTest.createTestProperties();
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            Nxt.init(DB_UNIT_TEST, properties);
            return null;
        });
        blockchain = BlockchainImpl.getInstance();
        blockchainProcessor = BlockchainProcessorImpl.getInstance();
        initBlockchainTest();
    }

    @AfterClass
    public static void shutdown() {
        BlockchainTest.shutdownNxt();
    }

    static void logContent() {
        Logger.logDebugMessage("at " + Nxt.getBlockchain().getHeight() + ": " + Helper.executeQuery("select * from " + SCHEMA + "." + TABLE));
    }
}
