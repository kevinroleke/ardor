/*
 * Copyright © 2022-2023 Jelurida IP B.V.
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
package nxt.db;

import nxt.dbschema.Db;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@RunWith(Parameterized.class)
public class VersionedEntityDbTableTest extends BaseDerivedTablesTest {

    private final VersionedEntityDbTable<Value> table;
    public VersionedEntityDbTableTest(Boolean isFastTrimming) {
        this.table = isFastTrimming ? fastTrimVersionedEntityDbTable : nonFastTrimVersionedEntityDbTable;
    }

    @Parameterized.Parameters(name = "fast={0}")
    public static Collection<Boolean[]> fastTrimmingTypes() {
        return Arrays.asList(new Boolean[][] { { true }, { false }});
    }

    @Test
    public void testTrimmingAfterDeletionAndInsertion() {
        logContent();
        long ID = 23L;

        deleteAndInsert(ID);
        generateBlocks(10);

        Db.db.runInDbTransaction(() -> table.trim(getHeight() - 5));
        logContent();

        Value value = table.get(entityKeyFactory.newKey(ID));
        Assert.assertEquals(2, value.value);
    }

    @Test
    public void testPopOffAfterDeletionAndInsertion() {
        Assume.assumeTrue(table == fastTrimVersionedEntityDbTable); //popoff works the same way
        long ID = 24L;

        deleteAndInsert(ID);
        generateBlocks(5);

        //now we insert a 3rd value
        Db.db.runInDbTransaction(() -> table.insert(new Value(ID, 3)));
        logContent();
        generateBlock();

        //then pop-off before that 3rd insertion
        Db.db.runInDbTransaction(() -> table.popOffTo(getHeight() - 2));
        logContent();

        Value value = table.get(entityKeyFactory.newKey(ID));
        Assert.assertEquals(2, value.value);
    }

    private void deleteAndInsert(long id) {
        Value value1 = new Value(id, 1);
        Db.db.runInDbTransaction(() -> table.insert(value1));
        logContent();
        generateBlock();

        Db.db.runInDbTransaction(() -> {
            //we delete the value
            table.delete(value1);
            //and insert another value for same ID
            table.insert(new Value(id, 2));
        });
        logContent();
    }
}