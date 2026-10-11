// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeSwitchTest {
    @Test
    void waitsForTheNextBarAfterTheKitIsReady() {
        ThemeSwitch swap = new ThemeSwitch("gm-lite", 8);
        swap.arm("gm-full");
        assertEquals("gm-lite", swap.poll(1));
        swap.preloaded();
        assertEquals("gm-lite", swap.poll(1));
        assertEquals("gm-lite", swap.poll(7));
        assertEquals("gm-full", swap.poll(8));
        assertEquals("gm-full", swap.kit());
    }

    @Test
    void aKitThatFinishesMidBarWaitsForTheFollowingBar() {
        ThemeSwitch swap = new ThemeSwitch("gm-lite", 1);
        swap.arm("gm-full");
        swap.preloaded();
        assertEquals("gm-lite", swap.poll(4));
        assertEquals("gm-full", swap.poll(5));
    }
}
