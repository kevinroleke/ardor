/*
 * Copyright © 2025-2026 Jelurida Swiss SA
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DroppedTransactionStoreTest {

    private DroppedTransactionStore store;
    private static final long MAX_SIZE = 5;

    @Before
    public void setUp() {
        store = new DroppedTransactionStore(MAX_SIZE);
    }

    @Test
    public void testAddAndGet() {
        UtxComparableData data = new UtxComparableData(false,
                false, 0, false, false, 0, 0, 0);
        store.add(1L, data, 100);
        UtxComparableData retrievedData = store.get(1L);
        assertNotNull(retrievedData);
    }

    @Test
    public void testGetNonExistent() {
        UtxComparableData retrievedData = store.get(2L);
        assertNull(retrievedData);
    }

    @Test
    public void testCleanupExpired() {
        UtxComparableData data1 = new UtxComparableData(false, false, 0, false,
                false, 0, 0, 0);
        UtxComparableData data2 = new UtxComparableData(false, false, 0, false,
                false, 0, 0, 0);
        store.add(1L, data1, 100);
        store.add(2L, data2, 200);
        store.cleanup(150);
        assertEquals(1, store.getSize());
        assertNull(store.get(1L));
        assertNotNull(store.get(2L));
    }

    @Test
    public void testCleanupExceedsMaxSize() {
        for (long i = 1; i <= 20; i++) {
            UtxComparableData data = new UtxComparableData(false, false, 0,
                    false, false, 0, 0, 0);
            store.add(i, data, 200 + (int) i);
        }
        store.cleanup(150);
        assertEquals(MAX_SIZE, store.getSize());
    }

    @Test
    public void testInsertingSameIdTwice() {
        UtxComparableData data = new UtxComparableData(false,
                false, 0, false, false, 0, 0, 0);
        store.add(1L, data, 100);
        store.add(1L, data, 100);
        Assert.assertEquals(1, store.getSize());
    }
}
