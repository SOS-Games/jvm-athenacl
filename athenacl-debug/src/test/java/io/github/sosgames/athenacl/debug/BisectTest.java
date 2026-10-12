// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BisectTest {
    @Test
    void injectedNoteIsFoundAtThatBar() {
        String good = note(1, 1, 60) + note(2, 3, 60);
        String bad = note(1, 1, 60) + note(2, 3, 62);
        Bisect.Hit hit = Bisect.events(good, bad);
        assertEquals(3, hit.bar());
        assertEquals(2, hit.eventId());
    }

    @Test
    void injectedControlIsTheFirstNewCall() {
        String good = call(1) + call(5) + call(9);
        String bad = call(1) + call(5) + call(7) + call(9);
        assertEquals(7, Bisect.firstControl(good, bad));
    }

    @Test
    void firstTrueIsTheShortestPrefix() {
        assertEquals(3, Bisect.firstTrue(5, k -> k >= 3));
        assertEquals(0, Bisect.firstTrue(4, k -> false));
    }

    private static String note(int id, int bar, int pitch) {
        return "{\"type\":\"note\",\"id\":" + id + ",\"bar\":" + bar + ",\"pitch\":" + pitch + "}\n";
    }

    private static String call(int bar) {
        return "{\"type\":\"state\",\"bar\":" + bar + ",\"beat\":1,\"state\":\"combat\",\"intensity\":0.6}\n";
    }
}
