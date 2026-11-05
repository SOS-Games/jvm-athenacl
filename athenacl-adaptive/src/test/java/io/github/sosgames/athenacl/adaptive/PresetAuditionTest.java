// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.motif.critic.MelodyCritic;
import io.github.sosgames.athenacl.motif.hook.HookSettings;
import io.github.sosgames.athenacl.theory.Key;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            Director.Performance performance = Director.perform(theme(id, 16), List.of(), 1);
            PhraseAnalysis.Report report = PhraseAnalysis.of(performance.score());
            assertTrue(report.passes(), id + " " + report.line());
        }
    }

    @Test
    void fantasyStatesMatchTheHarmonyReport() throws Exception {
        PhraseAnalysis.Report sneaking = PhraseAnalysis.of(play("fantasy", "sneaking", 16).score());
        assertTrue(sneaking.dronePresent(), sneaking.line());
        PhraseAnalysis.Report combat = PhraseAnalysis.of(play("fantasy", "combat", 16).score());
        assertFalse(combat.dronePresent(), combat.line());
        assertTrue(combat.dominantCadences() >= 1, combat.line());
        Theme dungeon = Themes.load(java.nio.file.Path.of("themes/fantasy-dungeon.yaml"));
        var timeline = ControlLog.readTimeline(java.nio.file.Path.of("samples/timelines/dungeon-tour.json"));
        PhraseAnalysis.Report tour = PhraseAnalysis.of(Director.perform(dungeon, timeline, 1).score());
        assertTrue(tour.distinctRoots() >= 4, tour.line());
        assertTrue(tour.dominantCadences() >= 1, tour.line());
        assertEquals(0, tour.overlaps(), tour.line());
        assertTrue(tour.rhythmValues() >= 3, tour.line());
    }

    @Test
    void aPeacefulFantasyPhraseScores() throws Exception {
        Theme theme = Themes.parse("fantasy.yaml", """
                id: fantasy
                preset: fantasy
                bars: 8
                states:
                  peaceful: {use: library:peaceful}
                hold: {state: peaceful, intensity: 0.5}
                """);
        Director.Performance performance = Director.perform(theme, List.of(), 1);
        List<NoteEvent> lead = new ArrayList<>();
        for (Director.Played played : performance.notes()) {
            if ("lead".equals(played.note().layer())) {
                lead.add(played.note());
            }
        }
        lead.sort(Comparator.comparingLong(NoteEvent::startTick));
        int[] midi = new int[lead.size()];
        String[] durations = new String[lead.size()];
        for (int i = 0; i < lead.size(); i++) {
            midi[i] = lead.get(i).pitch();
            durations[i] = lead.get(i).duration();
        }
        Key key = Key.parse(performance.bars().get(0).key());
        String romans = HarmonyNote.json(theme.states().get("peaceful"), 8);
        int mark = romans.indexOf("\"progression\":\"") + "\"progression\":\"".length();
        String progression = romans.substring(mark, romans.indexOf('"', mark));
        MelodyCritic.Scored scored = MelodyCritic.scorePitches(midi, durations, key, HookSettings.defaults(),
                MelodyCritic.chords(key, progression), List.of(), MelodyCritic.Mode.PHRASE);
        assertTrue(scored.score() >= 0.75, scored.score() + " rejected=" + scored.rejected());
    }

    @Test
    void aGenreAuditionStaysOnThatPalette() throws Exception {
        Theme hiphop = Presets.auditionTheme("hiphop", 14);
        Theme rock = Presets.auditionTheme("rock", 14);
        Theme fantasy = Presets.auditionTheme("fantasy", 14);
        assertEquals("(none)", hiphop.declared());
        assertEquals("(none)", rock.declared());
        assertTrue(hiphop.timeline().isEmpty());
        assertTrue(rock.timeline().isEmpty());
        assertTrue(fantasy.timeline().isEmpty());
        assertEquals("bass.synth,pad.newage,drums.808,lead.saw,rhodes,lead.square", hiphop.instrumentList(""));
        assertEquals("guitar.overdrive,bass.finger,drums.standard,organ,guitar.clean,piano", rock.instrumentList(""));
        assertEquals("strings,piano,cello,flute", fantasy.instrumentList(""));
        assertEquals("", hiphop.instrumentList("combat"));
        Director.Performance played = Director.perform(hiphop, List.of(), 1);
        Set<Integer> programs = new HashSet<>();
        for (Director.BarInfo bar : played.bars()) {
            assertEquals("", bar.state());
        }
        for (var control : played.score().controls()) {
            if (control instanceof ControlEvent.ProgramChange change) {
                programs.add(change.program());
            }
        }
        assertTrue(programs.contains(81), programs.toString());
        assertTrue(programs.contains(38), programs.toString());
        assertFalse(programs.contains(73), programs.toString());
        Set<Integer> rockPrograms = programs(Director.perform(rock, List.of(), 1));
        assertTrue(rockPrograms.contains(29), rockPrograms.toString());
        assertFalse(rockPrograms.contains(81), rockPrograms.toString());
        Theme dungeon = Themes.load(java.nio.file.Path.of("themes/fantasy-dungeon.yaml"));
        assertTrue(dungeon.states().containsKey("combat"));
        assertTrue(dungeon.states().containsKey("partying"));
    }

    private static Set<Integer> programs(Director.Performance performance) {
        Set<Integer> programs = new HashSet<>();
        for (var control : performance.score().controls()) {
            if (control instanceof ControlEvent.ProgramChange change) {
                programs.add(change.program());
            }
        }
        return programs;
    }

    private static Theme theme(String id, int bars) throws ThemeException {
        return Themes.parse(id + ".yaml", "id: " + id + "\npreset: " + id + "\nbars: " + bars + "\n");
    }

    private static Director.Performance play(String genre, String state, int bars) throws ThemeException {
        String yaml = "id: " + genre + "\npreset: " + genre + "\nbars: " + bars + "\n"
                + "states:\n  " + state + ": {use: library:" + state + "}\n"
                + "hold:\n  state: " + state + "\n  intensity: 0.5\n";
        return Director.perform(Themes.parse(genre + ".yaml", yaml), List.of(), 1);
    }
}
