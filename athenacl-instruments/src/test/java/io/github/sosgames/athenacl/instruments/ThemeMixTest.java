// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import io.github.sosgames.athenacl.adaptive.LayerSpec;
import io.github.sosgames.athenacl.adaptive.Presets;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeMixTest {
    @Test
    void aGenreAuditionHearsItsDeclaredInstruments() throws Exception {
        Theme hiphop = Presets.auditionTheme("hiphop", 14);
        Theme rock = Presets.auditionTheme("rock", 14);
        Theme fantasy = Presets.auditionTheme("fantasy", 14);
        assertNull(Ensemble.forPlayback(hiphop.base().composition(), hiphop.base().id(), 1));
        assertEquals("lead.saw", heard(hiphop, "lead"));
        assertEquals("drums.808", heard(hiphop, "drums"));
        assertEquals("rhodes", heard(hiphop, "chords"));
        assertEquals("guitar.overdrive", heard(rock, "lead"));
        assertEquals("guitar.clean", heard(rock, "harp"));
        assertEquals("flute", heard(fantasy, "lead"));
        assertEquals("strings", heard(fantasy, "pad"));
        assertEquals(List.of(""), ThemeMix.playbackStates(hiphop).stream().map(Theme.StateDef::id).toList());
    }

    @Test
    void aDeclaredStateStillSelectsAnEnsemble() throws Exception {
        Theme sneaking = Themes.parse("sneaking.yaml", """
                id: fantasy
                preset: fantasy
                bars: 4
                states:
                  sneaking: {use: library:sneaking}
                """);
        Theme.StateDef state = sneaking.state("sneaking");
        Ensemble.Result ensemble = Ensemble.forPlayback(state.composition(), state.id(), 1);
        assertNotNull(ensemble);
        assertTrue(ensemble.text().contains("role=lead"));
        Theme dungeon = Themes.load(Path.of("themes/fantasy-dungeon.yaml"));
        List<String> ids = ThemeMix.playbackStates(dungeon).stream().map(Theme.StateDef::id).toList();
        assertTrue(ids.contains("combat"));
        assertTrue(ids.contains("partying"));
        assertTrue(ids.stream().noneMatch(String::isEmpty));
    }

    private static String heard(Theme theme, String layer) {
        Theme.StateDef state = ThemeMix.playbackStates(theme).get(0);
        LayerSpec spec = null;
        for (LayerSpec candidate : state.specs()) {
            if (layer.equals(candidate.name())) {
                spec = candidate;
            }
        }
        assertNotNull(spec, layer);
        return ThemeMix.heardName(state, spec, Ensemble.forPlayback(state.composition(), state.id(), 1));
    }
}
