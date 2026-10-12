// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a key change. {@code pivot} names a chord that sits in both keys and spends two bars.
 * {@code direct} lands on the new tonic on the next bar. {@code mixture} borrows one chord
 * from the parallel major or minor. A state's {@code keyPolicy} is applied when that state
 * is entered: {@code themeKey} uses the state's key, {@code keep} stays put, {@code parallel}
 * flips major and minor on the same tonic, and {@code relative} moves to the relative major
 * or minor (or the major key that shares a mode's notes).
 */
public final class Modulation {
    private Modulation() {
    }

    public record Plan(String method, Key from, Key to, String pivot, String mixture, int bars) {
    }

    public static Plan plan(Key from, Key to, String method) {
        return switch (method) {
            case "pivot" -> new Plan("pivot", from, to, symbol(pivot(from, to), to), "", 2);
            case "direct" -> new Plan("direct", from, to, "", "", 1);
            case "mixture" -> new Plan("mixture", from, to, "", symbol(borrowed(from), parallel(from)), 1);
            default -> throw new IllegalArgumentException("method " + method);
        };
    }

    /** The key heard after this policy. {@code previous} is null on the first bar. */
    public static Key apply(String policy, Key previous, Key declared) {
        Key base = previous == null ? declared : previous;
        return switch (policy == null || policy.isBlank() ? "themeKey" : policy) {
            case "keep" -> base;
            case "parallel" -> parallel(base);
            case "relative" -> relative(base);
            case "themeKey" -> declared;
            default -> throw new IllegalArgumentException("key policy " + policy);
        };
    }

    /** Same tonic. Major becomes minor, and every other mode becomes major. */
    public static Key parallel(Key key) {
        Mode mode = key.mode() == Mode.IONIAN ? Mode.AEOLIAN : Mode.IONIAN;
        return Key.infer(key.tonicPc(), mode);
    }

    /**
     * Major goes to its relative minor, minor to its relative major.
     * Any other mode goes to the major key that uses the same notes.
     */
    public static Key relative(Key key) {
        if (key.mode() == Mode.IONIAN) {
            return Key.infer(Math.floorMod(key.tonicPc() - 3, 12), Mode.AEOLIAN);
        }
        if (key.mode() == Mode.AEOLIAN) {
            return Key.infer(Math.floorMod(key.tonicPc() + 3, 12), Mode.IONIAN);
        }
        return Key.infer(Math.floorMod(key.tonicPc() - offset(key.mode()), 12), Mode.IONIAN);
    }

    private static Triad pivot(Key from, Key to) {
        List<Triad> source = triads(from);
        List<Triad> destination = triads(to);
        for (Triad triad : destination) {
            if (triad.root == to.tonicPc() && source.contains(triad)) {
                return triad;
            }
        }
        for (Triad triad : source) {
            if (destination.contains(triad)) {
                return triad;
            }
        }
        return destination.get(0);
    }

    /** First triad of the parallel key that the current key does not already have. */
    private static Triad borrowed(Key key) {
        List<Triad> have = triads(key);
        List<Triad> parallel = triads(parallel(key));
        for (int degree : new int[] {0, 5, 2, 3, 4, 6, 1}) {
            Triad triad = parallel.get(degree);
            if (!have.contains(triad)) {
                return triad;
            }
        }
        return parallel.get(0);
    }

    private static String symbol(Triad triad, Key key) {
        return new Chord(triad.root, triad.quality).symbol(key);
    }

    private static List<Triad> triads(Key key) {
        int[] steps = key.mode().steps();
        List<Triad> found = new ArrayList<>();
        for (int degree = 0; degree < steps.length; degree++) {
            int root = Math.floorMod(key.tonicPc() + steps[degree], 12);
            int third = Math.floorMod(steps[(degree + 2) % steps.length] - steps[degree], 12);
            int fifth = Math.floorMod(steps[(degree + 4) % steps.length] - steps[degree], 12);
            found.add(new Triad(root, quality(third, fifth)));
        }
        return found;
    }

    private static ChordQuality quality(int third, int fifth) {
        if (third == 3 && fifth == 6) {
            return ChordQuality.DIMINISHED;
        }
        if (third == 4 && fifth == 8) {
            return ChordQuality.AUGMENTED;
        }
        if (third == 3) {
            return ChordQuality.MINOR;
        }
        return ChordQuality.MAJOR;
    }

    /** Semitones from the major tonic up to this mode's tonic, for the same notes. */
    private static int offset(Mode mode) {
        return switch (mode) {
            case IONIAN -> 0;
            case DORIAN -> 2;
            case PHRYGIAN, PHRYGIAN_DOMINANT -> 4;
            case LYDIAN -> 5;
            case MIXOLYDIAN -> 7;
            case AEOLIAN -> 9;
            case LOCRIAN -> 11;
        };
    }

    private record Triad(int root, ChordQuality quality) {
    }
}
