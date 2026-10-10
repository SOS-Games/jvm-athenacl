// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

import java.util.Locale;

/**
 * Where the off-beat eighth sits inside the quarter. {@code 0.50} is straight.
 * {@code 0.62} lands the off-beat a little late, which is a light swing.
 * The number is a fraction of the beat, not a ratio of long to short.
 */
public record Groove(double swing) {
    public Groove {
        if (swing <= 0 || swing >= 1) {
            throw new IllegalArgumentException("swing must be between 0 and 1");
        }
    }

    public static Groove parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("groove is empty");
        }
        Double swing = null;
        for (String part : text.split(",")) {
            String piece = part.trim();
            int eq = piece.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("groove " + text);
            }
            String key = piece.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = piece.substring(eq + 1).trim();
            if (!key.equals("swing")) {
                throw new IllegalArgumentException("unknown groove " + key);
            }
            try {
                swing = Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("swing " + value);
            }
        }
        if (swing == null) {
            throw new IllegalArgumentException("groove needs swing=");
        }
        return new Groove(swing);
    }
}
