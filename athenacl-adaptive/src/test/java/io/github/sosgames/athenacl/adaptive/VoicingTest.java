// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.theory.ClosedVoiceLeader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pad, brass, and bell stack the whole chord unless the state asks for an open fifth. */
class VoicingTest {
    @Test
    void aCloseVoicingKeepsTheThirdAndSteps() throws Exception {
        Theme theme = Themes.parse("fantasy.yaml", """
                id: fantasy
                preset: fantasy
                bars: 2
                meter: "4/4"
                """);
        Director.Performance performance = Director.perform(theme, List.of(), 1);
        TreeMap<Integer, List<Integer>> pads = pitches(performance, "pad");
        assertTrue(hasThird(pads.get(1)), "bar 1 pad " + pads.get(1));
        int[] first = ints(pads.get(1));
        int[] second = ints(pads.get(2));
        assertEquals(first.length, second.length);
        assertTrue(ClosedVoiceLeader.maxMotion(first, second) <= 4, "leap " + ClosedVoiceLeader.maxMotion(first, second));
        assertEquals(0, ClosedVoiceLeader.parallels(first, second));
    }

    @Test
    void anOpenFifthIsRequested() throws Exception {
        Theme theme = Themes.parse("open.yaml", """
                id: open
                preset: fantasy
                bars: 1
                meter: "4/4"
                states:
                  peaceful:
                    voicing: {style: open5}
                """);
        Director.Performance performance = Director.perform(theme, List.of(), 1);
        List<Integer> pads = pitches(performance, "pad").get(1);
        assertEquals(2, pads.size());
        assertEquals(7, Math.abs(pads.get(1) - pads.get(0)));
    }

    private static boolean hasThird(List<Integer> pitches) {
        for (int i = 0; i < pitches.size(); i++) {
            for (int j = i + 1; j < pitches.size(); j++) {
                int gap = Math.floorMod(pitches.get(j) - pitches.get(i), 12);
                if (gap == 3 || gap == 4) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[] ints(List<Integer> pitches) {
        int[] values = new int[pitches.size()];
        for (int i = 0; i < pitches.size(); i++) {
            values[i] = pitches.get(i);
        }
        return values;
    }

    private static TreeMap<Integer, List<Integer>> pitches(Director.Performance performance, String layer) {
        TreeMap<Integer, List<Integer>> byBar = new TreeMap<>();
        for (Director.Played played : performance.notes()) {
            NoteEvent note = played.note();
            if (layer.equals(note.layer())) {
                byBar.computeIfAbsent(note.bar(), ignored -> new ArrayList<>()).add(note.pitch());
            }
        }
        return byBar;
    }
}
