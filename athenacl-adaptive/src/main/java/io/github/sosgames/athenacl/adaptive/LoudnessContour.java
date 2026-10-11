// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.util.Locale;
import java.util.Map;

/**
 * How loud each state should sit, in LU relative to {@code reference}.
 * Combat is a few LU louder than sneaking. The spread is the distance between the loudest and quietest.
 */
public record LoudnessContour(double reference, Map<String, Double> offsets, double maxSpread, double maxStep) {
    public LoudnessContour(double reference, Map<String, Double> offsets, double maxSpread) {
        this(reference, offsets, maxSpread, 4);
    }

    public LoudnessContour {
        offsets = Map.copyOf(offsets);
    }

    public double offset(String state) {
        return offsets.getOrDefault(state, 0.0);
    }

    public double target(String state) {
        return reference + offset(state);
    }

    public double spread() {
        if (offsets.isEmpty()) {
            return 0;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : offsets.values()) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    /** One decimal with a sign, such as {@code +4.0} or {@code -1.5}. */
    public static String tenths(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }
}
