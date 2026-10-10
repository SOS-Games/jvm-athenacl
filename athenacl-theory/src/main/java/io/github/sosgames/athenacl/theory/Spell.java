// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

/** Letter names and accidentals. C is letter 0. */
final class Spell {
    static final int[] NATURAL = {0, 2, 4, 5, 7, 9, 11};
    private static final String[] SHARP = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static final String[] FLAT = {
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    };

    private Spell() {
    }

    static String note(int letter, int accidental) {
        String name = "CDEFGAB".substring(letter, letter + 1);
        return switch (accidental) {
            case 0 -> name;
            case 1 -> name + "#";
            case 2 -> name + "##";
            case -1 -> name + "b";
            case -2 -> name + "bb";
            default -> name + (accidental > 0 ? "#".repeat(accidental) : "b".repeat(-accidental));
        };
    }

    static int accidental(String name) {
        int value = 0;
        for (int i = 1; i < name.length(); i++) {
            char mark = name.charAt(i);
            if (mark == '#') {
                value++;
            } else if (mark == 'b') {
                value--;
            }
        }
        return value;
    }

    static String chromatic(int pc, boolean flats) {
        return (flats ? FLAT : SHARP)[Math.floorMod(pc, 12)];
    }

    static int normalize(int semitones) {
        int value = semitones;
        while (value > 6) {
            value -= 12;
        }
        while (value < -6) {
            value += 12;
        }
        return value;
    }
}
