// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sieves, tunings, and a few generator fixtures. A sieve such as {@code 3@2}
 * keeps the integers that leave a remainder of 2. {@code &} is intersection and
 * {@code |} is union. The logistic map and the permutation list are the
 * generator fixtures in this check.
 */
public final class TheorySamples {
    public record Check(int pass, int fail, List<String> failed) {
    }

    private TheorySamples() {
    }

    public static Check check(Path golden) throws IOException {
        int pass = 0;
        List<String> failed = new ArrayList<>();
        for (String line : Files.readAllLines(golden, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t");
            boolean ok = switch (cells[0]) {
                case "sieve" -> sieveOk(cells);
                case "temper" -> temperOk(cells);
                case "verhulst" -> verhulstOk(cells);
                case "perm" -> permOk(cells);
                case "set" -> setOk(cells);
                default -> false;
            };
            if (ok) {
                pass++;
            } else {
                failed.add(cells.length > 1 ? cells[1] : cells[0]);
            }
        }
        return new Check(pass, failed.size(), List.copyOf(failed));
    }

    private static boolean sieveOk(String[] cells) {
        return cells[2].equals(join(sieve(cells[1], 0, 23)));
    }

    private static boolean temperOk(String[] cells) {
        double[] got = switch (cells[1]) {
            case "twelve" -> Temperament.twelve();
            case "pythagorean" -> Temperament.pythagorean();
            case "just" -> Temperament.just();
            case "meantone" -> Temperament.meantone();
            default -> null;
        };
        if (got == null) {
            return false;
        }
        String[] expect = cells[2].split(",");
        if (got.length != expect.length) {
            return false;
        }
        for (int i = 0; i < got.length; i++) {
            if (!near(Double.parseDouble(expect[i]), got[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean verhulstOk(String[] cells) {
        double p = Double.parseDouble(cells[1]);
        double x = Double.parseDouble(cells[2]);
        String[] expect = cells[3].split(",");
        for (String value : expect) {
            x = p * x * (1.0 - x);
            if (!near(Double.parseDouble(value), x)) {
                return false;
            }
        }
        return true;
    }

    private static boolean permOk(String[] cells) {
        int[] items = ints(cells[1]);
        return cells[2].equals(perms(items));
    }

    private static boolean setOk(String[] cells) {
        SetClass form = SetClass.parse(cells[1]);
        return form.pcs().equals(cells[2]) && form.icv().equals(cells[3]);
    }

    static List<Integer> sieve(String logic, int zMin, int zMax) {
        List<Integer> hit = new ArrayList<>();
        SieveTest test = new SieveParser(logic).parse();
        for (int z = zMin; z <= zMax; z++) {
            if (test.holds(z)) {
                hit.add(z);
            }
        }
        return hit;
    }

    private static String perms(int[] items) {
        List<int[]> found = new ArrayList<>();
        permute(items, new ArrayList<>(), new boolean[items.length], found);
        StringBuilder text = new StringBuilder();
        for (int[] row : found) {
            if (!text.isEmpty()) {
                text.append(';');
            }
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    text.append(',');
                }
                text.append(row[i]);
            }
        }
        return text.toString();
    }

    private static void permute(int[] items, List<Integer> prefix, boolean[] used, List<int[]> found) {
        if (prefix.size() == items.length) {
            int[] row = new int[prefix.size()];
            for (int i = 0; i < row.length; i++) {
                row[i] = prefix.get(i);
            }
            found.add(row);
            return;
        }
        for (int i = 0; i < items.length; i++) {
            if (used[i]) {
                continue;
            }
            used[i] = true;
            prefix.add(items[i]);
            permute(items, prefix, used, found);
            prefix.remove(prefix.size() - 1);
            used[i] = false;
        }
    }

    private static int[] ints(String text) {
        String[] cells = text.split(",");
        int[] values = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            values[i] = Integer.parseInt(cells[i]);
        }
        return values;
    }

    private static String join(List<Integer> values) {
        StringBuilder text = new StringBuilder();
        for (int value : values) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(value);
        }
        return text.toString();
    }

    private static boolean near(double expect, double got) {
        double gap = Math.abs(expect - got);
        return gap <= 1e-9 || gap <= 1e-9 * Math.max(1.0, Math.abs(expect));
    }

    @FunctionalInterface
    private interface SieveTest {
        boolean holds(int z);
    }

    private static final class SieveParser {
        private final String text;
        private int at;

        SieveParser(String text) {
            this.text = text;
        }

        SieveTest parse() {
            SieveTest expr = union();
            if (at != text.length()) {
                throw new IllegalArgumentException("sieve " + text);
            }
            return expr;
        }

        private SieveTest union() {
            SieveTest left = intersection();
            while (at < text.length() && text.charAt(at) == '|') {
                at++;
                SieveTest right = intersection();
                SieveTest both = left;
                left = z -> both.holds(z) || right.holds(z);
            }
            return left;
        }

        private SieveTest intersection() {
            SieveTest left = factor();
            while (at < text.length() && text.charAt(at) == '&') {
                at++;
                SieveTest right = factor();
                SieveTest both = left;
                left = z -> both.holds(z) && right.holds(z);
            }
            return left;
        }

        private SieveTest factor() {
            if (at < text.length() && text.charAt(at) == '(') {
                at++;
                SieveTest inner = union();
                if (at >= text.length() || text.charAt(at) != ')') {
                    throw new IllegalArgumentException("sieve " + text);
                }
                at++;
                return inner;
            }
            int modulus = number();
            int residual = 0;
            if (at < text.length() && text.charAt(at) == '@') {
                at++;
                residual = number();
            }
            int mod = modulus;
            int res = residual;
            return z -> Math.floorMod(z, mod) == Math.floorMod(res, mod);
        }

        private int number() {
            int start = at;
            if (at < text.length() && text.charAt(at) == '-') {
                at++;
            }
            while (at < text.length() && Character.isDigit(text.charAt(at))) {
                at++;
            }
            return Integer.parseInt(text.substring(start, at));
        }
    }
}
