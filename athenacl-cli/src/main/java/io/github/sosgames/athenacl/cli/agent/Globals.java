// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Flags that belong on every command. They are peeled off before picocli runs so
 * {@code --json} and {@code -vv} work either before or after the command name.
 * Flags that belong to one command, such as {@code schema --out}, stay in {@code rest}
 * for picocli.
 */
final class Globals {
    private static final Set<String> VALUE_FLAGS = Set.of(
            "--out", "--formats", "--where", "--runs", "--bars", "--seed", "--soundbank", "--cache", "--key",
            "--groove", "--meter");
    boolean json;
    boolean dryRun;
    boolean strict;
    boolean verbose;
    boolean veryVerbose;
    boolean helpRequested;
    String logLevel;
    String error;
    final List<String> rest = new ArrayList<>();

    static Globals parse(String[] args) {
        Globals g = new Globals();
        if (args == null) {
            return g;
        }
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--json" -> g.json = true;
                case "--dry-run" -> g.dryRun = true;
                case "--strict" -> g.strict = true;
                case "-v" -> g.verbose = true;
                case "-vv" -> g.veryVerbose = true;
                case "-h", "--help" -> g.helpRequested = true;
                case "--log-level" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                        g.error = "usage: --log-level <error|warn|info|debug|trace>";
                        return g;
                    }
                    g.logLevel = args[++i];
                }
                default -> {
                    if (token.startsWith("--log-level=")) {
                        g.logLevel = token.substring("--log-level=".length());
                        if (g.logLevel.isEmpty()) {
                            g.error = "usage: --log-level <error|warn|info|debug|trace>";
                            return g;
                        }
                    } else {
                        g.rest.add(token);
                    }
                }
            }
        }
        return g;
    }

    /** First word that is not a flag. Null when the line has no command. */
    static String firstCommand(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (token.startsWith("--") && token.contains("=")) {
                continue;
            }
            if (VALUE_FLAGS.contains(token)) {
                i++;
                continue;
            }
            if (token.startsWith("-")) {
                continue;
            }
            return token;
        }
        return null;
    }

    /**
     * The command word when it is not one we know. Null when the line is empty,
     * or when it starts with a real command and the failure is some other usage error.
     */
    static String unknownCommand(String[] args) {
        String name = firstCommand(args == null ? List.of() : List.of(args));
        if (name == null) {
            return null;
        }
        return CommandCatalog.byName(name) == null ? name : null;
    }
}
