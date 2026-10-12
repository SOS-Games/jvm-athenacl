// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Texture-static switches, the two clone-static switches, and the clone filters.
 * The plan's 27, 3, and 17 counted private bases. The public lists are 22, 2, and 15.
 * Filters run on the values 1 through 6. A selector written {@code oc} is stored as
 * ordered cyclic. An anchor multiply shifts by the lowest or highest value, scales,
 * then shifts back.
 */
public final class TextureClone {
    public record Check(int pass, int fail, List<String> failed) {
    }

    private static final double[] VALUES = {1, 2, 3, 4, 5, 6};

    private TextureClone() {
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
                case "module" -> moduleOk(cells);
                case "static" -> staticOk(cells);
                case "filter" -> filterOk(cells);
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

    private static boolean moduleOk(String[] cells) {
        for (TextureModules.Module module : TextureModules.modules()) {
            if (module.acronym().equals(cells[1])) {
                return module.name().equals(cells[2]) && module.parameters().equals(cells[3]);
            }
        }
        return false;
    }

    private static boolean staticOk(String[] cells) {
        List<String> got = switches(cells[1]);
        if (got.size() != cells.length - 2) {
            return false;
        }
        for (int i = 0; i < got.size(); i++) {
            if (!same(cells[i + 2], got.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean filterOk(String[] cells) {
        double[] got = filter(cells[1]);
        String[] expect = cells[2].split(",");
        if (got.length != expect.length) {
            return false;
        }
        for (int i = 0; i < got.length; i++) {
            if (!OmdeGenerators.near(Double.parseDouble(expect[i]), got[i])) {
                return false;
            }
        }
        return true;
    }

    static List<String> switches(String acronym) {
        return switch (acronym) {
            case "pml" -> List.of(nums(1, 2), num(0.25));
            case "mto" -> List.of(num(0.1));
            case "lws", "sst", "pdf" -> List.of(onOff("on"));
            case "ols" -> List.of("off");
            case "omd" -> List.of(num(0.5));
            case "lfm", "lop", "lfd" -> List.of(level("event", "event", "set", "voice"));
            case "lom" -> List.of(level("set", "event", "set", "voice"));
            case "lfp" -> List.of(level("voice", "event", "set", "voice"));
            case "lep" -> List.of(level("path", "set", "path"));
            case "imc" -> List.of(level("linear", "linear"));
            case "pic", "set", "rmt" -> List.of(onOff("off"));
            case "tec" -> List.of(num(8));
            case "tsc" -> List.of(num(4));
            case "edp" -> List.of(level("duration", "duration", "set"));
            case "lec" -> List.of(level("segment", "segment", "texture"));
            case "psc", "msc" -> List.of(selector("oc"));
            case "trs" -> List.of(level("textureTime", "textureTime", "cloneTime"));
            default -> throw new IllegalArgumentException(acronym);
        };
    }

    static double[] filter(String acronym) {
        double[] values = VALUES.clone();
        return switch (acronym) {
            case "b" -> values;
            case "ob" -> reverse(values);
            case "or" -> rotate(values, 2);
            case "pl" -> reverse(rotate(values, 2));
            case "r" -> fill(9);
            case "fa" -> map(values, (v) -> v + 3);
            case "fm" -> map(values, (v) -> v * 2);
            case "fd" -> map(values, (v) -> v / 2.0);
            case "fp" -> map(values, (v) -> StrictMath.pow(v, 2));
            case "fma" -> anchor(values, true, false);
            case "fda" -> anchor(values, false, true);
            case "fq" -> map(values, (v) -> attract(v, 0, 2, 1));
            case "ffb" -> map(values, (v) -> funnel(3.5, 0, 10, v));
            case "mf" -> map(values, (v) -> limit(2, 5, v));
            case "msf" -> scale(values, 2, 5);
            default -> throw new IllegalArgumentException(acronym);
        };
    }

    private static boolean same(String expect, String got) {
        try {
            return OmdeGenerators.near(Double.parseDouble(expect), Double.parseDouble(got));
        } catch (NumberFormatException e) {
            return expect.equals(got);
        }
    }

    private static String selector(String raw) {
        return switch (raw) {
            case "oc", "1", "orderedCyclic" -> "orderedCyclic";
            case "rp", "randomPermutate" -> "randomPermutate";
            default -> throw new IllegalArgumentException(raw);
        };
    }

    private static String onOff(String raw) {
        return switch (raw) {
            case "on", "1" -> "on";
            case "off", "0" -> "off";
            default -> throw new IllegalArgumentException(raw);
        };
    }

    private static String level(String raw, String... allowed) {
        for (String name : allowed) {
            if (name.equals(raw)) {
                return name;
            }
        }
        throw new IllegalArgumentException(raw);
    }

    private static String num(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String nums(double... values) {
        StringBuilder text = new StringBuilder();
        for (double value : values) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(num(value));
        }
        return text.toString();
    }

    private static double[] fill(double value) {
        double[] out = new double[VALUES.length];
        java.util.Arrays.fill(out, value);
        return out;
    }

    private static double[] map(double[] values, java.util.function.DoubleUnaryOperator op) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = op.applyAsDouble(values[i]);
        }
        return out;
    }

    private static double[] reverse(double[] values) {
        double[] out = values.clone();
        for (int i = 0; i < out.length / 2; i++) {
            double swap = out[i];
            out[i] = out[out.length - 1 - i];
            out[out.length - 1 - i] = swap;
        }
        return out;
    }

    private static double[] rotate(double[] values, int steps) {
        int cut = Math.floorMod(steps, values.length);
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[(i + cut) % values.length];
        }
        return out;
    }

    private static double[] anchor(double[] values, boolean lower, boolean divide) {
        double edge = lower ? min(values) : max(values);
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double shifted = values[i] - edge;
            out[i] = (divide ? shifted / 2.0 : shifted * 2.0) + edge;
        }
        return out;
    }

    private static double attract(double value, double gridRef, double step, double pull) {
        double[] bounds = bounds(value, gridRef, step);
        double low = bounds[0];
        double high = bounds[1];
        if (low == high) {
            return low;
        }
        double difLow = Math.abs(low - value);
        double difHigh = Math.abs(high - value);
        boolean up = difLow >= difHigh;
        double gridPoint = up ? high : low;
        if (pull == 1) {
            return gridPoint;
        }
        double deviate = (up ? difHigh : difLow) * (1 - pull);
        return up ? gridPoint - deviate : gridPoint + deviate;
    }

    private static double[] bounds(double value, double gridRef, double step) {
        boolean down = gridRef >= value;
        double previous = gridRef;
        for (int n = 0; n < 999; n++) {
            double next = down ? previous - step : previous + step;
            double low = Math.min(previous, next);
            double high = Math.max(previous, next);
            if (value >= low && value <= high) {
                return new double[] {low, high};
            }
            previous = next;
        }
        throw new IllegalStateException("no grid neighbors");
    }

    private static double funnel(double threshold, double a, double b, double value) {
        double low = Math.min(a, b);
        double high = Math.max(a, b);
        if (value > threshold) {
            return high;
        }
        if (value < threshold) {
            return low;
        }
        return high;
    }

    private static double limit(double a, double b, double value) {
        double low = Math.min(a, b);
        double high = Math.max(a, b);
        if (value > high) {
            return high;
        }
        if (value < low) {
            return low;
        }
        return value;
    }

    private static double[] scale(double[] values, double lowOut, double highOut) {
        double low = min(values);
        double high = max(values);
        double span = high - low;
        double[] out = new double[values.length];
        double spanOut = highOut - lowOut;
        for (int i = 0; i < values.length; i++) {
            double unit = span == 0 ? 0 : (values[i] - low) / span;
            out[i] = unit * spanOut + lowOut;
        }
        return out;
    }

    private static double min(double[] values) {
        double edge = values[0];
        for (double value : values) {
            edge = Math.min(edge, value);
        }
        return edge;
    }

    private static double max(double[] values) {
        double edge = values[0];
        for (double value : values) {
            edge = Math.max(edge, value);
        }
        return edge;
    }
}
