// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.hook;

import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.motif.MotifNote;
import io.github.sosgames.athenacl.motif.critic.MelodyCritic;
import io.github.sosgames.athenacl.theory.Key;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hook contour varies, and a strong beat uses the chord that is sounding. */
class HookShapeTest {
    @Test
    void theSameSeedRepeatsAndAnotherSeedMoves() {
        Key key = Key.parse("C major");
        HookSettings settings = HookSettings.defaults();
        HookGenerator.Grown first = grow(key, 4, settings);
        HookGenerator.Grown again = grow(key, 4, settings);
        assertEquals(first.motif().notes(), again.motif().notes());
        boolean moved = false;
        for (int seed = 5; seed < 40; seed++) {
            if (!grow(key, seed, settings).motif().notes().equals(first.motif().notes())) {
                moved = true;
                break;
            }
        }
        assertTrue(moved, "every seed grew the same hook");
    }

    @Test
    void oneLeapIsThreeToSixDegrees() {
        Key key = Key.parse("C major");
        HookSettings settings = HookSettings.defaults();
        boolean leaped = false;
        for (int seed = 0; seed < 200; seed++) {
            List<MotifNote> notes = grow(key, seed, settings).motif().notes();
            int big = 0;
            int leapDegrees = 0;
            int previous = -1;
            int previousDegree = 0;
            for (int i = 0; i < notes.size(); i++) {
                int degree = notes.get(i).degree();
                int midi = HookGenerator.midi(key, degree);
                if (previous >= 0 && Math.abs(midi - previous) > 5) {
                    big++;
                    leapDegrees = Math.abs(degree - previousDegree);
                }
                previous = midi;
                previousDegree = degree;
            }
            assertTrue(big <= 1, "seed " + seed + " leaps " + big + " " + notes);
            if (big == 1) {
                assertTrue(leapDegrees >= 3 && leapDegrees <= 6, "seed " + seed + " leap " + leapDegrees);
                leaped = true;
            }
        }
        assertTrue(leaped, "no hook leaped");
    }

    @Test
    void strongBeatsSitOnTheProgression() {
        Key key = Key.parse("C major");
        List<int[]> chords = MelodyCritic.chords(key, "i VI VII i");
        HookSettings settings = HookSettings.defaults();
        int hits = 0;
        int strong = 0;
        for (int seed = 0; seed < 100; seed++) {
            HookGenerator.Grown grown = HookGenerator.grow(key,
                    DefaultRandom.of(seed).fork("hook-chord".hashCode()), settings, chords);
            long tick = 0;
            List<MotifNote> notes = grown.motif().notes();
            for (int i = 0; i < notes.size(); i++) {
                boolean downbeat = tick % TimeBase.PPQ == 0
                        && ((tick / TimeBase.PPQ) % 4 == 0 || (tick / TimeBase.PPQ) % 4 == 2);
                if (downbeat && i < notes.size() - 1) {
                    strong++;
                    int at = Math.min(chords.size() - 1, i * chords.size() / notes.size());
                    int pc = Math.floorMod(HookGenerator.midi(key, notes.get(i).degree()), 12);
                    for (int tone : chords.get(at)) {
                        if (Math.floorMod(tone, 12) == pc) {
                            hits++;
                            break;
                        }
                    }
                }
                tick += TimeBase.durationTicks(notes.get(i).duration());
            }
        }
        assertTrue(hits == strong, hits + "/" + strong);
    }

    @Test
    void aCellCanStartLaterInItsPattern() {
        String[] first = HookGenerator.durations("long-short-short", 4,
                DefaultRandom.of(1).fork("cell".hashCode()));
        assertEquals(4, first.length);
        boolean differed = false;
        for (int seed = 2; seed < 30 && !differed; seed++) {
            String[] rotated = HookGenerator.durations("long-short-short", 4,
                    DefaultRandom.of(seed).fork("cell".hashCode()));
            for (int i = 0; i < first.length; i++) {
                if (!first[i].equals(rotated[i])) {
                    differed = true;
                }
            }
        }
        assertTrue(differed, String.join(",", first));
    }

    private static HookGenerator.Grown grow(Key key, int seed, HookSettings settings) {
        return HookGenerator.grow(key, DefaultRandom.of(seed).fork("hook-shape".hashCode()), settings);
    }
}
