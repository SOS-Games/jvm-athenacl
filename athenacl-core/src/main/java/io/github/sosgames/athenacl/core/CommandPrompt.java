// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves one line the way the athenaCL prompt does. A two-letter prefix is
 * capitalized ({@code tpsd 7} becomes {@code TPsd}). {@code ?} is help and
 * {@code !} is shell. A bare prefix such as {@code pi} opens that menu.
 * {@code q} is quit and {@code man} is help. The line is not executed.
 */
public final class CommandPrompt {
    public record Line(String command, String args, String prefix) {
    }

    private static final Set<String> PREFIXES = Set.of(
            "PI", "TM", "TP", "TI", "TE", "TC", "TT", "EO", "EM", "EL", "AO", "AP", "AH", "AU");

    private static final Map<String, String> ALIASES = Map.of(
            "q", "quit",
            "man", "help",
            "AUbug", "AUpc",
            "TMv", "TMv");

    private CommandPrompt() {
    }

    public static List<String> names() {
        List<String> all = new ArrayList<>(CommandsA.names());
        all.addAll(CommandsB.names());
        return List.copyOf(all);
    }

    public static Line parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("no command given");
        }
        String corrected = correct(raw.strip());
        String[] parts = split(corrected);
        String token = parts[0];
        String args = parts[1];
        if (token.endsWith("_") && PREFIXES.contains(token.substring(0, token.length() - 1))) {
            return new Line("menu", args, token.substring(0, token.length() - 1));
        }
        String command = ALIASES.getOrDefault(token, token);
        if (!names().contains(command) && !ALIASES.containsKey(token)) {
            throw new IllegalArgumentException("unknown command: " + raw.strip());
        }
        return new Line(command, args, "");
    }

    static String correct(String line) {
        if (line.length() <= 1) {
            return line;
        }
        int space = line.indexOf(' ');
        String cmd = space < 0 ? line : line.substring(0, space);
        String prefix = cmd.length() >= 2 ? cmd.substring(0, 2).toUpperCase(Locale.ROOT) : cmd.toUpperCase(Locale.ROOT);
        String postfix = cmd.length() > 2 ? cmd.substring(2).toLowerCase(Locale.ROOT) : "";
        String postarg = space < 0 ? "" : line.substring(space);
        if (PREFIXES.contains(prefix)) {
            return prefix + postfix + postarg;
        }
        return line;
    }

    private static String[] split(String line) {
        if (line.charAt(0) == '?') {
            line = "help " + line.substring(1);
        } else if (line.charAt(0) == '!') {
            line = "shell " + line.substring(1);
        }
        int i = 0;
        while (i < line.length() && ident(line.charAt(i))) {
            i++;
        }
        String cmd = line.substring(0, i);
        String arg = line.substring(i).strip();
        if (PREFIXES.contains(cmd)) {
            cmd = cmd + "_";
        }
        return new String[] {cmd, arg};
    }

    private static boolean ident(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
}
