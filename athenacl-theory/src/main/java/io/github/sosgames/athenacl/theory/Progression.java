// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chords of one state, one per bar, repeating.
 * The default phrase is eight bars, {@code i VI iv V i VI V i}, with a half cadence
 * on bar 4 and an authentic cadence on bar 8.
 * A minor-ish scale (its seventh is a whole step below the tonic) turns {@code V}
 * into a dominant seventh when {@code leadingTone} is on.
 * A diminished triad is kept only on {@code ii} and {@code vii}.
 */
public final class Progression {
    private static final List<String> DEFAULT = List.of("i", "VI", "iv", "V", "i", "VI", "V", "i");

    private final Key key;
    private final List<String> numerals;
    private final int rhythm;
    private final boolean leadingTone;
    private final boolean pedal;
    private final Map<Integer, String> cadences;
    private final int phrase;

    private Progression(Key key, List<String> numerals, int rhythm, boolean leadingTone, boolean pedal,
                        Map<Integer, String> cadences, int phrase) {
        this.key = key;
        this.numerals = numerals;
        this.rhythm = rhythm;
        this.leadingTone = leadingTone;
        this.pedal = pedal;
        this.cadences = cadences;
        this.phrase = phrase;
    }

    /**
     * @param numerals roman numerals; empty uses the eight-bar default
     * @param harmonicRhythm bars each numeral lasts before the next one
     * @param cadences bar number in the phrase to a cadence name ({@code half}, {@code authentic}) or a numeral
     */
    public static Progression of(Key key, List<String> numerals, int harmonicRhythm, boolean leadingTone,
                                 boolean pedal, Map<Integer, String> cadences) {
        List<String> tokens = new ArrayList<>();
        if (numerals != null) {
            for (String token : numerals) {
                if (token != null && !token.isBlank()) {
                    tokens.add(token.trim());
                }
            }
        }
        if (tokens.isEmpty()) {
            tokens = DEFAULT;
        }
        Map<Integer, String> marks = new LinkedHashMap<>();
        if (cadences != null) {
            marks.putAll(cadences);
        }
        if (marks.isEmpty()) {
            marks.put(4, "half");
            marks.put(8, "authentic");
        }
        int phrase = 8;
        for (int bar : marks.keySet()) {
            phrase = Math.max(phrase, bar);
        }
        return new Progression(key, List.copyOf(tokens), Math.max(1, harmonicRhythm), leadingTone, pedal,
                Map.copyOf(marks), phrase);
    }

    /** The chord sounding on this 1-based bar. */
    public Chord at(int bar) {
        int position = Math.floorMod(Math.max(bar, 1) - 1, phrase) + 1;
        String cadence = cadences.get(position);
        String token;
        boolean cadenceBar = cadence != null;
        if (cadenceBar) {
            token = cadenceToken(cadence);
        } else {
            int index = Math.floorMod(Math.floorDiv(Math.max(bar, 1) - 1, rhythm), numerals.size());
            token = numerals.get(index);
        }
        RomanNumeral numeral = RomanNumeral.parse(token);
        int[] intervals = numeral.quality().intervals();
        String printed = numeral.format();
        int[] steps = key.mode().steps();
        boolean minorish = steps.length > 6 && steps[6] < 11;
        if (leadingTone && numeral.degree() == 5 && numeral.accidental() == 0 && minorish) {
            intervals = ChordQuality.DOMINANT7.intervals();
            printed = "V7";
        }
        if (numeral.degree() != 2 && numeral.degree() != 7
                && intervals.length >= 3 && intervals[1] == 3 && intervals[2] == 6) {
            intervals = intervals.clone();
            intervals[2] = 7;
        }
        int root = key.degreePc(numeral.degree(), numeral.accidental());
        return new Chord(bar, printed, root, intervals, cadenceBar, pedal);
    }

    private String cadenceToken(String name) {
        String text = name.trim();
        if (text.equalsIgnoreCase("half")) {
            return "V";
        }
        if (text.equalsIgnoreCase("authentic") || text.equalsIgnoreCase("perfect")) {
            return key.mode().steps()[2] >= 4 ? "I" : "i";
        }
        return text;
    }

    /**
     * One bar's chord. {@code midi} treats 0 as the root, 2 as the third, and 4 as the fifth,
     * so a layer that used to stack those scale degrees now stacks the sounding chord.
     */
    public record Chord(int bar, String numeral, int rootPc, int[] intervals, boolean cadence, boolean pedal) {
        public Chord {
            intervals = intervals.clone();
        }

        public int[] intervals() {
            return intervals.clone();
        }

        public int rootMidi(int octave) {
            return 12 * (octave + 1) + rootPc;
        }

        public int midi(int degree, int octave) {
            int member = Math.floorDiv(Math.floorMod(degree, 16), 2);
            int index = Math.floorMod(member, intervals.length);
            int extra = Math.floorDiv(member, intervals.length) + Math.floorDiv(degree, 16);
            return 12 * (octave + 1) + rootPc + intervals[index] + 12 * extra;
        }
    }
}
