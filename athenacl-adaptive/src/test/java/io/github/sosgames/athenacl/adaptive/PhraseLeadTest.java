// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.TimeBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lead is one note at a time, and a period's answer starts like its question. */
class PhraseLeadTest {
    @Test
    void aPeriodSharesItsOpeningAndDoesNotOverlap() throws Exception {
        Theme theme = Themes.parse("fantasy.yaml", """
                id: fantasy
                preset: fantasy
                bars: 8
                meter: "4/4"
                """);
        Director.Performance performance = Director.perform(theme, List.of(), 1);
        List<NoteEvent> lead = lead(performance);
        assertFalse(lead.isEmpty());
        assertMonophonic(lead);
        assertEquals(pitches(lead, 1), pitches(lead, 5));
        assertChordTones(performance, lead);
        assertTrue(longCadence(lead, 4, 11, 4, 7), "half cadence " + pitches(lead, 4));
        assertTrue(longCadence(lead, 8, 9, 0), "authentic cadence " + pitches(lead, 8));
        assertTrue(leapThenStep(lead), "no recovered leap");
    }

    @Test
    void aHookThemeStaysMonophonic() throws Exception {
        Theme theme = Themes.load(java.nio.file.Path.of("themes/fantasy-dungeon.yaml"));
        Director.Performance performance = Director.perform(theme, List.of(), 1);
        assertMonophonic(lead(performance));
    }

    private static void assertMonophonic(List<NoteEvent> lead) {
        lead.sort(java.util.Comparator.comparingLong(NoteEvent::startTick).thenComparingLong(NoteEvent::id));
        for (int i = 1; i < lead.size(); i++) {
            NoteEvent previous = lead.get(i - 1);
            NoteEvent next = lead.get(i);
            assertTrue(previous.startTick() + previous.durationTicks() <= next.startTick(),
                    "overlap at " + previous.startTick());
        }
    }

    private static void assertChordTones(Director.Performance performance, List<NoteEvent> lead) {
        TreeMap<Integer, List<Integer>> pads = new TreeMap<>();
        for (Director.Played played : performance.notes()) {
            NoteEvent note = played.note();
            if ("pad".equals(note.layer())) {
                pads.computeIfAbsent(note.bar(), ignored -> new ArrayList<>()).add(Math.floorMod(note.pitch(), 12));
            }
        }
        long bar = TimeBase.barTicks(4, 4);
        for (NoteEvent note : lead) {
            long at = Math.floorMod(note.startTick(), bar);
            if (at != 0 && at != bar / 2) {
                continue;
            }
            List<Integer> chord = pads.get(note.bar());
            assertTrue(chord.contains(Math.floorMod(note.pitch(), 12)),
                    "bar " + note.bar() + " pitch " + note.pitch());
        }
    }

    private static boolean longCadence(List<NoteEvent> lead, int bar, int... pcs) {
        NoteEvent last = null;
        for (NoteEvent note : lead) {
            if (note.bar() == bar) {
                last = note;
            }
        }
        if (last == null || last.durationTicks() < TimeBase.PPQ * 2) {
            return false;
        }
        int pc = Math.floorMod(last.pitch(), 12);
        for (int want : pcs) {
            if (pc == want) {
                return true;
            }
        }
        return false;
    }

    private static boolean leapThenStep(List<NoteEvent> lead) {
        for (int i = 1; i < lead.size() - 1; i++) {
            int leap = Math.abs(lead.get(i).pitch() - lead.get(i - 1).pitch());
            int step = Math.abs(lead.get(i + 1).pitch() - lead.get(i).pitch());
            if (leap >= 5 && step >= 1 && step <= 2) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> pitches(List<NoteEvent> lead, int bar) {
        List<Integer> pitches = new ArrayList<>();
        for (NoteEvent note : lead) {
            if (note.bar() == bar) {
                pitches.add(note.pitch());
            }
        }
        return pitches;
    }

    private static List<NoteEvent> lead(Director.Performance performance) {
        List<NoteEvent> notes = new ArrayList<>();
        for (Director.Played played : performance.notes()) {
            if ("lead".equals(played.note().layer())) {
                notes.add(played.note());
            }
        }
        return notes;
    }
}
