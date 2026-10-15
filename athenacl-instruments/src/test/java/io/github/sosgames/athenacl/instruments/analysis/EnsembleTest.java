// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.adaptive.Composition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnsembleTest {
    @Test
    void theSameSeedPicksTheSameEnsemble() throws Exception {
        Ensemble.Result first = Ensemble.choose(profile(), composition(), "sneaking", 1);
        Ensemble.Result second = Ensemble.choose(profile(), composition(), "sneaking", 1);
        assertEquals(first.text(), second.text());
        assertTrue(first.text().contains("pairs.maxRoughness<="));
        for (Ensemble.Role role : first.roles()) {
            assertEquals("ok", role.verdict());
        }
    }

    @Test
    void aHundredSeedsStayInsideTheTopFew() throws Exception {
        Set<String> leads = new HashSet<>();
        for (int seed = 1; seed <= 100; seed++) {
            Ensemble.Result result = Ensemble.choose(profile(), composition(), "sneaking", seed);
            assertEquals(2, result.roles().size());
            leads.add(result.roles().get(0).instrument());
            assertTrue(result.maxRoughness() <= result.roughLimit());
        }
        assertTrue(leads.size() > 1, leads.toString());
    }

    @Test
    void aPinBeatsTheSearch() throws Exception {
        String yaml = """
                ensemble:
                  select: auto
                  roles:
                    lead: {pin: saw}
                    pad: {fit: ">=0.5"}
                  constraints: {maxPairRoughness: 5, maxMask: {lead: 0.95}, verdict: [ok]}
                instruments: {lead: flute, pad: strings}
                """;
        Ensemble.Result result = Ensemble.choose(profile(), composition(yaml), "combat", 1);
        Ensemble.Role lead = result.roles().get(0);
        assertEquals("saw", lead.instrument());
        assertEquals("pin", lead.from());
    }

    @Test
    void playbackUsesThePickAndKeepsUnlistedLayers() throws Exception {
        Ensemble.Result result = Ensemble.forPlayback(composition(), "sneaking", profile(), 1);
        assertEquals(result.roles().get(0).instrument(), result.playing("lead", "flute"));
        assertEquals("strings", result.playing("chords", "strings"));
        assertEquals(null, Ensemble.forPlayback(composition(), "sneaking", null, 1));
        String fixed = """
                ensemble:
                  select: fixed
                instruments: {lead: flute}
                """;
        assertEquals(null, Ensemble.forPlayback(composition(fixed), "sneaking", profile(), 1));
    }

    @Test
    void aMissingProfileKeepsTheFixedInstruments() throws Exception {
        Ensemble.Result result = Ensemble.fallback(composition(), "sneaking", 1);
        assertTrue(result.fallback());
        assertTrue(result.text().contains("ensemble.fallback=fixed"));
        assertEquals("flute", result.roles().get(0).instrument());
    }

    private static Composition composition() throws Exception {
        return composition("""
                ensemble:
                  select: auto
                  topK: 3
                  roles:
                    lead: {fit: ">=0.5"}
                    pad: {fit: ">=0.5"}
                  constraints: {maxPairRoughness: 5, maxMask: {lead: 0.95}, verdict: [ok]}
                instruments: {lead: flute, pad: strings}
                """);
    }

    private static Composition composition(String yaml) throws Exception {
        return Composition.empty().merge(new ObjectMapper(new YAMLFactory()).readTree(yaml), "test");
    }

    private static InstrumentProfile.Report profile() {
        return new InstrumentProfile.Report("analysis", List.of(
                row("pad", List.of("pad", "warm"), 1.2),
                row("pluck", List.of("plucky", "warm"), 4.0),
                row("saw", List.of("bright"), 3.0)),
                0, true, "abc", Path.of("profile.json"));
    }

    private static InstrumentProfile.Row row(String name, List<String> tags, double centroid) {
        return new InstrumentProfile.Row(name, "ok", List.of(), tags, 0.1, 20, "measured",
                centroid, 0.02, 200, false, 0.1, -20, SoundClass.SUSTAINED, 0, "");
    }
}
