// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.Acronyms;
import io.github.sosgames.athenacl.compat.CompatRandom;
import io.github.sosgames.athenacl.compat.PoArgs;
import io.github.sosgames.athenacl.compat.PyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The parameter objects a LineGroove beat needs. {@code tpsd} seeds the parameter
 * generator (a basket that shuffles). {@code tmsd} seeds the texture generator
 * (a uniform draw and the beta amplitude). They are not the same stream.
 * A three-argument {@code pulseTriple} still draws a sustain between 0.5 and 1.5.
 */
public final class ParameterObjects {
    private static final Map<String, String> NAMES = Map.of(
            "c", "constant",
            "bg", "basketGen",
            "pt", "pulseTriple",
            "ru", "randomUniform",
            "rb", "randomBeta",
            "oc", "orderedCyclic",
            "rp", "randomPermutate");

    private ParameterObjects() {
    }

    public record Streams(CompatRandom parameter, CompatRandom texture) {
        public static Streams seeds(long parameterSeed, long textureSeed) {
            return new Streams(CompatRandom.seed(parameterSeed), CompatRandom.seed(textureSeed));
        }
    }

    public record Rhythm(double dur, double sus, double acc) {
    }

    public static List<String> sample(String name, String args, int n, long seed) {
        if (n < 1) {
            throw new ScriptException("ATHENA_SYNTAX", "n", "n must be at least 1", "Try --n 6.");
        }
        String full = Acronyms.expand(name, NAMES);
        if (!"basketGen".equals(full) && !"constant".equals(full)) {
            throw new ScriptException("ATHENA_TEXTURE", "po",
                    "parameter object " + name + " is not in this slice",
                    "Try po sample basketGen \"oc,(1,2,3)\" --n 6.");
        }
        List<PyValue> row = PoArgs.parse(args == null || args.isBlank() ? name : name + "," + args);
        if (!(value(call(row)) instanceof Value value)) {
            throw new ScriptException("ATHENA_TEXTURE", "po",
                    "parameter object " + name + " is not a value",
                    "Try po sample basketGen \"oc,(1,2,3)\" --n 6.");
        }
        Streams streams = Streams.seeds(seed, seed);
        List<String> draws = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            draws.add(format(value.next(streams)));
        }
        return draws;
    }

    public static RhythmPo rhythm(String spec) {
        List<PyValue> row = PoArgs.parse(spec);
        if (row.isEmpty()) {
            throw new ScriptException("ATHENA_SYNTAX", "tie", "rhythm needs a parameter object",
                    "Try tie r pt,(c,2),(c,1),(c,1).");
        }
        PyValue.Seq seq = call(row);
        if (!(value(seq) instanceof RhythmPo rhythm)) {
            throw new ScriptException("ATHENA_TEXTURE", "tie", "that parameter is not a rhythm",
                    "tie r takes pulseTriple.");
        }
        return rhythm;
    }

    public static Value betaAmplitude() {
        return new Beta(0.4, 0.4, 0.7, 0.9);
    }

    private static PyValue.Seq call(List<PyValue> row) {
        if (row.size() == 1 && row.get(0) instanceof PyValue.Seq seq) {
            return seq;
        }
        return new PyValue.Seq(row, true);
    }

    private static Object value(PyValue.Seq seq) {
        if (seq.items().isEmpty() || !(seq.items().get(0) instanceof PyValue.Str type)) {
            throw new ScriptException("ATHENA_SYNTAX", "po", "a parameter object starts with its name",
                    "Try basketGen or pt.");
        }
        String full = Acronyms.expand(type.value(), NAMES);
        return switch (full) {
            case "constant" -> new Constant(number(seq.items().get(1)));
            case "basketGen" -> basket(seq);
            case "randomUniform" -> uniform(seq);
            case "randomBeta" -> beta(seq);
            case "pulseTriple" -> pulse(seq);
            default -> throw new ScriptException("ATHENA_TEXTURE", "po",
                    "parameter object " + type.value() + " is not in this slice",
                    "This slice has constant, basketGen, pulseTriple, randomUniform, and randomBeta.");
        };
    }

    private static Basket basket(PyValue.Seq seq) {
        String control = Acronyms.expand(text(seq.items().get(1)), NAMES);
        List<Double> values = numbers(seq.items().get(2));
        if ("orderedCyclic".equals(control)) {
            return new Basket(values, false);
        }
        if ("randomPermutate".equals(control)) {
            return new Basket(values, true);
        }
        throw new ScriptException("ATHENA_TEXTURE", "po",
                "basket control " + text(seq.items().get(1)) + " is not in this slice",
                "Use oc or rp.");
    }

    private static Uniform uniform(PyValue.Seq seq) {
        double min = number(seq.items().get(1));
        double max = number(seq.items().get(2));
        return new Uniform(min, max);
    }

    private static Beta beta(PyValue.Seq seq) {
        return new Beta(number(seq.items().get(1)), number(seq.items().get(2)),
                number(seq.items().get(3)), number(seq.items().get(4)));
    }

    private static PulseTriple pulse(PyValue.Seq seq) {
        List<PyValue> args = seq.items().subList(1, seq.items().size());
        Value div = child(args.get(0));
        Value mult = child(args.get(1));
        Value acc = child(args.get(2));
        Value sus = args.size() >= 4 ? child(args.get(3)) : new Uniform(0.5, 1.5);
        return new PulseTriple(div, mult, acc, sus);
    }

    private static Value child(PyValue node) {
        if (node instanceof PyValue.IntNum num) {
            return new Constant(num.value());
        }
        if (node instanceof PyValue.FloatNum num) {
            return new Constant(num.value());
        }
        if (node instanceof PyValue.Seq seq) {
            Object built = value(seq);
            if (built instanceof Value value) {
                return value;
            }
        }
        throw new ScriptException("ATHENA_SYNTAX", "po", "bad parameter argument",
                "Each argument is a number or a parameter object.");
    }

    private static String text(PyValue node) {
        if (node instanceof PyValue.Str str) {
            return str.value();
        }
        throw new ScriptException("ATHENA_SYNTAX", "po", "expected a name",
                "Basket control is oc or rp.");
    }

    private static double number(PyValue node) {
        if (node instanceof PyValue.IntNum num) {
            return num.value();
        }
        if (node instanceof PyValue.FloatNum num) {
            return num.value();
        }
        if (node instanceof PyValue.Seq seq && seq.items().size() == 1) {
            return number(seq.items().get(0));
        }
        throw new ScriptException("ATHENA_SYNTAX", "po", "expected a number",
                "Constant takes one number.");
    }

    private static List<Double> numbers(PyValue node) {
        if (!(node instanceof PyValue.Seq seq)) {
            return List.of(number(node));
        }
        List<Double> values = new ArrayList<>();
        for (PyValue item : seq.items()) {
            values.add(number(item));
        }
        return values;
    }

    static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    static double denorm(double value, double a, double b) {
        if (a == b) {
            return a;
        }
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        return value * (max - min) + min;
    }

    public sealed interface Value permits Constant, Basket, Uniform, Beta {
        double next(Streams streams);
    }

    public record Constant(double value) implements Value {
        @Override
        public double next(Streams streams) {
            return value;
        }
    }

    public static final class Basket implements Value {
        private final List<Double> ref;
        private final boolean permute;
        private final List<Double> scratch = new ArrayList<>();
        private int index;

        Basket(List<Double> ref, boolean permute) {
            this.ref = List.copyOf(ref);
            this.permute = permute;
        }

        @Override
        public double next(Streams streams) {
            if (ref.size() == 1) {
                return ref.get(0);
            }
            if (!permute) {
                if (index == ref.size()) {
                    index = 0;
                }
                return ref.get(index++);
            }
            if (scratch.isEmpty()) {
                scratch.addAll(ref);
            }
            Double picked = streams.parameter.choice(scratch);
            scratch.remove(scratch.indexOf(picked));
            return picked;
        }
    }

    public record Uniform(double min, double max) implements Value {
        @Override
        public double next(Streams streams) {
            return denorm(streams.texture.random(), min, max);
        }
    }

    public record Beta(double alpha, double beta, double min, double max) implements Value {
        @Override
        public double next(Streams streams) {
            double coin = streams.texture.random();
            double y = expo(alpha, streams);
            double z = expo(1.0 / beta, streams);
            double unit = z / (y + z);
            if (coin > 0.5) {
                unit = 1.0 - unit;
            }
            return denorm(unit, min, max);
        }

        private static double expo(double lambd, Streams streams) {
            double u = streams.texture.random();
            while (u <= 1e-7) {
                u = streams.texture.random();
            }
            return -StrictMath.log(u) / lambd;
        }
    }

    public record PulseTriple(Value div, Value mult, Value acc, Value sus) implements RhythmPo {
        @Override
        public Rhythm next(double bpm, Streams streams) {
            int divisor = Math.abs(whole(div.next(streams)));
            int multiplier = Math.abs(whole(mult.next(streams)));
            if (divisor == 0) {
                divisor = 1;
            }
            if (multiplier == 0) {
                multiplier = 1;
            }
            double accent = Math.abs(acc.next(streams));
            if (accent <= 0) {
                accent = 0;
            } else if (accent >= 1) {
                accent = 1;
            }
            double sustain = Math.abs(sus.next(streams));
            if (sustain == 0) {
                sustain = 1;
            }
            double dur = (60.0 / bpm / divisor) * multiplier;
            return new Rhythm(dur, dur * sustain, accent);
        }

        private static int whole(double value) {
            return (int) value;
        }
    }

    public sealed interface RhythmPo permits PulseTriple {
        Rhythm next(double bpm, Streams streams);
    }
}
