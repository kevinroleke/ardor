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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RunWith(Parameterized.class)
public class VersionedValuesDbTableTest extends BaseDerivedTablesTest {

    private final VersionedValuesDbTable<Long, Value> table;
    public VersionedValuesDbTableTest(Boolean isFastTrimming) {
        this.table = isFastTrimming ? fastTrimVersionedValuesTable : nonFastTrimVersionedValuesTable;
    }

    @Parameterized.Parameters(name = "fast={0}")
    public static Collection<Boolean[]> fastTrimmingTypes() {
        return Arrays.asList(new Boolean[][] { { true }, { false }});
    }

    @Test
    public void testTrimmingAfterDeletionAndInsertion() {
        long ID = 13L;
        deleteAndInsert(ID);
        generateBlocks(10);

        Db.db.runInDbTransaction(() -> table.trim(getHeight() - 5));
        logContent();

        List<Value> result = table.get(valuesKeyFactory.newKey(ID));
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(2, result.get(0).value);
    }

    @Test
    @Ignore("This case is not fixed, so we should be careful not to delete and insert at same height." +
            "Or find a way to save the values with MERGE instead of INSERT")
    public void testPopOffAfterDeletionAndInsertion() {
        Assume.assumeTrue(table == fastTrimVersionedValuesTable); //popoff works the same way
        long ID = 14L;

        deleteAndInsert(ID);
        generateBlocks(5);

        //now we insert a 3rd value
        Db.db.runInDbTransaction(() -> table.insert(ID, Collections.singletonList(new Value(3))));
        logContent();
        generateBlock();

        //then pop-off before that 3rd insertion
        Db.db.runInDbTransaction(() -> table.popOffTo(getHeight() - 2));
        logContent();

        List<Value> result = table.get(valuesKeyFactory.newKey(ID));
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(2, result.get(0).value);
    }

    private void deleteAndInsert(long id) {
        //we have some value(s) inserted in the past
        Db.db.runInDbTransaction(() -> table.insert(id, Collections.singletonList(new Value(1))));
        logContent();
        generateBlock();

        Db.db.runInDbTransaction(() -> {
            //we delete the value
            table.delete(id);
            //and insert another value for same ID
            table.insert(id, Collections.singletonList(new Value(2)));
        });
        logContent();
    }

}