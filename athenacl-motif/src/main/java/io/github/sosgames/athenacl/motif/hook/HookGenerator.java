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
        int span = settings.maxNotes() - settings.minNotes();
        int count = settings.minNotes() + (span == 0 ? 0 : rng.nextInt(span + 1));
        String contour = pick(rng, settings.contours());
        int variant = rng.nextInt(2);
        String cell = settings.cells().get(rng.nextInt(settings.cells().size()));
        int[] degrees = shape(contour, count, variant);
        String[] durations = durations(cell, degrees.length);
        snapStrongBeats(key, degrees, durations);
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

    /** Scale degrees. The last note of a 4–6 note hook is the tonic. One interior leap, then steps. */
    static int[] shape(String contour, int count, int variant) {
        int[] degrees = new int[count];
        if ("fall".equals(contour)) {
            degrees[0] = 4;
            if (count == 1) {
                return degrees;
            }
            degrees[1] = 0;
            for (int i = 2; i < count - 1; i++) {
                degrees[i] = Math.min(2, i - 1);
            }
            if (count > 2) {
                degrees[count - 1] = count == 3 ? 2 : 0;
            }
            return degrees;
        }
        if ("call-response".equals(contour) && count >= 4) {
            degrees[0] = 0;
            degrees[1] = 2;
            degrees[2] = 4;
            for (int i = 3; i < count - 1; i++) {
                degrees[i] = Math.max(1, 4 - (i - 2));
            }
            degrees[count - 1] = 0;
            return degrees;
        }
        degrees[0] = 0;
        if (count == 1) {
            return degrees;
        }
        if (count == 2) {
            degrees[1] = 0;
            return degrees;
        }
        if (count == 3) {
            degrees[1] = variant == 0 ? 4 : 2;
            degrees[2] = variant == 0 ? 2 : 0;
            return degrees;
        }
        int leap = variant == 0 || count < 5 ? 4 : 5;
        degrees[1] = leap;
        int cursor = leap;
        for (int i = 2; i < count - 1; i++) {
            cursor = Math.max(1, cursor - 1);
            degrees[i] = cursor;
        }
        degrees[count - 1] = 0;
        return degrees;
    }

    static String[] durations(String cell, int count) {
        return switch (cell == null ? "" : cell) {
            case "driving-8ths" -> fill(count, "1/8");
            case "long-short-short" -> cycle(count, "1/4", "1/8", "1/8");
            case "short-short-long" -> cycle(count, "1/8", "1/8", "1/4");
            case "anticipation" -> cycle(count, "1/8", "1/4");
            case "even-quarters" -> fill(count, "1/4");
            default -> hook(count);
        };
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

    private static String[] fill(int count, String duration) {
        String[] durations = new String[count];
        for (int i = 0; i < count; i++) {
            durations[i] = duration;
        }
        return durations;
    }

    private static String[] cycle(int count, String... pattern) {
        String[] durations = new String[count];
        for (int i = 0; i < count; i++) {
            durations[i] = pattern[i % pattern.length];
        }
        return durations;
    }

    /** Chord tones on beats 1 and 3. The cadence note is left on the degree the shape chose. */
    private static void snapStrongBeats(Key key, int[] degrees, String[] durations) {
        long tick = 0;
        for (int i = 0; i < degrees.length && i < durations.length; i++) {
            boolean strong = tick % TimeBase.PPQ == 0
                    && ((tick / TimeBase.PPQ) % 4 == 0 || (tick / TimeBase.PPQ) % 4 == 2);
            if (strong && i < degrees.length - 1 && !triad(degrees[i])) {
                degrees[i] = nearestTriad(degrees[i]);
            }
            tick += TimeBase.durationTicks(durations[i]);
        }
        if (degrees.length > 0) {
            int last = degrees[degrees.length - 1];
            if (last < 0 || last > 6) {
                degrees[degrees.length - 1] = 0;
            }
        }
        // Touch the key so a caller passing a real key still type-checks the scale size.
        if (key.pitchClassNames().isEmpty() && degrees.length > 0) {
            degrees[0] = 0;
        }
    }

    private static boolean triad(int degree) {
        int wrapped = Math.floorMod(degree, 7);
        return wrapped == 0 || wrapped == 2 || wrapped == 4;
    }

    private static int nearestTriad(int degree) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int candidate : new int[] {0, 2, 4}) {
            int distance = Math.abs(degree - candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** MIDI pitch for a scale degree in octave 5, where C4 is 60. */
    public static int midi(Key key, int degree) {
        return key.degreeMidi(degree, 5);
    }
}
