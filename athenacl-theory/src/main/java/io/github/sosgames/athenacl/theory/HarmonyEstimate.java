// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chord and key guesses for {@code describe-music}. The key is one guess for the bars
 * you asked about: cosine similarity against a mode profile. The lowest note of the
 * first of those bars counts as four extra notes, because a short loop usually states its tonic there.
 * A bar's chord is printed only when every chord tone is actually sounding.
 */
public final class HarmonyEstimate {
    private HarmonyEstimate() {
    }

    public record Reading(String key, String confidence, Map<Integer, String> chords) {
        public String chord(int bar) {
            return chords.get(bar);
        }

        static Reading none() {
            return new Reading(null, null, Map.of());
        }
    }

    public static Reading read(Score score) {
        return read(score, 1, score.bars());
    }

    public static Reading read(Score score, int fromBar, int toBar) {
        long[] hist = new long[12];
        NoteEvent openingBass = null;
        boolean any = false;
        for (NoteEvent note : score.notes()) {
            if (note.channel() == 9 || note.bar() < fromBar || note.bar() > toBar) {
                continue;
            }
            any = true;
            hist[Math.floorMod(note.pitch(), 12)] += note.durationTicks();
            if (note.bar() == fromBar && (openingBass == null || note.pitch() < openingBass.pitch())) {
                openingBass = note;
            }
        }
        if (!any) {
            return Reading.none();
        }
        if (openingBass != null) {
            hist[Math.floorMod(openingBass.pitch(), 12)] += openingBass.durationTicks() * 4L;
        }
        Key best = null;
        double bestScore = -1;
        for (int tonic = 0; tonic < 12; tonic++) {
            for (Mode mode : Mode.values()) {
                double fit = cosine(hist, template(tonic, mode));
                if (fit > bestScore) {
                    bestScore = fit;
                    best = Key.infer(tonic, mode);
                }
            }
        }
        if (best == null || bestScore < 0) {
            return Reading.none();
        }
        Map<Integer, String> chords = new LinkedHashMap<>();
        for (int bar = fromBar; bar <= toBar; bar++) {
            String symbol = chord(notesIn(score, bar), best);
            if (symbol != null) {
                chords.put(bar, symbol);
            }
        }
        String confidence = String.format(Locale.ROOT, "%.2f", bestScore);
        return new Reading(best.name(), confidence, chords);
    }

    static String chord(List<NoteEvent> notes, Key key) {
        boolean[] on = new boolean[12];
        int sounding = 0;
        for (NoteEvent note : notes) {
            if (note.channel() == 9) {
                continue;
            }
            int pc = Math.floorMod(note.pitch(), 12);
            if (!on[pc]) {
                sounding++;
                on[pc] = true;
            }
        }
        if (sounding == 0) {
            return null;
        }
        ChordQuality bestQuality = null;
        int bestRoot = 0;
        int bestExtras = Integer.MAX_VALUE;
        int bestSize = Integer.MAX_VALUE;
        for (int root = 0; root < 12; root++) {
            for (ChordQuality quality : ChordQuality.values()) {
                int[] steps = quality.intervals();
                boolean[] used = new boolean[12];
                boolean missing = false;
                for (int step : steps) {
                    int pc = Math.floorMod(root + step, 12);
                    if (!on[pc]) {
                        missing = true;
                        break;
                    }
                    used[pc] = true;
                }
                if (missing) {
                    continue;
                }
                int extras = 0;
                for (int pc = 0; pc < 12; pc++) {
                    if (on[pc] && !used[pc]) {
                        extras++;
                    }
                }
                boolean better = extras < bestExtras
                        || (extras == bestExtras && steps.length < bestSize)
                        || (extras == bestExtras && steps.length == bestSize && root == key.tonicPc()
                        && bestRoot != key.tonicPc());
                if (bestQuality == null || better) {
                    bestQuality = quality;
                    bestRoot = root;
                    bestExtras = extras;
                    bestSize = steps.length;
                }
            }
        }
        if (bestQuality == null) {
            return null;
        }
        return Chord.symbol(key.spell(bestRoot), bestQuality);
    }

    private static List<NoteEvent> notesIn(Score score, int bar) {
        return score.notes().stream().filter(note -> note.bar() == bar).toList();
    }

    private static double[] template(int tonic, Mode mode) {
        double[] weights = new double[12];
        int[] steps = mode.steps();
        for (int i = 0; i < steps.length; i++) {
            int pc = Math.floorMod(tonic + steps[i], 12);
            double weight = switch (i) {
                case 0 -> 6.0;
                case 2 -> 3.5;
                case 4 -> 4.5;
                default -> 2.0;
            };
            weights[pc] = weight;
        }
        return weights;
    }

    private static double cosine(long[] hist, double[] weights) {
        double dot = 0;
        double left = 0;
        double right = 0;
        for (int i = 0; i < 12; i++) {
            dot += hist[i] * weights[i];
            left += (double) hist[i] * hist[i];
            right += weights[i] * weights[i];
        }
        if (left == 0 || right == 0) {
            return 0;
        }
        return dot / Math.sqrt(left * right);
    }
}
