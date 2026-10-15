// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import java.util.Locale;

/**
 * What kind of preset this is, decided before any pass or fail.
 * A kit line {@code class:} wins. Otherwise the name, the GM program family,
 * and the attack tell the same story: a taiko is not judged like a flute.
 */
public final class SoundClass {
    public static final String SUSTAINED = "pitched-sustained";
    public static final String PERCUSSIVE = "pitched-percussive";
    public static final String UNPITCHED = "unpitched-percussion";
    public static final String TUNED = "tuned-percussion";
    public static final String ENSEMBLE = "ensemble";
    public static final String SYNTH = "synth-raw";

    private SoundClass() {
    }

    public static String classify(String name, int program, boolean percussion, String override,
                                  double attackMs, double decayMs, boolean died, double flatness) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (percussion || n.contains("drum") || n.contains("taiko")
                || (program >= 112 && program <= 119)) {
            return UNPITCHED;
        }
        if (n.contains("timpani") || n.contains("bell") || n.contains("celesta")
                || program == 47 || program == 9 || program == 14) {
            return TUNED;
        }
        if (n.contains("choir") || n.contains("strings") || n.contains("ensemble")
                || (program >= 48 && program <= 54)) {
            return ENSEMBLE;
        }
        if (n.contains("square") || n.contains("saw") || n.contains("synth")
                || (program >= 80 && program <= 87) || program == 38 || program == 39) {
            return SYNTH;
        }
        if (n.contains("koto") || n.contains("kalimba") || n.contains("shamisen") || n.contains("pizz")
                || (died && attackMs <= 40 && decayMs > 0 && decayMs <= 400 && flatness < 0.35)
                || (program >= 11 && program <= 13 && died)) {
            return PERCUSSIVE;
        }
        if ((program >= 8 && program <= 15) && !died) {
            return TUNED;
        }
        return SUSTAINED;
    }
}
