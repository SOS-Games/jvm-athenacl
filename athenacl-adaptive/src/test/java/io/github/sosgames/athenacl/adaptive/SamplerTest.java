// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerTest {
    @Test
    void theSameSeedDrawsTheSameFantasy() throws IOException {
        String once = sample("fantasy", 1).lines(false);
        String twice = sample("fantasy", 1).lines(false);
        assertEquals(once, twice);
        assertTrue(once.contains("source=sampled"));
        assertTrue(once.contains("seed=1/s1/"));
        assertTrue(once.contains("source=generated:markov(minor-functional)"));
    }

    @Test
    void fiftySeedsSpreadTheKeyAndTheProgression() throws IOException {
        Set<String> tonics = new HashSet<>();
        Set<String> progressions = new HashSet<>();
        for (int seed = 1; seed <= 50; seed++) {
            Composition drawn = sample("fantasy", seed);
            tonics.add(drawn.text("scale.tonic", ""));
            List<String> chords = drawn.list("harmony.progression");
            assertEquals(8, chords.size(), "seed " + seed);
            assertEquals("i", chords.get(0));
            assertEquals("i", chords.get(7));
            progressions.add(String.join(",", chords));
        }
        assertTrue(tonics.size() >= 3, tonics.toString());
        assertTrue(progressions.size() >= 5, progressions.toString());
    }

    @Test
    void aPinReplacesTheDraw() throws IOException {
        Composition merged = Resolver.resolveTheme(null, null, Path.of("samples/themes/pinned.yaml"));
        Composition drawn = Sampler.sample(merged, 7, Sampler.SECTION);
        assertEquals("D", drawn.text("scale.tonic", ""));
        assertTrue(drawn.lines(false).contains("scale.tonic=D source=pin:"));
    }

    @Test
    void sneakingKeepsItsTempoAndARelativeRangeScales() throws IOException {
        Composition sneaking = Sampler.sample(Resolver.resolve("fantasy", "sneaking", "", null), 1, Sampler.SECTION);
        assertEquals(76, sneaking.number("tempo.bpm", 0));
        assertEquals("dorian", sneaking.text("scale.mode", ""));
        Composition reggae = Sampler.sample(Resolver.resolve("reggae", "sneaking", "", null), 1, Sampler.SECTION);
        assertTrue(reggae.lines(false).contains("relative=0.8"));
        assertTrue(reggae.lines(false).contains("source=sampled:"));
    }

    @Test
    void grammarSieveAndPoAreGenerated() throws IOException {
        Composition ambient = sample("ambient", 3);
        assertTrue(ambient.lines(false).contains("source=generated:grammar"));
        Composition fantasy = sample("fantasy", 3);
        assertTrue(fantasy.lines(false).contains("source=generated:sieve("));
        assertTrue(fantasy.lines(false).contains("source=generated:po(basketGen)"));
        assertTrue(fantasy.lines(true).contains("distribution="));
    }

    @Test
    void fantasyRendersKeepTheLeadChecks() throws ThemeException {
        for (int seed = 1; seed <= 20; seed++) {
            Director.Performance played = Presets.play("fantasy", 16, seed);
            PhraseAnalysis.Report report = PhraseAnalysis.of(played.score());
            assertTrue(report.passes(), "seed " + seed + " " + report.line());
        }
    }

    private static Composition sample(String genre, int seed) throws IOException {
        return Sampler.sample(Resolver.genre(genre), seed, Sampler.SECTION);
    }
}
