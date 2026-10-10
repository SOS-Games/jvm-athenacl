// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A chord symbol such as {@code Dm7}. Pitch classes are spelled in thirds from the root
 * ({@code D,F,A,C}), using the key's name for the root when one is supplied.
 */
public record Chord(int rootPc, ChordQuality quality) {
    public Chord {
        rootPc = Math.floorMod(rootPc, 12);
    }

    public static Chord parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("missing chord");
        }
        String token = text.trim();
        int letter = "CDEFGAB".indexOf(Character.toUpperCase(token.charAt(0)));
        if (letter < 0) {
            throw new IllegalArgumentException("chord " + text);
        }
        int index = 1;
        int accidental = 0;
        while (index < token.length()) {
            char mark = token.charAt(index);
            if (mark == '#') {
                accidental++;
                index++;
            } else if (mark == 'b' && accidental <= 0 && index < 3) {
                accidental--;
                index++;
            } else {
                break;
            }
        }
        if (Math.abs(accidental) > 2) {
            throw new IllegalArgumentException("chord " + text);
        }
        String suffix = token.substring(index);
        ChordQuality quality = quality(suffix);
        if (quality == null) {
            throw new IllegalArgumentException("chord " + text);
        }
        int pc = Math.floorMod(Spell.NATURAL[letter] + accidental, 12);
        return new Chord(pc, quality);
    }

    public int[] pitchClasses() {
        int[] steps = quality.intervals();
        int[] pcs = new int[steps.length];
        for (int i = 0; i < steps.length; i++) {
            pcs[i] = Math.floorMod(rootPc + steps[i], 12);
        }
        return pcs;
    }

    public String symbol(Key key) {
        return symbol(key.spell(rootPc), quality);
    }

    public String pcs(Key key) {
        return String.join(",", toneNames(key.spell(rootPc)));
    }

    public RomanNumeral roman(Key key) {
        int bestDegree = 1;
        int bestAcc = 0;
        int bestAbs = 99;
        for (int degree = 1; degree <= 7; degree++) {
            int acc = Spell.normalize(rootPc - key.degreePc(degree, 0));
            int abs = Math.abs(acc);
            if (abs < bestAbs) {
                bestAbs = abs;
                bestAcc = acc;
                bestDegree = degree;
            }
        }
        if (bestAbs > 2) {
            throw new IllegalArgumentException("chord does not sit on a scale degree");
        }
        return new RomanNumeral(bestDegree, bestAcc, quality);
    }

    List<String> toneNames(String rootName) {
        int letter = "CDEFGAB".indexOf(rootName.charAt(0));
        int[] offsets = letterOffsets(quality);
        int[] steps = quality.intervals();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < steps.length; i++) {
            int cursor = Math.floorMod(letter + offsets[i], 7);
            int want = Math.floorMod(rootPc + steps[i], 12);
            int acc = Spell.normalize(want - Spell.NATURAL[cursor]);
            names.add(Spell.note(cursor, acc));
        }
        return names;
    }

    private static int[] letterOffsets(ChordQuality quality) {
        return switch (quality) {
            case SUS2 -> new int[] {0, 1, 4};
            case SUS4 -> new int[] {0, 3, 4};
            default -> thirds(quality.intervals().length);
        };
    }

    private static int[] thirds(int count) {
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = 2 * i;
        }
        return offsets;
    }

    static String symbol(String root, ChordQuality quality) {
        return switch (quality) {
            case MAJOR -> root;
            case MINOR -> root + "m";
            case DIMINISHED -> root + "dim";
            case AUGMENTED -> root + "aug";
            case DOMINANT7 -> root + "7";
            case MINOR7 -> root + "m7";
            case MAJOR7 -> root + "maj7";
            case HALF_DIM -> root + "m7b5";
            case DIM7 -> root + "dim7";
            case SUS2 -> root + "sus2";
            case SUS4 -> root + "sus4";
        };
    }

    private static ChordQuality quality(String suffix) {
        String id = suffix.toLowerCase(Locale.ROOT);
        return switch (id) {
            case "" -> ChordQuality.MAJOR;
            case "m", "min", "mi" -> ChordQuality.MINOR;
            case "dim", "o" -> ChordQuality.DIMINISHED;
            case "aug", "+" -> ChordQuality.AUGMENTED;
            case "7" -> ChordQuality.DOMINANT7;
            case "m7", "min7" -> ChordQuality.MINOR7;
            case "maj7", "ma7" -> ChordQuality.MAJOR7;
            case "m7b5" -> ChordQuality.HALF_DIM;
            case "dim7", "o7" -> ChordQuality.DIM7;
            case "sus2" -> ChordQuality.SUS2;
            case "sus4" -> ChordQuality.SUS4;
            default -> suffix.equals("M7") ? ChordQuality.MAJOR7 : null;
        };
    }
}
