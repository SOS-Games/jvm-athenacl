// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveTest {
    @Test
    void fantasySneakingUsesTheStateTheme() throws IOException {
        Composition resolved = Presets.resolve("fantasy", "sneaking");
        assertEquals("dorian", resolved.text("scale.mode", ""));
        assertEquals("false", resolved.text("scale.leadingTone", ""));
        assertEquals("pedal", resolved.text("harmony.style", ""));
        assertEquals(76, resolved.number("tempo.bpm", 0));
        assertTrue(resolved.flag("drone.enabled", false));
        assertEquals("genre:fantasy|state:sneaking|state:fantasy/sneaking", resolved.chainText());
    }

    @Test
    void aTypoNamesTheNearestModule() {
        assertEquals("did you mean harmony", Resolver.hint("harmny"));
    }
}
