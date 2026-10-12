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
 * The 36 public wave, distribution, breakpoint, and envelope generators.
 * The plan's 44 counted eight private bases; those bases are not generators.
 * Gauss draws on the parameter stream. Every other distribution draws on the
 * texture stream. Both are seeded to 7 before a generator is sampled.
 */
public final class OmdeGenerators {
    public record Generator(String acronym, String name) {
    }

    public record Check(int pass, int fail, List<String> failed) {
    }

    private static final double OFFSET = 1e-9;

    private static final List<Generator> OMDE = List.of(
            new Generator("ws", "waveSine"),
            new Generator("wc", "waveCosine"),
            new Generator("wsu", "waveSawUp"),
            new Generator("wsd", "waveSawDown"),
            new Generator("wp", "wavePulse"),
            new Generator("wt", "waveTriangle"),
            new Generator("whps", "waveHalfPeriodSine"),
            new Generator("whpc", "waveHalfPeriodCosine"),
            new Generator("whpp", "waveHalfPeriodPulse"),
            new Generator("whpt", "waveHalfPeriodTriangle"),
            new Generator("wpu", "wavePowerUp"),
            new Generator("wpd", "wavePowerDown"),
            new Generator("ru", "randomUniform"),
            new Generator("rl", "randomLinear"),
            new Generator("ril", "randomInverseLinear"),
            new Generator("rt", "randomTriangular"),
            new Generator("rit", "randomInverseTriangular"),
            new Generator("re", "randomExponential"),
            new Generator("rie", "randomInverseExponential"),
            new Generator("rbe", "randomBilateralExponential"),
            new Generator("rg", "randomGauss"),
            new Generator("rc", "randomCauchy"),
            new Generator("rb", "randomBeta"),
            new Generator("rw", "randomWeibull"),
            new Generator("bpl", "breakPointLinear"),
            new Generator("bpp", "breakPointPower"),
            new Generator("bphc", "breakPointHalfCosine"),
            new Generator("bpf", "breakPointFlat"),
            new Generator("bgl", "breakGraphLinear"),
            new Generator("bgp", "breakGraphPower"),
            new Generator("bghc", "breakGraphHalfCosine"),
            new Generator("bgf", "breakGraphFlat"),
            new Generator("ls", "lineSegment"),
            new Generator("egt", "envelopeGeneratorTrapezoid"),
            new Generator("egu", "envelopeGeneratorUnit"),
            new Generator("ega", "envelopeGeneratorAdsr"));

    private OmdeGenerators() {
    }

    public static List<Generator> omdeGroup() {
        return OMDE;
    }

    public static List<Double> sample(String acronym) {
        CompatRandom parameter = CompatRandom.seed(7);
        CompatRandom texture = CompatRandom.seed(7);
        double[] series = series(acronym, parameter, texture);
        List<Double> out = new ArrayList<>();
        for (double value : series) {
            out.add(value);
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
            List<Double> got = sample(cells[0]);
            boolean ok = true;
            for (int i = 0; i < 6; i++) {
                if (!near(Double.parseDouble(cells[i + 1]), got.get(i))) {
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

    static boolean near(double expect, double got) {
        double gap = Math.abs(expect - got);
        return gap <= 1e-9 || gap <= 1e-9 * Math.max(1.0, Math.abs(expect));
    }

    private static double[] series(String acronym, CompatRandom parameter, CompatRandom texture) {
        return switch (acronym) {
            case "ws" -> waveSteps("sine", 0);
            case "wc" -> waveSteps("cosine", 0);
            case "wsu" -> waveSteps("sawUp", 0);
            case "wsd" -> waveSteps("sawDown", 0);
            case "wp" -> waveSteps("square", 0);
            case "wt" -> waveSteps("triangle", 0);
            case "whps" -> halfPeriod("sine");
            case "whpc" -> halfPeriod("cosine");
            case "whpp" -> halfPeriod("square");
            case "whpt" -> halfPeriod("triangle");
            case "wpu" -> waveSteps("powerUp", StrictMath.pow(2.0, 2));
            case "wpd" -> waveSteps("powerDown", StrictMath.pow(2.0, 2));
            case "ru" -> fill(texture::random);
            case "rl" -> fill(() -> Math.min(texture.random(), texture.random()));
            case "ril" -> fill(() -> Math.max(texture.random(), texture.random()));
            case "rt" -> fill(() -> triangular(texture, false));
            case "rit" -> fill(() -> triangular(texture, true));
            case "re" -> fill(() -> exponential(texture, false));
            case "rie" -> fill(() -> exponential(texture, true));
            case "rbe" -> fill(() -> bilateral(texture));
            case "rg" -> fill(() -> gauss(parameter));
            case "rc" -> fill(() -> cauchy(texture));
            case "rb" -> fill(() -> beta(texture));
            case "rw" -> fill(() -> weibull(texture));
            case "bpl" -> breakSteps(false, "linear", 1);
            case "bpp" -> breakSteps(true, "power", 2);
            case "bphc" -> breakSteps(false, "cosine", 1);
            case "bpf" -> breakSteps(true, "flat", 1);
            case "bgl" -> graphSteps("linear", 1);
            case "bgp" -> graphSteps("power", 2);
            case "bghc" -> graphSteps("cosine", 1);
            case "bgf" -> graphSteps("flat", 1);
            case "ls" -> lineSteps();
            case "egt" -> envelope(trapezoid());
            case "egu" -> envelope(unit());
            case "ega" -> envelope(adsr());
            default -> throw new IllegalArgumentException(acronym);
        };
    }

    private static double[] waveSteps(String shape, double power) {
        double[] out = new double[6];
        for (int t = 0; t < 6; t++) {
            out[t] = wave(t, 0.25, 0, shape, power);
        }
        return out;
    }

    private static double[] halfPeriod(String shape) {
        double[] out = new double[6];
        Double start = null;
        double end = 0;
        double shift = 0;
        Double phase = null;
        for (int step = 0; step < 6; step++) {
            if (start == null || step < start || step >= end) {
                double spc = 8;
                shift = -step;
                start = (double) step;
                end = step + spc * 0.5;
                phase = phase == null || phase == 0.5 ? 0.0 : 0.5;
            }
            out[step] = wave(step + shift, 1.0 / 8.0, phase, shape, 0);
        }
        return out;
    }

    private static double wave(double t, double frequency, double phase0, String shape, double power) {
        double period = 1.0 / frequency;
        if ("sine".equals(shape) || "cosine".equals(shape)) {
            double phase = (phase0 * period + t) * frequency;
            double turn = 2.0 * Math.PI * phase;
            double trig = "sine".equals(shape) ? StrictMath.sin(turn) : StrictMath.cos(turn);
            return (1.0 + trig) / 2.0;
        }
        t += period * phase0;
        t %= period;
        if (t < 0) {
            t += period;
        }
        return switch (shape) {
            case "sawUp" -> t / period;
            case "sawDown" -> 1.0 - t / period;
            case "square" -> t < period / 2.0 ? 1.0 : 0.0;
            case "triangle" -> t < period * 0.5
                    ? t / (period * 0.5)
                    : 1.0 - (t - period * 0.5) / (period * 0.5);
            case "powerUp" -> StrictMath.pow(t / period, power);
            case "powerDown" -> StrictMath.pow(1.0 - t / period, power);
            default -> throw new IllegalArgumentException(shape);
        };
    }

    private static double triangular(CompatRandom texture, boolean inverse) {
        while (true) {
            double a = texture.random();
            double b = texture.random() / 2.0;
            boolean low = a < 0.5 && (inverse ? a < b : a > b);
            boolean high = a >= 0.5 && (inverse ? (a - 0.5) > b : (a - 0.5) < b);
            if (low || high) {
                return a;
            }
        }
    }

    private static double exponential(CompatRandom texture, boolean inverse) {
        while (true) {
            double raw = expo(texture, 0.5);
            double value = inverse ? 1.0 - raw : raw;
            if (inverse ? value > 0.0 : value < 1.0) {
                return value;
            }
        }
    }

    private static double bilateral(CompatRandom texture) {
        double raw;
        do {
            raw = expo(texture, 0.5);
        } while (raw >= 1.0);
        if (texture.random() > 0.5) {
            return 0.5 + raw / 2.0;
        }
        return 0.5 - raw / 2.0;
    }

    private static double gauss(CompatRandom parameter) {
        while (true) {
            double value = parameter.gauss(0.5, 0.1);
            if (value >= 0.0 && value <= 1.0) {
                return value;
            }
        }
    }

    private static double cauchy(CompatRandom texture) {
        while (true) {
            double x;
            do {
                x = texture.random();
            } while (x == 0.5);
            double value = 0.1 * StrictMath.tan(x * Math.PI) + 0.5;
            if (value >= 0.0 && value <= 1.0) {
                return value;
            }
        }
    }

    private static double beta(CompatRandom texture) {
        double coin = texture.random();
        double unit = betaUnit(texture, 0.5, 0.5);
        return coin > 0.5 ? 1.0 - unit : unit;
    }

    private static double betaUnit(CompatRandom texture, double alpha, double beta) {
        double y = expo(texture, alpha);
        double z = expo(texture, 1.0 / beta);
        return z / (y + z);
    }

    private static double weibull(CompatRandom texture) {
        while (true) {
            double u = texture.random();
            double value = 0.5 * StrictMath.pow(-StrictMath.log(u), 1.0 / 2.0);
            if (value >= 0.0 && value <= 1.0) {
                return value;
            }
        }
    }

    private static double expo(CompatRandom texture, double lambd) {
        double u = texture.random();
        while (u <= 1e-7) {
            u = texture.random();
        }
        return -StrictMath.log(u) / lambd;
    }

    private static double[] breakSteps(boolean loop, String kind, double exp) {
        return walk(points(0, 0, 3, 1, 6, 0), loop, kind, exp);
    }

    private static double[] graphSteps(String kind, double exp) {
        return walk(points(0, 0, 2, 1, 4, 0.5, 6, 0.25), true, kind, exp);
    }

    private static double[] lineSteps() {
        double[] out = new double[6];
        double start = Double.NaN;
        double end = 0;
        List<double[]> segment = List.of();
        for (int step = 0; step < 6; step++) {
            if (Double.isNaN(start) || step >= end || step < start) {
                start = step;
                end = step + 4;
                segment = List.of(new double[] {start, 0}, new double[] {end, 1});
            }
            out[step] = at(segment, step, true, "linear", 1);
        }
        return out;
    }

    private static double[] envelope(List<double[]> points) {
        return walk(points, true, "linear", 1);
    }

    private static List<double[]> trapezoid() {
        return shapes(8, 4, spans(4, 0.5, 2, 1, 1), true);
    }

    private static List<double[]> adsr() {
        double[] span = spans(4, 1, 2, 1, 0.5);
        List<double[]> points = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < 8; i++) {
            double attack = step(span[0]);
            double decay = step(span[1]);
            double sustain = step(span[2]);
            points.add(new double[] {t, 0});
            points.add(new double[] {t + attack, 1});
            points.add(new double[] {t + attack + decay, 0.5});
            points.add(new double[] {t + attack + decay + sustain, 0.5});
            points.add(new double[] {t + 4 - OFFSET, 0});
            t += 4;
        }
        return points;
    }

    private static List<double[]> unit() {
        double center = 0.4;
        double width = 0.2;
        double rampUp = (1 - width) * center * 4;
        double hold = width * 4;
        List<double[]> points = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < 8; i++) {
            points.add(new double[] {t, 0});
            points.add(new double[] {t + rampUp, 1});
            points.add(new double[] {t + rampUp + hold, 1});
            points.add(new double[] {t + 4 - OFFSET, 0});
            t += 4;
        }
        return points;
    }

    private static List<double[]> shapes(int count, double dur, double[] span, boolean trapezoid) {
        List<double[]> points = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < count; i++) {
            double cursor = t;
            points.add(new double[] {cursor, 0});
            cursor += step(span[0]);
            points.add(new double[] {cursor, 1});
            cursor += step(span[1]);
            points.add(new double[] {cursor, 1});
            cursor += step(span[2]);
            points.add(new double[] {cursor, 0});
            if (trapezoid) {
                points.add(new double[] {t + dur - OFFSET, 0});
            }
            t += dur;
        }
        return points;
    }

    private static double[] spans(double dur, double a, double b, double c, double d) {
        List<Double> unit = ValueMath.unitNormProportion(List.of(a, b, c, d));
        return new double[] {unit.get(0) * dur, unit.get(1) * dur, unit.get(2) * dur, unit.get(3) * dur};
    }

    private static double step(double shift) {
        return shift <= 0 ? OFFSET : shift;
    }

    private static double[] walk(List<double[]> points, boolean loop, String kind, double exp) {
        double[] out = new double[6];
        for (int t = 0; t < 6; t++) {
            out[t] = at(points, t, loop, kind, exp);
        }
        return out;
    }

    private static double at(List<double[]> points, double t, boolean loop, String kind, double exp) {
        double x0 = points.get(0)[0];
        double x1 = points.get(points.size() - 1)[0];
        double period = x1 - x0;
        if (loop) {
            while (t < x0) {
                t += period;
            }
            while (t >= x1) {
                t -= period;
            }
        } else if (t < x0) {
            return points.get(0)[1];
        }
        double time0 = points.get(0)[0];
        double value0 = points.get(0)[1];
        int index = 0;
        for (double[] point : points) {
            if (!loop && t < point[0]) {
                break;
            }
            if (loop && t < point[0]) {
                return mix(t, time0, value0, point[0], point[1], kind, exp);
            }
            index++;
            time0 = point[0];
            value0 = point[1];
        }
        if (!loop && index == points.size()) {
            return value0;
        }
        if (!loop) {
            double[] next = points.get(index);
            return mix(t, time0, value0, next[0], next[1], kind, exp);
        }
        return value0;
    }

    private static double mix(double time, double time0, double value0, double time1, double value1,
            String kind, double exp) {
        if ("flat".equals(kind)) {
            return value0;
        }
        double span = time1 - time0;
        double r = span == 0 ? 0 : (time - time0) / span;
        if ("linear".equals(kind)) {
            return value0 + r * (value1 - value0);
        }
        if ("cosine".equals(kind)) {
            double x = r * Math.PI + Math.PI;
            return value0 + (value1 - value0) * (1 + StrictMath.cos(x)) / 2.0;
        }
        if (value1 == value0 || exp == 0) {
            return value0 + r * (value1 - value0);
        }
        if (exp > 0) {
            if (value1 >= value0) {
                return value0 + StrictMath.pow(r, 1.0 + exp) * (value1 - value0);
            }
            return value1 + StrictMath.pow(1.0 - r, 1.0 + exp) * (value0 - value1);
        }
        if (value1 >= value0) {
            return value1 + StrictMath.pow(1.0 - r, 1.0 - exp) * (value0 - value1);
        }
        return value0 + StrictMath.pow(r, 1.0 - exp) * (value1 - value0);
    }

    private static List<double[]> points(double... pairs) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add(new double[] {pairs[i], pairs[i + 1]});
        }
        return out;
    }

    private static double[] fill(Draw draw) {
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            out[i] = draw.next();
        }
        return out;
    }

    @FunctionalInterface
    private interface Draw {
        double next();
    }
}
