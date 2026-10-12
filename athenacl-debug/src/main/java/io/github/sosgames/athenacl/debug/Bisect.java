// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * Finds the first bar where two event logs disagree, and the first control call that
 * is not in the good log. The search walks bars in order. It does not render audio.
 */
public final class Bisect {
    private Bisect() {
    }

    public record Hit(int bar, long eventId, int added, int removed, int changed) {
        public boolean identical() {
            return bar == 0;
        }
    }

    public static Hit events(String goodJsonl, String badJsonl) {
        TreeMap<Integer, List<String>> good = notesByBar(goodJsonl);
        TreeMap<Integer, List<String>> bad = notesByBar(badJsonl);
        int max = 0;
        if (!good.isEmpty()) {
            max = Math.max(max, good.lastKey());
        }
        if (!bad.isEmpty()) {
            max = Math.max(max, bad.lastKey());
        }
        for (int bar = 1; bar <= max; bar++) {
            List<String> left = good.getOrDefault(bar, List.of());
            List<String> right = bad.getOrDefault(bar, List.of());
            if (left.equals(right)) {
                continue;
            }
            return new Hit(bar, firstEvent(left, right), count(right, left), count(left, right), changed(left, right));
        }
        return new Hit(0, 0, 0, 0, 0);
    }

    public static boolean sameBar(String leftJsonl, String rightJsonl, int bar) {
        return notesByBar(leftJsonl).getOrDefault(bar, List.of())
                .equals(notesByBar(rightJsonl).getOrDefault(bar, List.of()));
    }

    /**
     * The first control line that is not the same call as the good log.
     * Returns that line's bar, or 0 when the calls match.
     */
    public static int firstControl(String goodLog, String badLog) {
        List<String> good = calls(goodLog);
        List<String> bad = calls(badLog);
        int n = Math.max(good.size(), bad.size());
        for (int i = 0; i < n; i++) {
            String left = i < good.size() ? good.get(i) : "";
            String right = i < bad.size() ? bad.get(i) : "";
            if (!left.equals(right)) {
                String source = right.isEmpty() ? left : right;
                return field(source, "bar");
            }
        }
        return 0;
    }

    /**
     * Smallest {@code k} from 1 to {@code count} for which {@code prefixOk} is true.
     * {@code prefixOk} must stay true once it becomes true. Returns 0 when even the
     * full prefix is false.
     */
    public static int firstTrue(int count, IntPredicate prefixOk) {
        if (count < 1 || !prefixOk.test(count)) {
            return 0;
        }
        int lo = 0;
        int hi = count;
        while (lo + 1 < hi) {
            int mid = (lo + hi) / 2;
            if (prefixOk.test(mid)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    private static int changed(List<String> good, List<String> bad) {
        int n = 0;
        for (String line : bad) {
            long id = fieldLong(line, "id");
            String other = byId(good, id);
            if (other != null && !other.equals(line)) {
                n++;
            }
        }
        return n;
    }

    private static int count(List<String> haystack, List<String> missingFrom) {
        int n = 0;
        for (String line : haystack) {
            if (byId(missingFrom, fieldLong(line, "id")) == null) {
                n++;
            }
        }
        return n;
    }

    private static String byId(List<String> lines, long id) {
        for (String line : lines) {
            if (fieldLong(line, "id") == id) {
                return line;
            }
        }
        return null;
    }

    private static long firstEvent(List<String> good, List<String> bad) {
        int n = Math.max(good.size(), bad.size());
        for (int i = 0; i < n; i++) {
            String left = i < good.size() ? good.get(i) : "";
            String right = i < bad.size() ? bad.get(i) : "";
            if (!left.equals(right)) {
                String source = right.isEmpty() ? left : right;
                return fieldLong(source, "id");
            }
        }
        return 0;
    }

    private static TreeMap<Integer, List<String>> notesByBar(String jsonl) {
        TreeMap<Integer, List<String>> bars = new TreeMap<>();
        if (jsonl == null) {
            return bars;
        }
        for (String line : jsonl.split("\n", -1)) {
            if (!line.contains("\"type\":\"note\"")) {
                continue;
            }
            int bar = field(line, "bar");
            bars.computeIfAbsent(bar, ignored -> new ArrayList<>()).add(line);
        }
        return bars;
    }

    private static List<String> calls(String log) {
        List<String> lines = new ArrayList<>();
        if (log == null) {
            return lines;
        }
        for (String line : log.split("\n", -1)) {
            if (line.contains("\"type\":\"state\"")
                    || line.contains("\"type\":\"overlay\"")
                    || line.contains("\"type\":\"theme\"")) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static int field(String line, String name) {
        return (int) fieldLong(line, name);
    }

    private static long fieldLong(String line, String name) {
        String key = "\"" + name + "\":";
        int at = line.indexOf(key);
        if (at < 0) {
            return 0;
        }
        int i = at + key.length();
        int end = i;
        if (end < line.length() && line.charAt(end) == '"') {
            return 0;
        }
        while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '-')) {
            end++;
        }
        if (end == i) {
            return 0;
        }
        return Long.parseLong(line.substring(i, end));
    }
}
