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

package nxt.peer.helper;

import org.slf4j.helpers.MessageFormatter;

/**
 * <p>Efficient logging adapter.
 *
 * <p>All message parameters are parsed intrinsically by the slf4j {@link MessageFormatter} helper class,
 * so that only pure Strings are logged, avoiding any expensive {@code String.format()} calls.
 * The slf4j algorithm uses StringBuilders only.
 *
 * <p>Note that messages need to be of the form {@code Loggy.warn("Hello {} {}", name, age)}.
 */
public class Loggy {

    public static String format(String msg, Object... objs) {
        return MessageFormatter.arrayFormat(msg, objs).getMessage();
    }

    // Debug
    public static boolean isDebugEnabled() {
        return nxt.util.Logger.isDebugEnabled();
    }
    public static void debug(String s) {
        nxt.util.Logger.logDebugMessage(s);
    }
    public static void debug(String s, Object... objects) {
        nxt.util.Logger.logDebugMessage(format(s, objects));
    }
    public static void debug(String s, Throwable throwable) {
        nxt.util.Logger.logDebugMessage(s, throwable);
    }

    // Info
    public static boolean isInfoEnabled() {
        return nxt.util.Logger.isInfoEnabled();
    }
    public static void info(String s) {
        nxt.util.Logger.logInfoMessage(s);
    }
    public static void info(String s, Object... objects) {
        nxt.util.Logger.logInfoMessage(format(s, objects));
    }
    public static void info(String s, Throwable throwable) {
        nxt.util.Logger.logInfoMessage(s, throwable);
    }

    // Warn
    public static boolean isWarnEnabled() {
        return nxt.util.Logger.isWarningEnabled();
    }
    public static void warn(String s) {
        nxt.util.Logger.logWarningMessage(s);
    }
    public static void warn(String s, Object... objects) {
        nxt.util.Logger.logWarningMessage(format(s, objects));
    }
    public static void warn(String s, Throwable throwable) {
        nxt.util.Logger.logWarningMessage(s, throwable);
    }

    // Error
    public static boolean isErrorEnabled() {
        return nxt.util.Logger.isErrorEnabled();
    }
    public static void error(String s) {
        nxt.util.Logger.logErrorMessage(s);
    }
    public static void error(String s, Object... objects) {
        nxt.util.Logger.logErrorMessage(format(s, objects));
    }
    public static void error(String s, Throwable throwable) {
        nxt.util.Logger.logErrorMessage(s, throwable);
    }

}
