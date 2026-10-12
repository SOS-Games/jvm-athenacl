// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.critic;

import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.motif.hook.HookGenerator;
import io.github.sosgames.athenacl.motif.hook.HookSettings;
import io.github.sosgames.athenacl.motif.hook.MelodyPitches;
import io.github.sosgames.athenacl.theory.Key;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public-domain phrases transcribed here, not a downloaded corpus.
 * Amazing Grace (New Britain), Twinkle (Ah! vous dirai-je, maman), Ode to Joy,
 * London Bridge, Mary Had a Little Lamb, and When the Saints Go Marching In.
 */
class MelodyTest {
    @Test
    void hooksStayShortAndMostlyStepwise() {
        Key key = Key.parse("C major");
        HookSettings settings = HookSettings.defaults();
        int chordTones = 0;
        int strong = 0;
        for (int seed = 0; seed < 1000; seed++) {
            HookGenerator.Grown grown = HookGenerator.grow(key,
                    DefaultRandom.of(seed).fork("hook-property".hashCode()), settings);
            int count = grown.motif().notes().size();
            assertTrue(count >= 3 && count <= 6, "notes=" + count);
            int leaps = 0;
            int previous = -1;
            long tick = 0;
            for (int i = 0; i < count; i++) {
                int degree = grown.motif().notes().get(i).degree();
                int midi = HookGenerator.midi(key, degree);
                if (previous >= 0 && Math.abs(midi - previous) > 5) {
                    leaps++;
                }
                previous = midi;
                boolean downbeat = tick % TimeBase.PPQ == 0
                        && ((tick / TimeBase.PPQ) % 4 == 0 || (tick / TimeBase.PPQ) % 4 == 2);
                if (downbeat) {
                    strong++;
                    if (degree == 0 || degree == 2 || degree == 4) {
                        chordTones++;
                    }
                }
                int last = grown.motif().notes().get(count - 1).degree();
                assertTrue(last == 0 || last == 2 || last == 4);
                tick += TimeBase.durationTicks(grown.motif().notes().get(i).duration());
            }
            assertTrue(leaps <= 1, "seed " + seed + " leaps " + leaps);
        }
        assertTrue(chordTones / (double) strong >= 0.80, chordTones + "/" + strong);
    }

    @Test
    void sameSeedPicksTheSameHook() {
        Key key = Key.parse("E minor");
        MelodyCritic.Ranked first = MelodyCritic.rank(key, 3, "melody", HookSettings.defaults(),
                MelodyCritic.chords(key, "i VI VII i"), List.of());
        MelodyCritic.Ranked second = MelodyCritic.rank(key, 3, "melody", HookSettings.defaults(),
                MelodyCritic.chords(key, "i VI VII i"), List.of());
        assertEquals(first.chosen().cand(), second.chosen().cand());
        assertEquals(first.chosen().score(), second.chosen().score());
        assertEquals(first.chosen().motif().notes(), second.chosen().motif().notes());
        assertTrue(first.chosen().features().get("closure") >= 0.8);
        assertNull(first.chosen().rejected());
    }

    @Test
    void sixtyFourCandidatesStayInsideTheBudget() {
        Key key = Key.parse("E minor");
        MelodyCritic.rank(key, 1, "warm", HookSettings.defaults(), List.of(), List.of());
        long started = System.nanoTime();
        MelodyCritic.rank(key, 2, "budget", HookSettings.defaults(), List.of(), List.of());
        long nanos = System.nanoTime() - started;
        assertTrue(nanos < 10_000_000L, Long.toString(nanos));
    }

    @Test
    void hardRulesRejectTheNamedFailures() {
        Key key = Key.parse("C major");
        assertEquals("maxLeap", score(key, 60, 64, 84, 60).rejected());
        assertEquals("maxSamePitchRun", score(key, 60, 60, 60, 60, 60).rejected());
        assertEquals("noUnresolvedTritoneLeap", score(key, 60, 66, 72).rejected());
        assertEquals("cadenceStable", score(key, 60, 62, 64, 65).rejected());
        int[] twinkle = {60, 60, 67, 67, 69, 69, 67};
        MelodyCritic.Scored copied = MelodyCritic.scorePitches(twinkle, quarters(twinkle.length), key,
                HookSettings.defaults(), List.of(), List.of(twinkle));
        assertEquals("copyGuard", copied.rejected());
    }

    @Test
    void folkPhrasesOutrankWalksAndShuffles() {
        List<Double> folk = new ArrayList<>();
        folk.add(folkScore("G major", 67, 67, 74, 71, 67, 71, 69, 67));
        folk.add(folkScore("C major", 60, 60, 67, 67, 69, 69, 67));
        folk.add(folkScore("C major", 64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 60));
        folk.add(folkScore("C major", 64, 65, 64, 62, 60, 62, 64, 60));
        folk.add(folkScore("C major", 64, 62, 60, 62, 64, 64, 64));
        folk.add(folkScore("C major", 67, 69, 71, 67, 69, 71, 72, 71, 69, 67));
        List<Double> walks = new ArrayList<>();
        walks.add(folkScore("C major", 60, 64, 84, 66, 59, 62, 69, 60));
        walks.add(folkScore("C major", 48, 72, 50, 80, 55, 77, 60));
        walks.add(folkScore("C major", 60, 61, 73, 58, 82, 63, 70, 60));
        walks.add(folkScore("G major", 55, 67, 80, 58, 79, 62, 67));
        assertTrue(auc(folk, walks) >= 0.85, "walk " + auc(folk, walks));
        List<Double> shuffled = new ArrayList<>();
        int[][] phrases = {
                {67, 67, 74, 71, 67, 71, 69, 67},
                {60, 60, 67, 67, 69, 69, 67},
                {64, 64, 65, 67, 67, 65, 64, 62, 60},
                {64, 65, 64, 62, 60, 62, 64, 60},
                {64, 62, 60, 62, 64, 64, 64},
                {67, 69, 71, 67, 69, 71, 72, 71, 69, 67}
        };
        String[] keys = {"G major", "C major", "C major", "C major", "C major", "C major"};
        for (int i = 0; i < phrases.length; i++) {
            for (int shuffle = 0; shuffle < 4; shuffle++) {
                int[] mixed = phrases[i].clone();
                var rng = DefaultRandom.of(9).fork(i, shuffle);
                for (int n = mixed.length - 1; n > 0; n--) {
                    int swap = rng.nextInt(n + 1);
                    int tmp = mixed[n];
                    mixed[n] = mixed[swap];
                    mixed[swap] = tmp;
                }
                shuffled.add(folkScore(keys[i], mixed));
            }
        }
        assertTrue(auc(folk, shuffled) >= 0.70, "shuffle " + auc(folk, shuffled) + " " + folk + " " + shuffled);
    }

    @Test
    void phraseModeRanksFolkAboveTheOldLeadAndTheWalk() throws Exception {
        MelodyCritic.Scored folk = phrase(Path.of("samples/melodies/folk-pd-01.mid"), "G major");
        MelodyCritic.Scored oldLead = oldScaleLead();
        MelodyCritic.Scored walk = phrase(Path.of("samples/melodies/random-walk.mid"), "C major");
        double folkScore = value(folk);
        double leadScore = value(oldLead);
        double walkScore = value(walk);
        assertTrue(folkScore > leadScore, "folk " + folkScore + " " + folk.rejected()
                + " lead " + leadScore + " " + oldLead.rejected());
        assertTrue(leadScore > walkScore, "lead " + leadScore + " " + oldLead.rejected()
                + " walk " + walkScore + " " + walk.rejected());
    }

    @Test
    void phraseModeUsesTheBarChordAndWaitsForThePhraseEnd() {
        Key key = Key.parse("C major");
        int[] midi = {60, 71, 62};
        String[] durations = {"1/1", "1/4", "1/4"};
        List<int[]> chords = MelodyCritic.chords(key, "I V");
        MelodyCritic.Scored phrase = MelodyCritic.scorePitches(midi, durations, key, HookSettings.defaults(),
                chords, List.of(), MelodyCritic.Mode.PHRASE);
        assertNull(phrase.rejected());
        assertEquals(1.0, phrase.features().get("chordTone"), 1e-9);
        MelodyCritic.Scored hook = MelodyCritic.scorePitches(midi, durations, key, HookSettings.defaults(),
                chords, List.of(), MelodyCritic.Mode.HOOK);
        assertEquals("cadenceStable", hook.rejected());
    }

    @Test
    void fitRecoversClosureAsThePlantedWeight() {
        double[] defaults = MelodyCritic.defaultWeights();
        WeightFit.Model model = WeightFit.fit(new WeightFit.ListView() {
            @Override
            public int n() {
                return 16;
            }

            @Override
            public double diff(int row, int feature) {
                return feature == 8 ? 1 : 0;
            }
        }, defaults);
        assertEquals("closure", model.order()[0]);
        assertEquals(1.0, model.accuracy(), 1e-9);
        assertEquals(model.hash(), WeightFit.fit(new WeightFit.ListView() {
            @Override
            public int n() {
                return 16;
            }

            @Override
            public double diff(int row, int feature) {
                return feature == 8 ? 1 : 0;
            }
        }, defaults).hash());
    }

    private static MelodyCritic.Scored score(Key key, int... midi) {
        return MelodyCritic.scorePitches(midi, quarters(midi.length), key, HookSettings.defaults(), List.of(), List.of());
    }

    private static double value(MelodyCritic.Scored scored) {
        return scored.rejected() == null ? scored.score() : 0;
    }

    private static MelodyCritic.Scored phrase(Path path, String key) throws Exception {
        List<MelodyPitches.Note> notes = MelodyPitches.read(path);
        return MelodyCritic.scorePitches(MelodyPitches.midis(notes), MelodyPitches.durations(notes),
                Key.parse(key), HookSettings.defaults(), List.of(), List.of(), MelodyCritic.Mode.PHRASE);
    }

    /** The lead before the phrase engine: scale steps of a quarter, four to a bar. */
    private static MelodyCritic.Scored oldScaleLead() {
        Key key = Key.parse("C major");
        int steps = key.pitchClassNames().size();
        int degree = -1;
        int[] midi = new int[32];
        String[] durations = new String[32];
        for (int i = 0; i < midi.length; i++) {
            RandomSource rng = DefaultRandom.of(1).fork("peaceful".hashCode(), "lead".hashCode(), i / 4 + 1, i % 4);
            if (degree < 0) {
                degree = rng.nextInt(steps);
            } else {
                degree = Math.floorMod(degree + rng.nextInt(3) - 1, steps);
            }
            midi[i] = key.degreeMidi(degree, 5);
            durations[i] = "1/4";
        }
        return MelodyCritic.scorePitches(midi, durations, key, HookSettings.defaults(),
                List.of(), List.of(), MelodyCritic.Mode.PHRASE);
    }

    private static double folkScore(String key, int... midi) {
        MelodyCritic.Scored scored = score(Key.parse(key), midi);
        return scored.rejected() == null ? scored.score() : 0;
    }

    private static String[] quarters(int count) {
        String[] durations = new String[count];
        for (int i = 0; i < count; i++) {
            durations[i] = "1/4";
        }
        return durations;
    }

    private static double auc(List<Double> positive, List<Double> negative) {
        double wins = 0;
        for (double good : positive) {
            for (double bad : negative) {
                if (good > bad) {
                    wins += 1;
                } else if (good == bad) {
                    wins += 0.5;
                }
            }
        }
        return wins / (positive.size() * (double) negative.size());
    }
}
