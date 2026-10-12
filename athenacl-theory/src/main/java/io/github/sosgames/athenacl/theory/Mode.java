// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.Locale;

/**
 * The seven diatonic modes, plus phrygian dominant for the maqam, flamenco, and klezmer packs.
 * {@code major} means ionian and {@code minor} means aeolian. Phrygian dominant is a 12-tone
 * stand-in, not a claim about traditional practice.
 */
public enum Mode {
    IONIAN("ionian", 0, 2, 4, 5, 7, 9, 11),
    DORIAN("dorian", 0, 2, 3, 5, 7, 9, 10),
    PHRYGIAN("phrygian", 0, 1, 3, 5, 7, 8, 10),
    LYDIAN("lydian", 0, 2, 4, 6, 7, 9, 11),
    MIXOLYDIAN("mixolydian", 0, 2, 4, 5, 7, 9, 10),
    AEOLIAN("aeolian", 0, 2, 3, 5, 7, 8, 10),
    LOCRIAN("locrian", 0, 1, 3, 5, 6, 8, 10),
    PHRYGIAN_DOMINANT("phrygian-dominant", 0, 1, 4, 5, 7, 8, 10);

    private final String id;
    private final int[] steps;

    Mode(String id, int... steps) {
        this.id = id;
        this.steps = steps;
    }

    public String id() {
        return id;
    }

    public int[] steps() {
        return steps.clone();
    }

    public static Mode parse(String name) {
        String id = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "major" -> IONIAN;
            case "minor", "naturalminor" -> AEOLIAN;
            default -> {
                for (Mode mode : values()) {
                    if (mode.id.equals(id)) {
                        yield mode;
                    }
                }
                throw new IllegalArgumentException("unknown mode " + name);
            }
        };
    }
}
