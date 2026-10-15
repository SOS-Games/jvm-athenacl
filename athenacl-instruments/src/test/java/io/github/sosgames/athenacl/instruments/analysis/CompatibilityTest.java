// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityTest {
    @Test
    void aPluckFitsAnArpAndNotAPad() {
        InstrumentProfile.Row pluck = row("pluck", "ok", List.of("plucky", "warm"), 7, 8, 1.1, 0.02, 90, true);
        assertTrue(Compatibility.fitness("pad", pluck) < 0.5);
        assertTrue(Compatibility.fitness("arp", pluck) >= 0.7);
    }

    @Test
    void theSameRegisterMasksItself() {
        InstrumentProfile.Row saw = row("saw", "ok", List.of("bright"), 0.1, 12, 3.2, 0.01, 2000, false);
        Compatibility.Pair pair = Compatibility.pair(saw, saw, "C4..C5", "C4..C5");
        assertEquals("clash", pair.verdict());
        assertTrue(pair.mask() >= 0.9);
    }

    @Test
    void aThemeLayerKeepsTheFileName() {
        assertEquals("theme:lint-override.yaml",
                Compatibility.layerName("theme:samples/themes/lint-override.yaml"));
    }

    private static InstrumentProfile.Row row(String name, String verdict, List<String> tags,
                                              double cents, double attack, double centroid, double flatness,
                                              double decay, boolean died) {
        return new InstrumentProfile.Row(name, verdict, List.of(), tags, cents, attack, "measured",
                centroid, flatness, decay, died, 0.1, -20, SoundClass.SUSTAINED, 0, "");
    }
}
