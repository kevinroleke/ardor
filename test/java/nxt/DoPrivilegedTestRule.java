/*
 * Copyright © 2016-2023 Jelurida IP B.V.
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

package nxt;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * This rule runs all code with privileges.
 */
public class DoPrivilegedTestRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                        try {
                            base.evaluate();
                        } catch (Throwable throwable) {
                            throw new ThrowableWrapper(throwable);
                        }
                        return null;
                    });
                } catch (PrivilegedActionException e) {
                    Exception exception = e.getException();
                    if (exception instanceof ThrowableWrapper) {
                        throw exception.getCause();
                    }
                    throw e;
                }
            }
        };
    }

    private static class ThrowableWrapper extends Exception {
        public ThrowableWrapper(Throwable cause) {
            super(cause);
        }
    }
}
