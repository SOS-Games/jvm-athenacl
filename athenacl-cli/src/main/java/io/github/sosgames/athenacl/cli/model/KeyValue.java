// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Grep-stable text output. One record is one line. A line is either
 * {@code key=value} pairs or a leading kind word plus pairs ({@code error code=NOT_FOUND ...}).
 * Values that contain spaces or other punctuation are double-quoted. Paths, schema ids,
 * and argv slots stay bare so they are easy to copy.
 */
public final class KeyValue {
    private static final Pattern RAW = Pattern.compile("[A-Za-z0-9._,+/:@#\\[\\]\\-]*");

    private KeyValue() {
    }

    public static String line(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        appendFields(sb, fields);
        return sb.toString();
    }

    public static String record(String kind, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(kind);
        if (!fields.isEmpty()) {
            sb.append(' ');
            appendFields(sb, fields);
        }
        return sb.toString();
    }

    public static String formatValue(String value) {
        if (value == null) {
            return "\"\"";
        }
        if (RAW.matcher(value).matches()) {
            return value;
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    public static Map<String, String> issueFields(Issue issue) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("code", issue.code());
        fields.put("path", issue.path());
        fields.put("message", issue.message());
        fields.put("fix", issue.fix());
        if (issue.docRef() != null) {
            fields.put("docRef", issue.docRef());
        }
        return fields;
    }

    private static void appendFields(StringBuilder sb, Map<String, String> fields) {
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                sb.append(' ');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(formatValue(entry.getValue()));
        }
    }
}
