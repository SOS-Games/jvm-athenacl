// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeDiffTest {
    @Test
    void combatKeyDiffersBetweenDungeonAndSpace() throws Exception {
        Theme dungeon = Themes.load(Path.of("themes/fantasy-dungeon.yaml"));
        Theme space = Themes.load(Path.of("themes/scifi-space.yaml"));
        String text = ThemeDiff.text(ThemeDiff.compare(dungeon, space));
        assertTrue(text.contains("changed=/states/combat/key from=D-aeolian to=F-lydian"), text);
    }
}
