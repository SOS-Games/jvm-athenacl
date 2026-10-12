// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.hook;

import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.motif.Motif;
import io.github.sosgames.athenacl.motif.MotifNote;
import io.github.sosgames.athenacl.theory.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grows one short hook as an ordinary {@link Motif}. The id stays {@code m1} so
 * {@code PhrasePlan} can still turn it into a sentence. The contour name (arch, fall,
 * and the rest) is returned beside the motif, because {@code Motif.contour()} is the
 * L7 up/down/same string and this layer does not change that.
 * A hook is the {@code a} cell. Sentence and period are the forms {@code PhrasePlan}
 * already walks across bars; this class does not replace that planner.
 */
public final class HookGenerator {
    private HookGenerator() {
    }

    public record Grown(Motif motif, String contour, String form, String cell) {
    }

    public static Grown grow(Key key, RandomSource rng, HookSettings settings) {
        return grow(key, rng, settings, List.of());
    }

    /** {@code chords} are the pitch classes of each bar. Strong beats sit on the chord sounding then. */
    public static Grown grow(Key key, RandomSource rng, HookSettings settings, List<int[]> chords) {
        int span = settings.maxNotes() - settings.minNotes();
        int count = settings.minNotes() + (span == 0 ? 0 : rng.nextInt(span + 1));
        String contour = pick(rng, settings.contours());
        String cell = settings.cells().get(rng.nextInt(settings.cells().size()));
        int[] degrees = shape(contour, count, rng);
        String[] durations = durations(cell, degrees.length, rng);
        boolean[] chordTone = new boolean[degrees.length];
        snapStrongBeats(key, degrees, durations, chords, chordTone);
        keepOneLeap(degrees, chordTone);
        List<MotifNote> notes = new ArrayList<>();
        for (int i = 0; i < degrees.length; i++) {
            notes.add(new MotifNote(degrees[i], durations[i]));
        }
        int steps = Math.max(1, key.mode().steps().length);
        String form = "call-response".equals(contour) ? "period" : "sentence";
        return new Grown(new Motif("m1", steps, notes), contour, form, cell);
    }

    private static String pick(RandomSource rng, Map<String, Double> weights) {
        double sum = 0;
        for (double weight : weights.values()) {
            sum += Math.max(0, weight);
        }
        if (sum <= 0) {
            return weights.keySet().iterator().next();
        }
        double roll = rng.nextDouble() * sum;
        double cursor = 0;
        String last = "arch";
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            last = entry.getKey();
            cursor += Math.max(0, entry.getValue());
            if (roll <= cursor) {
                return entry.getKey();
            }
        }
        return last;
    }

    /**
     * One contour with a random interior. Steps stay within two degrees. One leap covers
     * three to six degrees, and the last note is the tonic when that is still a step.
     */
    static int[] shape(String contour, int count, RandomSource rng) {
        int n = Math.max(1, count);
        int[] degrees = new int[n];
        if (n == 1) {
            return degrees;
        }
        int leap = 3 + rng.nextInt(4);
        if ("fall".equals(contour)) {
            degrees[0] = Math.min(6, leap + rng.nextInt(Math.max(1, 7 - leap)));
            int leapAt = n <= 3 ? 1 : 1 + rng.nextInt(n - 2);
            int cursor = degrees[0];
            for (int i = 1; i < n - 1; i++) {
                if (i == leapAt) {
                    cursor = Math.max(0, cursor - leap);
                } else if (cursor > 0) {
                    cursor -= 1;
                }
                degrees[i] = cursor;
            }
            degrees[n - 1] = 0;
            return degrees;
        }
        boolean call = "call-response".equals(contour) && n >= 4;
        degrees[0] = call ? 0 : rng.nextInt(2);
        if (call) {
            degrees[1] = 1 + rng.nextInt(2);
        }
        int leapAt = call ? Math.min(n - 2, 2 + (n > 4 ? rng.nextInt(n - 4) : 0)) : 1;
        if (!call && n > 3) {
            leapAt = 1 + rng.nextInt(n - 2);
        }
        int cursor = degrees[Math.min(leapAt, 1) == 1 && call ? 1 : 0];
        for (int i = 1; i < n - 1; i++) {
            if (call && i == 1) {
                cursor = degrees[1];
                degrees[i] = cursor;
                continue;
            }
            if (i == leapAt) {
                cursor = Math.min(6, cursor + leap);
            } else if (i < leapAt) {
                cursor = Math.min(6, cursor + 1);
            } else if (cursor > 0) {
                cursor -= 1;
            }
            degrees[i] = cursor;
        }
        degrees[n - 1] = 0;
        return degrees;
    }

    /** One skip of three to six degrees. A strong beat that already fits the chord stays put. */
    private static void keepOneLeap(int[] degrees, boolean[] chordTone) {
        if (degrees.length >= 2 && !chordTone[degrees.length - 1]) {
            degrees[degrees.length - 1] = cadence(degrees[degrees.length - 2]);
        }
        boolean leaped = false;
        for (int pass = 0; pass < 3; pass++) {
            leaped = false;
            for (int i = 1; i < degrees.length; i++) {
                int gap = Math.abs(degrees[i] - degrees[i - 1]);
                if (gap < 3) {
                    continue;
                }
                if (!leaped && gap <= 6) {
                    leaped = true;
                    continue;
                }
                if (!chordTone[i]) {
                    degrees[i] = stepToward(degrees[i - 1], degrees[i]);
                } else if (!chordTone[i - 1]) {
                    degrees[i - 1] = stepToward(degrees[i], degrees[i - 1]);
                }
            }
        }
        if (degrees.length >= 2 && !chordTone[degrees.length - 1]) {
            degrees[degrees.length - 1] = cadence(degrees[degrees.length - 2]);
            if (Math.abs(degrees[degrees.length - 1] - degrees[degrees.length - 2]) >= 3
                    && !chordTone[degrees.length - 2]) {
                degrees[degrees.length - 2] = stepToward(degrees[degrees.length - 1], degrees[degrees.length - 2]);
            }
        }
    }

    private static int stepToward(int anchor, int value) {
        if (value == anchor) {
            return anchor;
        }
        return anchor + Integer.signum(value - anchor);
    }

    private static int cadence(int previous) {
        if (Math.abs(previous) <= 2) {
            return 0;
        }
        return Math.abs(previous - 2) <= Math.abs(previous - 4) ? 2 : 4;
    }

    static String[] durations(String cell, int count, RandomSource rng) {
        if (cell == null || cell.isBlank() || "hook".equals(cell)) {
            return hook(count);
        }
        String[] pattern = switch (cell) {
            case "driving-8ths" -> new String[] {"1/8"};
            case "long-short-short" -> new String[] {"1/4", "1/8", "1/8"};
            case "short-short-long" -> new String[] {"1/8", "1/8", "1/4"};
            case "anticipation" -> new String[] {"1/8", "1/4"};
            case "even-quarters" -> new String[] {"1/4"};
            default -> hook(count);
        };
        int rotate = pattern.length <= 1 ? 0 : rng.nextInt(pattern.length);
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            values[i] = pattern[(i + rotate) % pattern.length];
        }
        return values;
    }

    private static String[] hook(int count) {
        if (count <= 3) {
            return new String[] {"1/4", "1/4", "1/2"};
        }
        if (count == 4) {
            return new String[] {"1/8", "1/8", "1/4", "1/2"};
        }
        if (count == 5) {
            return new String[] {"1/8", "1/8", "1/4", "1/4", "1/2"};
        }
        String[] durations = new String[count];
        for (int i = 0; i < count - 1; i++) {
            durations[i] = "1/8";
        }
        durations[count - 1] = "1/2";
        return durations;
    }

    /** Chord tones on beats 1 and 3. With no chord, that is the tonic triad. The last note stays put. */
    private static void snapStrongBeats(Key key, int[] degrees, String[] durations, List<int[]> chords,
                                         boolean[] chordTone) {
        long tick = 0;
        for (int i = 0; i < degrees.length && i < durations.length; i++) {
            boolean strong = tick % TimeBase.PPQ == 0
                    && ((tick / TimeBase.PPQ) % 4 == 0 || (tick / TimeBase.PPQ) % 4 == 2);
            if (strong && i < degrees.length - 1) {
                int[] pcs = chordAt(key, chords, i, degrees.length);
                degrees[i] = nearestChordDegree(key, degrees[i], pcs);
                chordTone[i] = true;
            }
            tick += TimeBase.durationTicks(durations[i]);
        }
    }

    private static int[] chordAt(Key key, List<int[]> chords, int index, int count) {
        if (chords == null || chords.isEmpty()) {
            return triadPcs(key);
        }
        int at = chords.size() == 1 ? 0 : Math.min(chords.size() - 1, index * chords.size() / Math.max(1, count));
        int[] pcs = chords.get(at);
        return pcs == null || pcs.length == 0 ? triadPcs(key) : pcs;
    }

    private static int[] triadPcs(Key key) {
        int[] steps = key.mode().steps();
        int tonic = key.tonicPc();
        return new int[] {
                tonic,
                Math.floorMod(tonic + steps[Math.min(2, steps.length - 1)], 12),
                Math.floorMod(tonic + steps[Math.min(4, steps.length - 1)], 12)
        };
    }

    private static int nearestChordDegree(Key key, int degree, int[] pcs) {
        if (matches(key, degree, pcs)) {
            return degree;
        }
        for (int distance = 1; distance <= 7; distance++) {
            if (matches(key, degree - distance, pcs)) {
                return degree - distance;
            }
            if (matches(key, degree + distance, pcs)) {
                return degree + distance;
            }
        }
        return degree;
    }

    private static boolean matches(Key key, int degree, int[] pcs) {
        int pc = Math.floorMod(key.degreeMidi(degree, 5), 12);
        for (int tone : pcs) {
            if (Math.floorMod(tone, 12) == pc) {
                return true;
            }
        }
        return false;
    }

    /** MIDI pitch for a scale degree in octave 5, where C4 is 60. */
    public static int midi(Key key, int degree) {
        return key.degreeMidi(degree, 5);
    }
}
