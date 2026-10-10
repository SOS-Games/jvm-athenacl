// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a roman-numeral string into a score. Each numeral is one bar of 4/4 at 120 bpm,
 * held as whole notes. {@code voiceLead} asks {@link ClosedVoiceLeader} for the smoothest
 * legal voicing; without it, every chord is stacked in one octave from C4.
 */
public final class Progressions {
    private Progressions() {
    }

    public record Realized(Score score, int chords, int maxMotion, int parallels) {
    }

    public static Realized realize(String numerals, Key key, boolean voiceLead) {
        List<RomanNumeral> chords = parse(numerals);
        if (chords.isEmpty()) {
            throw new IllegalArgumentException("progression is empty");
        }
        long barTicks = TimeBase.barTicks(4, 4);
        List<NoteEvent> notes = new ArrayList<>();
        int[] previous = null;
        int maxMotion = 0;
        int parallels = 0;
        long id = 1;
        for (int bar = 1; bar <= chords.size(); bar++) {
            int[] pcs = chords.get(bar - 1).pitchClasses(key);
            int[] voiced = voice(pcs, previous, voiceLead);
            if (previous != null && previous.length == voiced.length) {
                maxMotion = Math.max(maxMotion, ClosedVoiceLeader.maxMotion(previous, voiced));
                parallels += ClosedVoiceLeader.parallels(previous, voiced);
            }
            previous = voiced;
            long start = (bar - 1L) * barTicks;
            for (int midi : voiced) {
                notes.add(new NoteEvent(
                        id,
                        start,
                        barTicks,
                        midi,
                        key.noteName(midi),
                        80,
                        0,
                        "harmony",
                        bar,
                        "1",
                        "1/1",
                        "0/harmony/bar" + bar + "/note#" + id));
                id++;
            }
        }
        List<ControlEvent> controls = List.of(
                new ControlEvent.Tempo(0, 120),
                new ControlEvent.Meter(0, 4, 4),
                new ControlEvent.ProgramChange(0, 0, 0));
        Score score = new Score(controls, notes, 120, 4, 4, chords.size());
        return new Realized(score, chords.size(), maxMotion, parallels);
    }

    public static List<RomanNumeral> parse(String numerals) {
        if (numerals == null || numerals.isBlank()) {
            throw new IllegalArgumentException("progression is empty");
        }
        List<RomanNumeral> chords = new ArrayList<>();
        for (String token : numerals.trim().split("\\s+")) {
            chords.add(RomanNumeral.parse(token));
        }
        return chords;
    }

    private static int[] voice(int[] pcs, int[] previous, boolean voiceLead) {
        if (!voiceLead) {
            return ClosedVoiceLeader.block(pcs);
        }
        if (previous == null) {
            return ClosedVoiceLeader.first(pcs);
        }
        return ClosedVoiceLeader.next(pcs, previous);
    }
}
