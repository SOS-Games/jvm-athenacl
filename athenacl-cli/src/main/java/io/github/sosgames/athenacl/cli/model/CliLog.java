// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.io.PrintStream;
import java.util.Locale;

/**
 * Stderr log for the CLI. Level names match {@code java.lang.System.Logger}, but lines are
 * written here so a JDK console handler cannot leak them onto stdout. Default level is warn,
 * so a normal command is silent. {@code -v} is debug, {@code -vv} is trace, and
 * {@code --log-level} wins when both are set.
 */
public final class CliLog {
    public enum Level {
        ERROR, WARN, INFO, DEBUG, TRACE
    }

    private static volatile Level current = Level.WARN;
    private static volatile PrintStream err = System.err;

    private CliLog() {
    }

    public static void configure(Level level, PrintStream stream) {
        current = level == null ? Level.WARN : level;
        err = stream == null ? System.err : stream;
    }

    public static Level resolve(String logLevel, boolean verbose, boolean veryVerbose) {
        if (logLevel != null && !logLevel.isBlank()) {
            return switch (logLevel.toLowerCase(Locale.ROOT)) {
                case "error" -> Level.ERROR;
                case "warn", "warning" -> Level.WARN;
                case "info" -> Level.INFO;
                case "debug" -> Level.DEBUG;
                case "trace" -> Level.TRACE;
                default -> throw new IllegalArgumentException("unknown log level: " + logLevel);
            };
        }
        if (veryVerbose) {
            return Level.TRACE;
        }
        if (verbose) {
            return Level.DEBUG;
        }
        return Level.WARN;
    }

    public static void debug(String category, String message) {
        log(Level.DEBUG, category, message);
    }

    public static void trace(String category, String message) {
        log(Level.TRACE, category, message);
    }

    public static void log(Level level, String category, String message) {
        if (level.ordinal() > current.ordinal()) {
            return;
        }
        err.println(level.name() + " " + category + " " + message);
        err.flush();
    }
}
