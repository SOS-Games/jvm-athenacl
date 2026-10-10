// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.theory.Key;

import java.util.ArrayList;
import java.util.List;

/**
 * Grows {@code m1} from a seed. The first note is the tonic, on the downbeat.
 * Later notes step by at most one scale degree. The same seed always grows the same motif.
 */
public final class MotifGenerator {
    private MotifGenerator() {
    }

    public static Motif generate(Key key, long seed) {
        int steps = key == null ? 7 : key.pitchClassNames().size();
        return generate(steps, seed);
    }

    public static Motif generate(int steps, long seed) {
        int size = Math.max(1, steps);
        RandomSource rng = DefaultRandom.of(seed).fork("motif".hashCode(), size);
        int count = 2 + rng.nextInt(7);
        List<MotifNote> notes = new ArrayList<>();
        int degree = 0;
        notes.add(new MotifNote(degree, "1/4"));
        for (int i = 1; i < count; i++) {
            degree = Math.floorMod(degree + rng.nextInt(3) - 1, size);
            notes.add(new MotifNote(degree, "1/4"));
        }
        return new Motif("m1", size, notes);
    }
}
