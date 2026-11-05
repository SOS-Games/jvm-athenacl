// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.rhythm.Meter;

/**
 * The arrangement a genre plays when no library state is on.
 * {@code groove:} in the genre file names that bed: which layers stay silent, which
 * hold, and which attack on an {@code x} grid. A named state ignores it, so a theme
 * file such as Ambush keeps the players it already had.
 * The grid divides the bar into one equal step per character. {@code x} is a hit.
 */
public final class GenreBed {
    private GenreBed() {
    }

    /** True for the genre base, and only when that genre wrote a groove. */
    public static boolean applies(Theme.StateDef state) {
        if (state == null || state.id() == null || !state.id().isBlank() || state.composition() == null) {
            return false;
        }
        for (String key : state.composition().leaves().keySet()) {
            if (key.equals("groove") || key.startsWith("groove.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code off} skips the layer, {@code hold} and {@code on} keep the old player,
     * and a string of {@code x} and dots is an attack grid. Empty when this layer
     * has no groove line.
     */
    public static String mode(Theme.StateDef state, String layer) {
        if (state == null || state.composition() == null || layer == null) {
            return "";
        }
        Composition composition = state.composition();
        if (composition.has("groove." + layer)) {
            return word(composition.text("groove." + layer, ""));
        }
        return word(composition.text("groove." + layer + ".pattern", ""));
    }

    /**
     * YAML reads a bare {@code off} as false and a bare {@code on} as true.
     * Both spellings mean the same thing here.
     */
    private static String word(String value) {
        if ("false".equals(value)) {
            return "off";
        }
        if ("true".equals(value)) {
            return "on";
        }
        return value == null ? "" : value;
    }

    /** Note length for a grid hit. Empty when the genre left it off, so the step itself is the length. */
    public static String duration(Theme.StateDef state, String layer) {
        if (state == null || state.composition() == null) {
            return "";
        }
        Composition composition = state.composition();
        String named = composition.text("groove." + layer + "Dur", "");
        if (!named.isBlank()) {
            return named;
        }
        return composition.text("groove." + layer + ".dur", "");
    }

    /** Chord degrees for an arpeggio grid. Empty when the genre did not list any. */
    public static int[] degrees(Theme.StateDef state, String layer) {
        if (state == null || state.composition() == null) {
            return new int[0];
        }
        java.util.List<Integer> listed = state.composition().ints("groove." + layer + "Degrees");
        if (listed.isEmpty()) {
            listed = state.composition().ints("groove." + layer + ".degrees");
        }
        int[] values = new int[listed.size()];
        for (int i = 0; i < listed.size(); i++) {
            values[i] = listed.get(i);
        }
        return values;
    }

    /** Rhythm-cell names for the genre-base lead. Empty when the genre did not list any. */
    public static java.util.List<String> leadCells(Theme.StateDef state) {
        if (!applies(state)) {
            return java.util.List.of();
        }
        return state.composition().list("groove.lead");
    }

    /** A grid is only {@code x}, {@code X}, and dots. A word such as {@code mixed} is not a grid. */
    public static boolean isPattern(String mode) {
        if (mode == null || mode.isBlank()) {
            return false;
        }
        boolean mark = false;
        for (int i = 0; i < mode.length(); i++) {
            char c = mode.charAt(i);
            if (c == 'x' || c == 'X' || c == '.') {
                mark = true;
            } else {
                return false;
            }
        }
        return mark;
    }

    /** Kick, snare, or hat grid. Empty when that lane was not written. */
    public static String lane(Theme.StateDef state, String name) {
        if (state == null || state.composition() == null) {
            return "";
        }
        return state.composition().text("groove." + name, "");
    }

    public static boolean anyLane(Theme.StateDef state) {
        return isPattern(lane(state, "kick")) || isPattern(lane(state, "snare")) || isPattern(lane(state, "hat"));
    }

    /** Length of this state's bar. A blank meter is 4/4. */
    public static long barTicks(Theme.StateDef state) {
        String meter = state == null ? null : state.meter();
        if (meter == null || meter.isBlank()) {
            return TimeBase.barTicks(4, 4);
        }
        Meter parsed = Meter.parse(meter);
        return TimeBase.barTicks(parsed.numerator(), parsed.denominator());
    }
}
