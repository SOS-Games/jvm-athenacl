// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetAuditionTest {
    @Test
    void everyPresetPlaysItsCharacter() throws Exception {
        List<String> ids = Presets.ids();
        assertTrue(ids.size() >= 16, "presets=" + ids.size());
        Set<String> characters = new HashSet<>();
        for (String id : ids) {
            Presets.Audition played = Presets.audition(id, 8, 1);
            assertEquals(id, played.id());
            assertEquals(8, played.bars());
            assertEquals(1, played.seed());
            assertTrue(played.notes() > 0, id);
            assertTrue(characters.add(played.character()), played.character());
        }
    }
}
