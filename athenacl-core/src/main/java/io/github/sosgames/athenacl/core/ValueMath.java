// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.CompatRandom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The arithmetic the value generators share: unit bounds, a sieve of moduli,
 * primes, a small cellular automaton, and the climate-control particle loop.
 * Python's {@code %} on a negative left side stays in the residue, so the
 * modulus here uses the same floor division.
 */
final class ValueMath {
    private ValueMath() {
    }

    static double denorm(double value, double a, double b) {
        if (a == b) {
            return a;
        }
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        return value * (max - min) + min;
    }

    static double limit(double value) {
        if (value > 1) {
            return 1;
        }
        if (value < 0) {
            return 0;
        }
        return value;
    }

    static double unitNorm(double value, double min, double max) {
        double span = max - min;
        if (span == 0) {
            return 0;
        }
        return (value - min) / span;
    }

    static List<Double> unitNormRange(List<Double> series) {
        double min = series.get(0);
        double max = series.get(0);
        for (double value : series) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return unitNormRange(series, min, max);
    }

    static List<Double> unitNormRange(List<Double> series, double min, double max) {
        List<Double> unit = new ArrayList<>();
        double span = max - min;
        if (series.size() <= 1) {
            unit.add(0.0);
            return unit;
        }
        for (double value : series) {
            unit.add(span == 0 ? 0 : (value - min) / span);
        }
        return unit;
    }

    static List<Double> unitNormProportion(List<Double> series) {
        double sum = 0;
        for (double value : series) {
            sum += value;
        }
        List<Double> unit = new ArrayList<>();
        for (double value : series) {
            unit.add(value / sum);
        }
        return unit;
    }

    record Bound(double low, double mean, double high) {
    }

    static List<Bound> unitBoundaryEqual(int parts) {
        List<Bound> bounds = new ArrayList<>();
        double step = 1.0 / parts;
        double low = 0;
        for (int face = 0; face < parts; face++) {
            double high = face == parts - 1 ? 1.0 : step * (face + 1);
            bounds.add(new Bound(low, low + step * 0.5, high));
            low = high;
        }
        return bounds;
    }

    static List<Bound> unitBoundaryProportion(List<Double> weights) {
        List<Double> unit = unitNormProportion(weights);
        List<Bound> bounds = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < unit.size(); i++) {
            double low = sum;
            double high = i == unit.size() - 1 ? 1.0 : sum + unit.get(i);
            if (i != unit.size() - 1) {
                sum += unit.get(i);
            }
            bounds.add(new Bound(low, (low + high) * 0.5, high));
        }
        return bounds;
    }

    static int unitBoundaryPos(double value, List<Bound> bounds) {
        if (value == 1) {
            return bounds.size() - 1;
        }
        for (int i = 0; i < bounds.size(); i++) {
            Bound bound = bounds.get(i);
            if (value >= bound.low && value < bound.high) {
                return i;
            }
        }
        return bounds.size() - 1;
    }

    static double boundaryFitLimit(double a, double b, double fill) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        if (fill >= min && fill <= max) {
            return fill;
        }
        return fill > max ? max : min;
    }

    static double boundaryRejectLimit(double a, double b, double fill) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        if (fill <= min || fill >= max) {
            return fill;
        }
        double center = min + Math.abs(max - min) * 0.5;
        return fill >= center ? max : min;
    }

    static int floatToIntWeight(Number value, CompatRandom random) {
        if (value instanceof Long || value instanceof Integer) {
            return value.intValue();
        }
        double raw = value.doubleValue();
        double q = random.random();
        double whole = Math.floor(raw);
        double frac = raw - whole;
        int weight;
        if (frac == 0.5) {
            weight = q >= 0.5 ? 1 : 0;
        } else if (frac > 0.5) {
            weight = q <= frac ? 1 : 0;
        } else {
            weight = q >= frac ? 0 : 1;
        }
        return (int) (whole + weight);
    }

    static List<Integer> primes(int start, int length) {
        List<Integer> found = new ArrayList<>();
        int n = start;
        while (found.size() < length) {
            if (isPrime(n)) {
                found.add(n);
            }
            n += n > 4 ? 2 : 1;
        }
        return found;
    }

    private static boolean isPrime(int n) {
        if (n < 2) {
            return false;
        }
        if (n % 2 == 0) {
            return n == 2;
        }
        for (int d = 3; d * d <= n; d += 2) {
            if (n % d == 0) {
                return false;
            }
        }
        return true;
    }

    static List<Integer> sieve(String logic, int zMin, int zMax) {
        List<Integer> hit = new ArrayList<>();
        for (int z = zMin; z <= zMax; z++) {
            if (sieveHolds(logic, z)) {
                hit.add(z);
            }
        }
        hit.sort(Integer::compareTo);
        return hit;
    }

    private static boolean sieveHolds(String logic, int z) {
        return new SieveParser(logic).parse().test(z);
    }

    @FunctionalInterface
    private interface SieveTest {
        boolean test(int z);
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
                left = z -> both.test(z) || right.test(z);
            }
            return left;
        }

        private SieveTest intersection() {
            SieveTest left = factor();
            while (at < text.length() && text.charAt(at) == '&') {
                at++;
                SieveTest right = factor();
                SieveTest both = left;
                left = z -> both.test(z) && right.test(z);
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

    static List<Double> fibonacci(int start, int length) {
        double upper = (1.0 + Math.sqrt(5.0)) / 2.0;
        double lower = (1.0 - Math.sqrt(5.0)) / 2.0;
        List<Double> series = new ArrayList<>();
        for (int i = start; i < start + length; i++) {
            double n = (Math.pow(upper, i + 1.0) - Math.pow(lower, i + 1.0)) / Math.sqrt(5.0);
            series.add(Math.floor(n));
        }
        return series;
    }

    static final class Cyclic {
        private double cycle;
        private double direction = 1;
        private final double min;
        private final double max;
        private final double inc;

        Cyclic(double min, double max, double inc) {
            this.min = min;
            this.max = max;
            this.inc = inc;
            this.cycle = min;
        }

        double next() {
            cycle += direction * inc;
            if (direction > 0 && cycle > max) {
                direction = -1;
                cycle = max;
            } else if (direction < 0 && cycle < min) {
                direction = 1;
                cycle = min;
            }
            return cycle;
        }
    }

    static final class Basket {
        private final List<Object> items;
        private int index;

        Basket(List<Object> items) {
            this.items = items;
        }

        Object next() {
            if (items.size() == 1) {
                return items.get(0);
            }
            if (index == items.size()) {
                index = 0;
            }
            return items.get(index++);
        }
    }

    static final class Quantizer {
        private List<Double> grid = List.of(1.0);

        void grid(List<Double> steps) {
            this.grid = steps;
        }

        double attract(double fill, double pull, double gridRef) {
            double[] bracket = neighbors(fill, gridRef);
            if (bracket[0] == bracket[1]) {
                return bracket[0];
            }
            double difLower = Math.abs(bracket[0] - fill);
            double difUpper = Math.abs(bracket[1] - fill);
            double gridPoint;
            double difMeasure;
            boolean up;
            if (difLower >= difUpper) {
                difMeasure = difUpper;
                gridPoint = bracket[1];
                up = true;
            } else {
                difMeasure = difLower;
                gridPoint = bracket[0];
                up = false;
            }
            if (pull == 1) {
                return gridPoint;
            }
            double deviate = difMeasure * (1 - pull);
            return up ? gridPoint - deviate : gridPoint + deviate;
        }

        private double[] neighbors(double value, double gridRef) {
            boolean down = gridRef >= value;
            List<Double> retro = new ArrayList<>(grid);
            java.util.Collections.reverse(retro);
            double cursor = gridRef;
            int n = 1;
            while (n < 999) {
                double width = down ? retro.get((n - 1) % retro.size()) : grid.get(n % grid.size());
                double step = down ? cursor - width : cursor + width;
                n++;
                double low = Math.min(cursor, step);
                double high = Math.max(cursor, step);
                if (value >= low && value <= high) {
                    return new double[] {low, high};
                }
                cursor = step;
            }
            return new double[] {value, value};
        }
    }

    static List<Double> noise(int resolution, double gamma, CompatRandom random) {
        int dice = 0;
        int span = 0;
        while (span < resolution) {
            dice++;
            span = 1 << dice;
        }
        List<Die> diceSet = new ArrayList<>();
        for (int i = 0; i < dice; i++) {
            diceSet.add(new Die(random));
        }
        int[] last = new int[dice];
        List<int[]> moves = binaryMoves(span, dice);
        double forced = -Math.abs(gamma);
        List<Double> weight = diceWeight(dice, forced);
        List<Double> out = new ArrayList<>();
        int movePos = 0;
        for (int n = 0; n < 6; n++) {
            int[] move = moves.get(movePos);
            for (int i = 0; i < dice; i++) {
                if (move[i] != last[i]) {
                    diceSet.get(i).roll(random);
                }
            }
            last = move.clone();
            double sum = 0;
            for (int i = 0; i < dice; i++) {
                sum += diceSet.get(i).value * weight.get(i);
            }
            out.add(sum);
            movePos++;
            if (movePos >= moves.size()) {
                movePos = 0;
            }
        }
        return out;
    }

    private static List<Double> diceWeight(int dice, double gamma) {
        List<Double> raw = new ArrayList<>();
        for (int i = 0; i < dice; i++) {
            double exp = (gamma + 1.0) * 0.5 * 0.6931472;
            double p = Math.exp(-exp);
            raw.add(Math.pow(p, i));
        }
        java.util.Collections.reverse(raw);
        return unitNormProportion(raw);
    }

    private static List<int[]> binaryMoves(int count, int width) {
        List<int[]> moves = new ArrayList<>();
        for (int value = 0; value < count; value++) {
            int[] bits = new int[width];
            int dec = value;
            int at = 0;
            if (dec == 0) {
                bits[width - 1] = 0;
            }
            List<Integer> lowFirst = new ArrayList<>();
            if (dec == 0) {
                lowFirst.add(0);
            }
            while (dec != 0) {
                lowFirst.add(dec % 2);
                dec = dec / 2;
            }
            while (lowFirst.size() < width) {
                lowFirst.add(0);
            }
            for (int i = 0; i < width; i++) {
                bits[i] = lowFirst.get(width - 1 - i);
            }
            moves.add(bits);
            at++;
        }
        return moves;
    }

    private static final class Die {
        double value;

        Die(CompatRandom random) {
            roll(random);
        }

        void roll(CompatRandom random) {
            value = random.random();
        }
    }

    static List<Double> elementary(int rule, double mutation, boolean weightRule, CompatRandom random) {
        int size = 15;
        int generations = 14;
        if (weightRule) {
            floatToIntWeight(rule == 0 ? 0.25 : (double) rule, random);
        }
        List<double[]> history = new ArrayList<>();
        double[] init = new double[size];
        init[size / 2] = 1;
        history.add(init);
        for (int gen = 0; gen < generations; gen++) {
            int filtered = weightRule
                    ? Math.floorMod(floatToIntWeight(0.25, random), 256)
                    : Math.floorMod(rule, 256);
            List<double[]> table = ruleTable(filtered);
            double[] prev = history.get(history.size() - 1);
            double[] next = new double[size];
            for (int pos = 0; pos < size; pos++) {
                double[] seen = neighborhood(prev, pos);
                for (double[] row : table) {
                    if (row[0] == seen[0] && row[1] == seen[1] && row[2] == seen[2]) {
                        double dst = row[3];
                        if (random.random() < mutation) {
                            List<Double> option = new ArrayList<>(List.of(0.0, 1.0));
                            option.remove(Double.valueOf((double) (int) dst));
                            dst = option.get(random.randint(0, option.size() - 1));
                        }
                        next[pos] = dst;
                        break;
                    }
                }
            }
            history.add(next);
        }
        return historySums(history);
    }

    private static double[] neighborhood(double[] row, int pos) {
        double[] seen = new double[3];
        int len = row.length;
        for (int q = 0; q < 3; q++) {
            double i = pos - 1.5 + q;
            boolean neg = i < 0;
            i = Math.abs(i) % len;
            if (neg) {
                i = -i;
            }
            int idx = (int) i;
            if (idx < 0) {
                idx += len;
            }
            seen[q] = row[idx];
        }
        return seen;
    }

    private static List<double[]> ruleTable(int ruleNo) {
        List<double[]> rows = new ArrayList<>();
        int n = 0;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                for (int c = 0; c < 2; c++) {
                    double dst = (ruleNo / Math.pow(2, n)) % 2;
                    rows.add(new double[] {a, b, c, dst});
                    n++;
                }
            }
        }
        return rows;
    }

    private static List<Double> historySums(List<double[]> history) {
        List<Double> sums = new ArrayList<>();
        for (double[] row : history) {
            double sum = 0;
            for (double value : row) {
                sum += value;
            }
            sums.add(sum);
        }
        return sums;
    }

    static List<Double> columnSums(int rule, double mutation, CompatRandom random) {
        int size = 15;
        List<double[]> history = rows(rule, mutation, true, random, size, 14);
        List<Double> sums = new ArrayList<>();
        for (int col = 0; col < size; col++) {
            double sum = 0;
            for (double[] row : history) {
                sum += row[col];
            }
            sums.add(sum);
        }
        return sums;
    }

    static List<Double> rowSums(int rule, CompatRandom random) {
        return elementary(rule, 0, false, random);
    }

    private static List<double[]> rows(int rule, double mutation, boolean weightRule, CompatRandom random,
            int size, int generations) {
        if (weightRule) {
            floatToIntWeight(0.25, random);
        }
        List<double[]> history = new ArrayList<>();
        double[] init = new double[size];
        init[size / 2] = 1;
        history.add(init);
        for (int gen = 0; gen < generations; gen++) {
            int filtered = weightRule
                    ? Math.floorMod(floatToIntWeight(0.25, random), 256)
                    : Math.floorMod(rule, 256);
            List<double[]> table = ruleTable(filtered);
            double[] prev = history.get(history.size() - 1);
            double[] next = new double[size];
            for (int pos = 0; pos < size; pos++) {
                double[] seen = neighborhood(prev, pos);
                for (double[] row : table) {
                    if (seen[0] == row[0] && seen[1] == row[1] && seen[2] == row[2]) {
                        double dst = row[3];
                        if (random.random() < mutation) {
                            List<Double> option = new ArrayList<>(List.of(0.0, 1.0));
                            option.remove((double) (int) dst);
                            dst = option.get(random.randint(0, option.size() - 1));
                        }
                        next[pos] = dst;
                        break;
                    }
                }
            }
            history.add(next);
        }
        return history;
    }

    static List<Double> grammarValues(String spec, int steps) {
        String[] parts = spec.split("@");
        Map<String, Double> symbols = new LinkedHashMap<>();
        Map<String, String> rules = new LinkedHashMap<>();
        readPairs(parts[0], symbols, rules, true);
        readPairs(parts[1], symbols, rules, false);
        String state = null;
        int maxOut = 0;
        for (String out : rules.values()) {
            maxOut = Math.max(maxOut, out.length());
        }
        for (int step = 0; step < steps; step++) {
            if (state == null || state.isEmpty()) {
                state = parts[2];
            }
            String stateNew = state;
            List<String[]> tags = new ArrayList<>();
            List<int[]> pairs = new ArrayList<>();
            List<String> slices = new ArrayList<>();
            for (int size = 1; size <= maxOut; size++) {
                for (int i = 0; i < state.length(); i += size) {
                    String slice = state.substring(i, Math.min(state.length(), i + size));
                    boolean seen = false;
                    for (int p = 0; p < pairs.size(); p++) {
                        if (pairs.get(p)[0] == i && slices.get(p).equals(slice)) {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen) {
                        pairs.add(new int[] {i});
                        slices.add(slice);
                    }
                }
            }
            int replacement = 0;
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                for (String slice : slices) {
                    if (!rule.getKey().equals(slice)) {
                        continue;
                    }
                    int found = stateNew.indexOf(slice);
                    String tag = "<" + replacement + ">";
                    replacement++;
                    stateNew = stateNew.substring(0, found) + tag + stateNew.substring(found + slice.length());
                    tags.add(new String[] {tag, rule.getValue()});
                }
            }
            for (String[] tag : tags) {
                int found = stateNew.indexOf(tag[0]);
                stateNew = stateNew.substring(0, found) + tag[1] + stateNew.substring(found + tag[0].length());
            }
            state = stateNew;
        }
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < state.length(); i++) {
            values.add(symbols.get(state.substring(i, i + 1)));
        }
        return values;
    }

    private static void readPairs(String text, Map<String, Double> symbols, Map<String, String> rules, boolean symbol) {
        for (String group : text.split("}")) {
            int open = group.indexOf('{');
            if (open < 0) {
                continue;
            }
            String key = group.substring(0, open).trim();
            String value = group.substring(open + 1).trim();
            if (symbol) {
                symbols.put(key, Double.parseDouble(value));
            } else {
                rules.put(key, value);
            }
        }
    }

    static List<Double> markov(List<Double> values, List<Double> weights, CompatRandom random, int n) {
        List<ValueMath.Bound> bounds = unitBoundaryProportion(weights);
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int pick = unitBoundaryPos(random.random(), bounds);
            out.add(values.get(pick));
        }
        return out;
    }

    static List<Double> climate(CompatRandom random, int n) {
        List<Particle> particles = new ArrayList<>();
        List<Transformer> transformers = new ArrayList<>();
        List<Double> out = new ArrayList<>();
        for (int sample = 0; sample < n; sample++) {
            for (Transformer transformer : new ArrayList<>(transformers)) {
                transformer.apply(particles, random);
            }
            List<Transformer> living = new ArrayList<>();
            for (Transformer transformer : transformers) {
                transformer.age += 1;
                if (transformer.age <= 2) {
                    living.add(transformer);
                }
            }
            transformers = living;
            List<Particle> alive = new ArrayList<>();
            for (Particle particle : particles) {
                particle.age += 1;
                if (particle.age <= 5) {
                    alive.add(particle);
                }
            }
            particles = alive;
            int sense = particles.size();
            for (int sensor = 0; sensor < 10; sensor++) {
                if (sense < 100) {
                    int count = production(100 - sense, random);
                    for (int i = 0; i < count; i++) {
                        particles.add(new Particle());
                    }
                } else if (sense > 100) {
                    int count = production(100 - sense, random);
                    for (int i = 0; i < count; i++) {
                        transformers.add(new Transformer());
                    }
                }
            }
            double count = particles.size();
            count = boundaryFitLimit(30, 140, count);
            out.add(unitNorm(count, 30, 140));
        }
        return out;
    }

    private static int production(int span, CompatRandom random) {
        int[] range = {1, 8};
        if (span >= -30 && span <= -10) {
            range = new int[] {6, 8};
        } else if (span >= 1 && span <= 10) {
            range = new int[] {1, 2};
        } else if (span >= 11 && span <= 20) {
            range = new int[] {1, 4};
        }
        return random.randint(range[0], range[1]);
    }

    private static final class Particle {
        int age;
    }

    private static final class Transformer {
        int age;

        void apply(List<Particle> particles, CompatRandom random) {
            if (particles.isEmpty()) {
                return;
            }
            int index = random.randint(0, particles.size() - 1);
            List<ValueMath.Bound> bounds = unitBoundaryProportion(List.of(1.0));
            unitBoundaryPos(random.random(), bounds);
            particles.remove(index);
        }
    }
}
