// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.CompatRandom;
import io.github.sosgames.athenacl.compat.PoArgs;
import io.github.sosgames.athenacl.compat.PyValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs one parameter object for a playing layer. The string is the same shape as
 * {@code po sample}: a name, then its arguments. {@code markovPulse} draws rhythm
 * lengths. {@code basketGen} walks a list. {@code sieve} keeps the positions a
 * logic string allows. A texture name such as {@code LineGroove} is checked, not played.
 * The seed is the caller's, so two renders with the same seed match.
 */
public final class LayerSource {
    private LayerSource() {
    }

    public static boolean knownTexture(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (TextureModules.Module module : TextureModules.modules()) {
            if (module.name().equals(name) || module.acronym().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** The next {@code n} rhythm tokens, such as {@code 1/4} or {@code 1/8}. */
    public static List<String> rhythm(String po, int n, long seed) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        String trimmed = po == null ? "" : po.trim();
        int comma = trimmed.indexOf(',');
        String name = comma < 0 ? trimmed : trimmed.substring(0, comma).trim();
        String args = comma < 0 ? "" : trimmed.substring(comma + 1).trim();
        return switch (name) {
            case "markovPulse", "mp" -> markov(args, n, seed);
            case "basketGen", "bg" -> basket(args, n, seed);
            default -> throw new IllegalArgumentException(
                    "parameter object " + name + " is not a rhythm source");
        };
    }

    /** Positions from {@code zMin} through {@code zMax} that {@code sieve, 5@0|5@1} keeps. */
    public static List<Integer> sieve(String po, int zMin, int zMax) {
        String logic = po == null ? "" : po.trim();
        if (logic.startsWith("sieve")) {
            int comma = logic.indexOf(',');
            logic = comma < 0 ? "" : logic.substring(comma + 1).trim();
        }
        if (logic.isEmpty() || zMax < zMin) {
            throw new IllegalArgumentException("sieve needs a logic string such as 5@0|5@1");
        }
        return ValueMath.sieve(logic, zMin, zMax);
    }

    private static List<String> markov(String args, int n, long seed) {
        List<PyValue> row = PoArgs.parse("markovPulse," + args);
        if (row.size() < 3) {
            throw new IllegalArgumentException("markovPulse needs durations and weights");
        }
        List<Double> values = numbers(row.get(1));
        List<Double> weights = numbers(row.get(2));
        if (values.isEmpty() || values.size() != weights.size()) {
            throw new IllegalArgumentException("markovPulse durations and weights must match");
        }
        List<String> tokens = new ArrayList<>();
        for (double value : ValueMath.markov(values, weights, CompatRandom.seed(seed), n)) {
            tokens.add(token(value));
        }
        return tokens;
    }

    private static List<String> basket(String args, int n, long seed) {
        List<String> tokens = new ArrayList<>();
        for (String value : ParameterObjects.sample("basketGen", args, n, seed)) {
            tokens.add(token(Double.parseDouble(value)));
        }
        return tokens;
    }

    private static List<Double> numbers(PyValue node) {
        if (node instanceof PyValue.Seq seq) {
            List<Double> values = new ArrayList<>();
            for (PyValue item : seq.items()) {
                values.add(number(item));
            }
            return values;
        }
        return List.of(number(node));
    }

    private static double number(PyValue node) {
        if (node instanceof PyValue.IntNum num) {
            return num.value();
        }
        if (node instanceof PyValue.FloatNum num) {
            return num.value();
        }
        throw new IllegalArgumentException("expected a number");
    }

    /** Quarter-lengths: 2 is a half note, 1 a quarter, 0.5 an eighth, 1.5 a dotted quarter. */
    static String token(double quarters) {
        long eighths = Math.round(quarters * 2);
        return switch ((int) eighths) {
            case 8 -> "1/1";
            case 6 -> "3/4";
            case 4 -> "1/2";
            case 3 -> "3/8";
            case 2 -> "1/4";
            case 1 -> "1/8";
            default -> throw new IllegalArgumentException("duration " + quarters + " is not a rhythm token");
        };
    }
}
