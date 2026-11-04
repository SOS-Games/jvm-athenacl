// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnsembleTest {
    @Test
    void theSameSeedPicksTheSameEnsemble() throws Exception {
        Ensemble.Result first = Ensemble.select("fantasy", "sneaking", null, null, 1);
        Ensemble.Result second = Ensemble.select("fantasy", "sneaking", null, null, 1);
        assertEquals(first.text(), second.text());
        assertFalse(first.text().contains("pairs.maxRoughness"));
        assertTrue(first.text().contains("role=lead"));
        assertTrue(first.text().contains("role=bass"));
    }

    @Test
    void twentySeedsUseAtLeastThreeLeads() throws Exception {
        Set<String> leads = new LinkedHashSet<>();
        Set<String> scifi = new LinkedHashSet<>();
        Set<String> lofi = new LinkedHashSet<>();
        for (int seed = 1; seed <= 20; seed++) {
            Ensemble.Result fantasy = Ensemble.select("fantasy", "sneaking", null, null, seed);
            Ensemble.Result space = Ensemble.select("scifi-space", "peaceful", null, null, seed);
            Ensemble.Result calm = Ensemble.select("lofi", "peaceful", null, null, seed);
            assertFalse(fantasy.fallback());
            assertFalse(space.fallback());
            assertFalse(calm.fallback());
            leads.add(instrument(fantasy, "lead"));
            scifi.add(instrument(space, "lead"));
            lofi.add(instrument(calm, "lead"));
        }
        assertTrue(leads.size() >= 3, leads.toString());
        assertFalse(scifi.isEmpty());
        assertFalse(lofi.isEmpty());
    }

    @Test
    void aPinBeatsTheSearch() throws Exception {
        Ensemble.Result result = Ensemble.select(null, "combat", java.nio.file.Path.of("samples/themes/pin.yaml"), null, 1);
        assertEquals("saw", instrument(result, "lead"));
        assertTrue(result.text().contains("from=pin"));
    }

    @Test
    void playbackSelectsWithoutAProfile() throws Exception {
        Ensemble.Result selected = Ensemble.select("fantasy", "sneaking", null, null, 1);
        assertEquals(instrument(selected, "lead"), selected.playing("lead", "flute"));
        assertEquals("strings", selected.playing("chords", "strings"));
    }

    @Test
    void aMissingKitKeepsTheFixedInstruments() throws Exception {
        Ensemble.Result result = Ensemble.select("fantasy", "sneaking", null, "nosuchprofile", 1);
        assertTrue(result.fallback());
        assertTrue(result.text().contains("ensemble.fallback=fixed"));
    }

    private static String instrument(Ensemble.Result result, String role) {
        for (Ensemble.Role row : result.roles()) {
            if (role.equals(row.role())) {
                return row.instrument();
            }
        }
        return "";
    }
}
