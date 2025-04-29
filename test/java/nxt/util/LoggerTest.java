/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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
package nxt.util;

import nxt.Nxt;
import nxt.NxtException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Handler;

public class LoggerTest {

    private String defaultConfigurationBackup;

    public static class TestException extends Exception {
        public TestException() {
        }

        public TestException(String message) {
            super(message);
        }
    }

    @Before
    public void setUp() {
        defaultConfigurationBackup = Nxt.getStringProperty("nxt.disabledThrowableStackTraces");
        Logger.setDisabledStackTraces("{\"nxt.NxtException$NotCurrentlyValidException\":" +
                "  [ \"Invalid bid order\", \"Block ID at ecBlockHeight\" ]," +
                "\"nxt.util.LoggerTest$TestException\": []}");
    }

    @After
    public void destroy() {
        Logger.setDisabledStackTraces(defaultConfigurationBackup);
    }

    @Test
    public void testStacktraceFilter() {
        Logger.logDebugMessage("test1", new NxtException.NotCurrentlyValidException("Invalid bid order " + 123452345));
        Assert.assertEquals(2, getLastMessage().split("\n").length);
        
        String message = "Block ID at ecBlockHeight " + 31231 + " etc.";
        Logger.logDebugMessage("test2", new NxtException.NotCurrentlyValidException(message));
        String[] lines = getLastMessage().split("\n");
        Assert.assertEquals(2, lines.length);
        Assert.assertTrue(lines[0].contains("test2"));
        Assert.assertTrue(lines[1].contains(message));

        Logger.logDebugMessage("test3", new NxtException.NotCurrentlyValidException("Other"));
        Assert.assertTrue(getLastMessage().split("\n").length > 2);

        Logger.logDebugMessage("test4", new TestException("anything"));
        Assert.assertEquals(2, getLastMessage().split("\n").length);

        Logger.logDebugMessage("test5", new TestException());
        Assert.assertEquals(2, getLastMessage().split("\n").length);
    }

    private String getLastMessage() {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("");
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            if (handler instanceof MemoryHandler) {
                return ((MemoryHandler) handler).getMessages(1).get(0);
            }
        }
        throw new RuntimeException("No MemoryHandler");
    }
}
