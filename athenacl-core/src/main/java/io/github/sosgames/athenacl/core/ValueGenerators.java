// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.CompatRandom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The 45 value generators. Each one is sampled six times with both random
 * streams seeded to 7, using arguments that stay off the wave and breakpoint
 * generators. {@code iterateHold} and {@code iterateSelect} refresh on the
 * first event the way Python 2 did, when a missing refresh counted as less
 * than every number. The private operator base is not a generator.
 */
public final class ValueGenerators {
    public record Generator(String acronym, String name) {
    }

    public record Check(int pass, int fail, List<String> failed) {
    }

    private static final List<Generator> VALUE = List.of(
            new Generator("pr", "pathRead"),
            new Generator("tf", "typeFormat"),
            new Generator("c", "constant"),
            new Generator("cg", "cyclicGen"),
            new Generator("bg", "basketGen"),
            new Generator("bs", "basketSelect"),
            new Generator("bf", "basketFill"),
            new Generator("bfs", "basketFillSelect"),
            new Generator("fs", "fibonacciSeries"),
            new Generator("vs", "valueSieve"),
            new Generator("sf", "sieveFunnel"),
            new Generator("sl", "sieveList"),
            new Generator("lp", "listPrime"),
            new Generator("vp", "valuePrime"),
            new Generator("lm", "logisticMap"),
            new Generator("hb", "henonBasket"),
            new Generator("lb", "lorenzBasket"),
            new Generator("n", "noise"),
            new Generator("m", "mask"),
            new Generator("ms", "maskScale"),
            new Generator("mr", "maskReject"),
            new Generator("fb", "funnelBinary"),
            new Generator("a", "accumulator"),
            new Generator("q", "quantize"),
            new Generator("mv", "markovValue"),
            new Generator("mga", "markovGeneratorAnalysis"),
            new Generator("cv", "caValue"),
            new Generator("cl", "caList"),
            new Generator("gt", "grammarTerminus"),
            new Generator("fml", "feedbackModelLibrary"),
            new Generator("ig", "iterateGroup"),
            new Generator("iw", "iterateWindow"),
            new Generator("ih", "iterateHold"),
            new Generator("ic", "iterateCross"),
            new Generator("is", "iterateSelect"),
            new Generator("sah", "sampleAndHold"),
            new Generator("oa", "operatorAdd"),
            new Generator("os", "operatorSubtract"),
            new Generator("om", "operatorMultiply"),
            new Generator("od", "operatorDivide"),
            new Generator("op", "operatorPower"),
            new Generator("oc", "operatorCongruence"),
            new Generator("oo", "oneOver"),
            new Generator("sr", "staticRange"),
            new Generator("si", "staticInst"));

    private ValueGenerators() {
    }

    public static List<Generator> valueGroup() {
        return VALUE;
    }

    public static List<Object> sample(String acronym) {
        CompatRandom random = CompatRandom.seed(7);
        List<?> series = series(acronym, random);
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < 6 && i < series.size(); i++) {
            out.add(series.get(i));
        }
        if (out.size() < 6) {
            throw new IllegalStateException(acronym + " produced " + out.size());
        }
        return out;
    }

    public static Check check(Path golden) throws IOException {
        int pass = 0;
        List<String> failed = new ArrayList<>();
        for (String line : Files.readAllLines(golden, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t");
            List<Object> got = sample(cells[0]);
            boolean ok = true;
            for (int i = 0; i < 6; i++) {
                if (!same(cells[i + 1], got.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                pass++;
            } else {
                failed.add(cells[0] + " got " + got);
            }
        }
        return new Check(pass, failed.size(), failed);
    }

    static boolean same(String golden, Object value) {
        if (golden.startsWith("'") && golden.endsWith("'")) {
            return value instanceof String text
                    && golden.substring(1, golden.length() - 1).equals(text);
        }
        if (golden.startsWith("(")) {
            return value instanceof double[] pair
                    && golden.equals("(" + trim(pair[0]) + ", " + trim(pair[1]) + ")");
        }
        double expect = Double.parseDouble(golden);
        double got = ((Number) value).doubleValue();
        double gap = Math.abs(expect - got);
        return gap <= 1e-9 || gap <= 1e-9 * Math.max(1.0, Math.abs(expect));
    }

    private static String trim(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e12) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static List<?> series(String acronym, CompatRandom random) {
        return switch (acronym) {
            case "pr" -> repeat(0);
            case "tf" -> repeat("4");
            case "c" -> repeat(4);
            case "cg" -> cyclic();
            case "bg" -> cycle(List.of(1, 2, 3));
            case "bs" -> repeat(pick(List.of(1, 2, 3, 4), 0.5));
            case "bf" -> repeat(7);
            case "bfs" -> repeat(pick(List.of(3, 3, 3, 3), 0.5));
            case "fs" -> walk(ValueMath.unitNormRange(ValueMath.fibonacci(8, 5)));
            case "vs" -> walk(normSieve("3|4", 0, 11));
            case "sf" -> repeat(funnel(ValueMath.sieve("3|4", 0, 11), 0.4));
            case "sl" -> walkInts(ValueMath.sieve("3|4", -12, 12));
            case "lp" -> walkInts(ValueMath.primes(2, 20));
            case "vp" -> walk(ValueMath.unitNormRange(doubles(ValueMath.primes(2, 20))));
            case "lm" -> logistic();
            case "hb" -> henon();
            case "lb" -> lorenz();
            case "n" -> ValueMath.noise(8, 1, random);
            case "m" -> repeat(ValueMath.boundaryFitLimit(0.2, 0.8, 0.5));
            case "ms" -> maskScale();
            case "mr" -> repeat(ValueMath.boundaryRejectLimit(0.2, 0.8, 0.5));
            case "fb" -> repeat(1);
            case "a" -> accumulator();
            case "q" -> repeat(quantize());
            case "mv" -> ValueMath.markov(
                    List.of(0.2, 0.5, 0.8, 0.0), List.of(5.0, 4.0, 7.0, 1.0), random, 6);
            case "mga" -> ValueMath.markov(List.of(1.0, 2.0, 3.0), List.of(4.0, 4.0, 4.0), random, 6);
            case "cv" -> walk(ValueMath.rowSums(8, random));
            case "cl" -> walk(ValueMath.columnSums(0, 0.01, random));
            case "gt" -> walk(ValueMath.grammarValues("a{.2}b{.5}@a{ba}b{ab}@a", 4));
            case "fml" -> ValueMath.climate(random, 6);
            case "ig" -> iterateGroup();
            case "iw" -> iterateWindow();
            case "ih" -> iterateHold();
            case "is" -> iterateSelect();
            case "ic" -> repeat(2.5);
            case "sah" -> repeat(0.2);
            case "oa" -> repeat(5);
            case "os" -> repeat(-1);
            case "om" -> repeat(6);
            case "od" -> repeat(4.0);
            case "op" -> repeat(8);
            case "oc" -> repeat(1);
            case "oo" -> repeat(0.25);
            case "sr" -> repeat(new double[] {0, 8});
            case "si" -> repeat(12);
            default -> throw new IllegalArgumentException(acronym);
        };
    }

    private static List<Double> cyclic() {
        ValueMath.Cyclic gen = new ValueMath.Cyclic(0, 1, 0.125);
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            values.add(gen.next());
        }
        return values;
    }

    private static List<Double> logistic() {
        double x = 0.5;
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            x = 3.7 * x * (1 - x);
            values.add(x);
        }
        return values;
    }

    private static List<Double> henon() {
        double x = 0.5;
        double y = 0.5;
        double a = 1.4;
        double b = 0.3;
        List<Double> basket = new ArrayList<>();
        int i = 0;
        while (i <= 30) {
            double xTemp = 1.0 - a * x * x + y;
            double yTemp = b * x;
            x = xTemp;
            y = yTemp;
            basket.add(x);
            i++;
        }
        return walk(ValueMath.unitNormRange(basket));
    }

    private static List<Double> lorenz() {
        double x = 1;
        double y = 1;
        double z = 1;
        double r = 28;
        double s = 10;
        double b = 2.67;
        double d = 0.01;
        List<Double> basket = new ArrayList<>();
        int i = 0;
        while (i <= 30) {
            double xTemp = s * (y - x);
            double yTemp = r * x - y - x * z;
            double zTemp = x * y - b * z;
            x += d * xTemp;
            y += d * yTemp;
            z += d * zTemp;
            basket.add(x);
            i++;
        }
        return walk(ValueMath.unitNormRange(basket));
    }

    private static List<Double> maskScale() {
        List<Double> raw = doubles(ValueMath.primes(2, 10));
        return walk(ValueMath.unitNormRange(raw));
    }

    private static double quantize() {
        ValueMath.Quantizer quant = new ValueMath.Quantizer();
        quant.grid(List.of(0.25));
        return quant.attract(0.3, 1, 0);
    }

    private static List<Double> normSieve(String logic, int min, int max) {
        return ValueMath.unitNormRange(doubles(ValueMath.sieve(logic, min, max)), min, max);
    }

    private static double funnel(List<Integer> series, double value) {
        List<Double> unit = ValueMath.unitNormRange(doubles(series));
        int low = series.get(0);
        int high = series.get(series.size() - 1);
        int[] map = new int[high - low + 1];
        for (int point : series) {
            map[point - low] = 1;
        }
        int index = ValueMath.unitBoundaryPos(value, ValueMath.unitBoundaryEqual(map.length));
        if (map[index] == 1) {
            int count = 0;
            for (int i = 0; i < map.length; i++) {
                if (map[i] == 1) {
                    if (i == index) {
                        return unit.get(count);
                    }
                    count++;
                }
            }
        }
        return unit.get(0);
    }

    private static int pick(List<Integer> items, double unit) {
        return items.get(ValueMath.unitBoundaryPos(unit, ValueMath.unitBoundaryEqual(items.size())));
    }

    private static List<Double> walk(List<Double> values) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(values.get(i % values.size()));
        }
        return out;
    }

    private static List<Integer> cycle(List<Integer> items) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(items.get(i % items.size()));
        }
        return out;
    }

    private static List<Double> doubles(List<Integer> values) {
        List<Double> out = new ArrayList<>();
        for (int value : values) {
            out.add((double) value);
        }
        return out;
    }

    private static List<Integer> walkInts(List<Integer> values) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(values.get(i % values.size()));
        }
        return out;
    }

    private static List<Integer> iterateGroup() {
        int[] control = {1, -1, 2};
        int at = 0;
        List<Integer> buffer = new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        while (out.size() < 6) {
            if (buffer.isEmpty()) {
                int q = control[at % control.length];
                at++;
                if (q < 0) {
                    // A negative count discards that many source values.
                } else if (q > 0) {
                    for (int i = 0; i < q; i++) {
                        buffer.add(1);
                    }
                }
            }
            if (!buffer.isEmpty()) {
                out.add(buffer.remove(0));
            }
        }
        return out;
    }

    private static List<Integer> iterateWindow() {
        int[] generators = {1, 9};
        int[] counts = {2, 1};
        int genAt = 0;
        int countAt = 0;
        List<Integer> buffer = new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        while (out.size() < 6) {
            if (buffer.isEmpty()) {
                int pos = genAt % generators.length;
                genAt++;
                int q = counts[countAt % counts.length];
                countAt++;
                for (int i = 0; i < q; i++) {
                    buffer.add(generators[pos]);
                }
            }
            out.add(buffer.remove(0));
        }
        return out;
    }

    private static List<Integer> iterateHold() {
        return hold(3, 4, 2, true);
    }

    private static List<Integer> iterateSelect() {
        return hold(5, 3, 2, false);
    }

    /**
     * A missing refresh compares as less than the event count, which is what
     * Python 2 did. Python 3 raises on that comparison, so the first event
     * would not run there.
     */
    private static List<Integer> hold(int source, int size, int refreshCount, boolean cyclic) {
        Integer refresh = null;
        int eventCount = 0;
        int total = 0;
        List<Integer> buffer = new ArrayList<>();
        int cursor = 0;
        List<Integer> out = new ArrayList<>();
        while (out.size() < 6) {
            boolean reached = refresh == null || eventCount >= refresh;
            if ((reached && (refresh == null || refresh != 0)) || total == 0) {
                buffer = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    buffer.add(source);
                }
                cursor = 0;
                eventCount = 0;
            }
            if (eventCount == 0 || total == 0) {
                refresh = refreshCount;
            }
            eventCount++;
            total++;
            if (cyclic) {
                if (cursor == buffer.size()) {
                    cursor = 0;
                }
                out.add(buffer.get(cursor++));
            } else {
                int index = ValueMath.unitBoundaryPos(0, ValueMath.unitBoundaryEqual(buffer.size()));
                out.add(buffer.get(index));
            }
        }
        return out;
    }

    private static List<Double> accumulator() {
        List<Double> out = new ArrayList<>();
        double value = 0;
        for (int i = 0; i < 6; i++) {
            out.add(value);
            value += 2;
        }
        return out;
    }

    private static List<Object> repeat(Object value) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(value);
        }
        return out;
    }
}
