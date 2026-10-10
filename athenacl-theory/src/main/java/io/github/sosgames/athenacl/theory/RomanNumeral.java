// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.Locale;

/**
 * A roman numeral measured from the major scale of the key, not from the mode.
 * In D aeolian, {@code bVII} is C major: the flat names the lowered 7th of D major.
 * Upper case is major ({@code bVII}) and lower case is minor ({@code i7}).
 */
public record RomanNumeral(int degree, int accidental, ChordQuality quality) {
    private static final String[] NUMERALS = {"VII", "VI", "IV", "V", "III", "II", "I"};
    private static final int[] DEGREES = {7, 6, 4, 5, 3, 2, 1};

    public RomanNumeral {
        if (degree < 1 || degree > 7) {
            throw new IllegalArgumentException("degree " + degree);
        }
    }

    public static RomanNumeral parse(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing roman numeral");
        }
        String text = token.trim();
        int index = 0;
        int accidental = 0;
        if (index < text.length() && (text.charAt(index) == 'b' || text.charAt(index) == '#')) {
            char kind = text.charAt(index);
            int count = 0;
            while (index < text.length() && text.charAt(index) == kind) {
                count++;
                index++;
            }
            if (count > 2) {
                throw new IllegalArgumentException("not a roman numeral: " + token);
            }
            accidental = kind == 'b' ? -count : count;
        }
        String rest = text.substring(index);
        int matched = -1;
        int degree = 0;
        boolean lower = false;
        for (int i = 0; i < NUMERALS.length; i++) {
            String numeral = NUMERALS[i];
            if (rest.length() >= numeral.length()
                    && rest.substring(0, numeral.length()).equalsIgnoreCase(numeral)) {
                String slice = rest.substring(0, numeral.length());
                boolean upper = slice.equals(slice.toUpperCase(Locale.ROOT));
                lower = slice.equals(slice.toLowerCase(Locale.ROOT));
                if (upper == lower) {
                    throw new IllegalArgumentException("not a roman numeral: " + token);
                }
                matched = numeral.length();
                degree = DEGREES[i];
                break;
            }
        }
        if (matched < 0) {
            throw new IllegalArgumentException("not a roman numeral: " + token);
        }
        ChordQuality parsed = quality(rest.substring(matched), lower, token);
        return new RomanNumeral(degree, accidental, parsed);
    }

    public int[] pitchClasses(Key key) {
        int root = key.degreePc(degree, accidental);
        int[] steps = quality.intervals();
        int[] pcs = new int[steps.length];
        for (int i = 0; i < steps.length; i++) {
            pcs[i] = Math.floorMod(root + steps[i], 12);
        }
        return pcs;
    }

    public String format() {
        String[] upper = {"I", "II", "III", "IV", "V", "VI", "VII"};
        String body = upper[degree - 1];
        if (minorCase(quality)) {
            body = body.toLowerCase(Locale.ROOT);
        }
        String prefix = switch (accidental) {
            case -1 -> "b";
            case -2 -> "bb";
            case 1 -> "#";
            case 2 -> "##";
            default -> "";
        };
        return prefix + body + suffix(quality);
    }

    private static boolean minorCase(ChordQuality quality) {
        return switch (quality) {
            case MINOR, MINOR7, DIMINISHED, HALF_DIM, DIM7 -> true;
            default -> false;
        };
    }

    private static String suffix(ChordQuality quality) {
        return switch (quality) {
            case MAJOR, MINOR -> "";
            case DOMINANT7, MINOR7 -> "7";
            case MAJOR7 -> "maj7";
            case DIMINISHED -> "dim";
            case AUGMENTED -> "aug";
            case HALF_DIM -> "m7b5";
            case DIM7 -> "dim7";
            case SUS2 -> "sus2";
            case SUS4 -> "sus4";
        };
    }

    private static ChordQuality quality(String suffix, boolean lower, String token) {
        if (suffix.equals("M7")) {
            return ChordQuality.MAJOR7;
        }
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "" -> lower ? ChordQuality.MINOR : ChordQuality.MAJOR;
            case "7" -> lower ? ChordQuality.MINOR7 : ChordQuality.DOMINANT7;
            case "maj7", "ma7" -> ChordQuality.MAJOR7;
            case "m7", "min7" -> ChordQuality.MINOR7;
            case "dim", "o" -> ChordQuality.DIMINISHED;
            case "dim7", "o7" -> ChordQuality.DIM7;
            case "aug", "+" -> ChordQuality.AUGMENTED;
            case "m7b5" -> ChordQuality.HALF_DIM;
            case "sus2" -> ChordQuality.SUS2;
            case "sus4" -> ChordQuality.SUS4;
            default -> throw new IllegalArgumentException("not a roman numeral: " + token);
        };
    }
}
