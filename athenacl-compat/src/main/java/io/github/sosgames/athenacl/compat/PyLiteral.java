// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a Python literal: numbers, strings, {@code None}, {@code True}, {@code False},
 * lists, and tuples. Project files store these in XML attributes, such as {@code [(0,)]}.
 */
public final class PyLiteral {
    private PyLiteral() {
    }

    public static PyValue parse(String text) {
        Parser parser = new Parser(text == null ? "" : text);
        PyValue value = parser.value();
        parser.skip();
        if (!parser.end()) {
            throw parser.bad("trailing text");
        }
        return value;
    }

    /** A comma-separated row, the shape a PO argument string has after quoting. */
    public static List<PyValue> parseRow(String text) {
        Parser parser = new Parser(text == null ? "" : text);
        parser.skip();
        List<PyValue> items = new ArrayList<>();
        if (parser.end()) {
            return items;
        }
        items.add(parser.value());
        while (parser.peek(',')) {
            parser.bump();
            parser.skip();
            if (parser.end()) {
                break;
            }
            items.add(parser.value());
        }
        parser.skip();
        if (!parser.end()) {
            throw parser.bad("trailing text");
        }
        return items;
    }

    private static final class Parser {
        private final String text;
        private int at;

        private Parser(String text) {
            this.text = text;
        }

        private PyValue value() {
            skip();
            if (end()) {
                throw bad("value missing");
            }
            char c = text.charAt(at);
            if (c == '\'' || c == '"') {
                return new PyValue.Str(string());
            }
            if (c == '[') {
                return new PyValue.Seq(group('[', ']'), false);
            }
            if (c == '(') {
                return new PyValue.Seq(group('(', ')'), true);
            }
            if (starts("None")) {
                at += 4;
                return new PyValue.None();
            }
            if (starts("True")) {
                at += 4;
                return new PyValue.Bool(true);
            }
            if (starts("False")) {
                at += 5;
                return new PyValue.Bool(false);
            }
            return number();
        }

        private List<PyValue> group(char open, char close) {
            if (!peek(open)) {
                throw bad("expected " + open);
            }
            bump();
            List<PyValue> items = new ArrayList<>();
            skip();
            if (peek(close)) {
                bump();
                return items;
            }
            items.add(value());
            while (true) {
                skip();
                if (peek(close)) {
                    bump();
                    return items;
                }
                if (!peek(',')) {
                    throw bad("expected a comma");
                }
                bump();
                skip();
                if (peek(close)) {
                    bump();
                    return items;
                }
                items.add(value());
            }
        }

        private String string() {
            char quote = text.charAt(at);
            at++;
            StringBuilder body = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == quote) {
                    return body.toString();
                }
                if (c == '\\' && at < text.length()) {
                    char next = text.charAt(at++);
                    body.append(switch (next) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case '\\', '\'', '"' -> next;
                        default -> next;
                    });
                } else {
                    body.append(c);
                }
            }
            throw bad("string is not closed");
        }

        private PyValue number() {
            int start = at;
            if (peek('+') || peek('-')) {
                at++;
            }
            boolean digits = false;
            while (at < text.length() && Character.isDigit(text.charAt(at))) {
                digits = true;
                at++;
            }
            boolean fraction = false;
            if (peek('.')) {
                fraction = true;
                at++;
                while (at < text.length() && Character.isDigit(text.charAt(at))) {
                    digits = true;
                    at++;
                }
            }
            if (peek('e') || peek('E')) {
                fraction = true;
                at++;
                if (peek('+') || peek('-')) {
                    at++;
                }
                int exp = at;
                while (at < text.length() && Character.isDigit(text.charAt(at))) {
                    at++;
                }
                if (at == exp) {
                    throw bad("exponent missing");
                }
            }
            if (!digits) {
                throw bad("not a literal");
            }
            String token = text.substring(start, at);
            if (!fraction) {
                return new PyValue.IntNum(Long.parseLong(token));
            }
            return new PyValue.FloatNum(Double.parseDouble(token));
        }

        private boolean starts(String word) {
            if (!text.startsWith(word, at)) {
                return false;
            }
            int end = at + word.length();
            return end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
        }

        private void skip() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        private boolean peek(char c) {
            return at < text.length() && text.charAt(at) == c;
        }

        private void bump() {
            at++;
        }

        private boolean end() {
            return at >= text.length();
        }

        private IllegalArgumentException bad(String message) {
            return new IllegalArgumentException(message + " at " + at);
        }
    }
}
