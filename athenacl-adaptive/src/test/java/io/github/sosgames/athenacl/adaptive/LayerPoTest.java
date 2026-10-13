// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.TimeBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Sci-fi's arp is a sieve and its lead rhythm is a Markov pulse. Both follow the seed. */
class LayerPoTest {
    @Test
    void theArpKeepsOnlyTheSieveAndRepeats() throws Exception {
        Theme theme = Themes.parse("scifi.yaml", """
                id: scifi
                preset: scifi-space
                bars: 2
                meter: "4/4"
                """);
        Director.Performance once = Director.perform(theme, List.of(), 1);
        Director.Performance twice = Director.perform(theme, List.of(), 1);
        List<Long> onsets = arpOnsets(once, 1);
        assertEquals(List.of(0L, 480L, 1440L, 2400L, 2880L), onsets);
        assertEquals(onsets, arpOnsets(twice, 1));
        assertFalse(lead(once, 1).isEmpty());
        assertEquals(lead(once, 1), lead(twice, 1));
        long cell = TimeBase.PPQ / 2;
        for (long onset : onsets) {
            assertEquals(0, onset % cell);
        }
    }

    private static List<Long> arpOnsets(Director.Performance performance, int bar) {
        List<Long> onsets = new ArrayList<>();
        long barTicks = performance.score().barTicks();
        for (Director.Played played : performance.notes()) {
            NoteEvent note = played.note();
            if (note.bar() == bar && "arp".equals(note.layer())) {
                onsets.add(Math.floorMod(note.startTick(), barTicks));
            }
        }
        return onsets;
    }

    private static List<String> lead(Director.Performance performance, int bar) {
        List<String> durations = new ArrayList<>();
        for (Director.Played played : performance.notes()) {
            NoteEvent note = played.note();
            if (note.bar() == bar && "lead".equals(note.layer())) {
                durations.add(note.duration());
            }
        }
        return durations;
    }
}
