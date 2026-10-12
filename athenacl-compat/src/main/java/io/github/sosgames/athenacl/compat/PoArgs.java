// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an athenaCL argument string into values. Bare words are quoted, then the
 * text is read as a Python row. {@code pt,(c,2)} becomes the string {@code pt} and
 * the list {@code c, 2}. A comma inside a word is not supported, matching the
 * original quoting step.
 */
public final class PoArgs {
    private static final String HIDDEN = "???????";

    private PoArgs() {
    }

    public static String restringulate(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String hidden = hideCommas(text);
        List<String> parts = new ArrayList<>();
        for (String raw : hidden.split(",", -1)) {
            String part = raw.trim().replace('[', '(').replace(']', ')');
            if (alreadyQuoted(part)) {
                parts.add(part);
                continue;
            }
            if (isWord(part)) {
                parts.add(quoteWord(part));
            } else {
                parts.add(part);
            }
        }
        String joined = String.join(",", parts).replace('(', '[').replace(')', ']');
        return joined.replace(HIDDEN, ",");
    }

    public static List<PyValue> parse(String text) {
        return PyLiteral.parseRow(restringulate(text));
    }

    private static String hideCommas(String text) {
        String current = text;
        for (char[] pair : new char[][] {{'\'', '\''}, {'"', '"'}, {'{', '}'}}) {
            StringBuilder next = new StringBuilder();
            boolean inside = false;
            for (int i = 0; i < current.length(); i++) {
                char c = current.charAt(i);
                if (c == pair[0]) {
                    inside = true;
                }
                if (c == pair[1]) {
                    inside = false;
                }
                if (c == ',' && inside) {
                    next.append(HIDDEN);
                } else {
                    next.append(c);
                }
            }
            current = next.toString();
        }
        return current;
    }

    private static boolean alreadyQuoted(String part) {
        int doubles = count(part, '"');
        int singles = count(part, '\'');
        if (doubles > 0 && doubles % 2 == 0 && singles == 0) {
            return true;
        }
        return singles > 0 && singles % 2 == 0 && doubles == 0;
    }

    private static boolean isWord(String part) {
        String cleaned = part.replace("(", "").replace(")", "");
        if (!isNumber(cleaned)) {
            return true;
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c == '(' || c == ' ') {
                continue;
            }
            if (Character.isLetter(c) || c == '/' || c == '+' || c == '_') {
                return true;
            }
            break;
        }
        for (String symbol : List.of("|", "&", "@", "{", "}", "=")) {
            if (part.contains(symbol)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumber(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == ' ' || c == '+')) {
                return false;
            }
        }
        return true;
    }

    private static String quoteWord(String part) {
        StringBuilder open = new StringBuilder();
        StringBuilder close = new StringBuilder();
        StringBuilder mid = new StringBuilder();
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c == '(') {
                open.append(c);
            } else if (c == ')') {
                close.append(c);
            } else {
                mid.append(c);
            }
        }
        return open + quote(mid.toString().strip()) + close;
    }

    private static String quote(String word) {
        return "'" + word.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static int count(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
