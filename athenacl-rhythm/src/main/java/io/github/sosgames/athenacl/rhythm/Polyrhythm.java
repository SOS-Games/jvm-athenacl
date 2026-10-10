// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Two pulse trains in the same bar, such as 3 against 2. Both hit the downbeat,
 * so they line up at the start of every bar even though the beats between differ.
 */
public final class Polyrhythm {
    private Polyrhythm() {
    }

    public record Voices(Score score, int layers, String alignedAtBar) {
    }

    public static Voices render(int left, int right, int bars) {
        if (left < 1 || right < 1) {
            throw new IllegalArgumentException("polyrhythm needs two counts above 0");
        }
        if (bars < 1) {
            throw new IllegalArgumentException("bars must be 1 or more");
        }
        long barTicks = TimeBase.barTicks(4, 4);
        List<NoteEvent> notes = new ArrayList<>();
        long id = 1;
        for (int bar = 1; bar <= bars; bar++) {
            id = lay(notes, id, Integer.toString(left), 60, "C4", left, bar, barTicks);
            id = lay(notes, id, Integer.toString(right), 55, "G3", right, bar, barTicks);
        }
        List<ControlEvent> controls = List.of(
                new ControlEvent.Tempo(0, 120),
                new ControlEvent.Meter(0, 4, 4),
                new ControlEvent.ProgramChange(0, 0, 0));
        Score score = new Score(controls, notes, 120, 4, 4, bars);
        return new Voices(score, 2, aligned(score, Integer.toString(left), Integer.toString(right)));
    }

    public static int[] ratio(String text) {
        if (text == null || !text.contains(":")) {
            throw new IllegalArgumentException("polyrhythm " + text);
        }
        String[] parts = text.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("polyrhythm " + text);
        }
        try {
            int left = Integer.parseInt(parts[0].trim());
            int right = Integer.parseInt(parts[1].trim());
            if (left < 1 || right < 1) {
                throw new IllegalArgumentException("polyrhythm " + text);
            }
            return new int[] {left, right};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("polyrhythm " + text);
        }
    }

    private static long lay(List<NoteEvent> notes, long id, String layer, int pitch, String name,
                            int pulses, int bar, long barTicks) {
        long start = (bar - 1L) * barTicks;
        long gap = barTicks / pulses;
        long dur = Math.min(TimeBase.PPQ / 4, Math.max(1, gap - 1));
        for (int i = 0; i < pulses; i++) {
            long tick = start + i * gap;
            notes.add(new NoteEvent(
                    id,
                    tick,
                    dur,
                    pitch,
                    name,
                    96,
                    0,
                    layer,
                    bar,
                    Swing.beatText(tick - start),
                    "1/16",
                    "0/" + layer + "/bar" + bar + "/note#" + id));
            id++;
        }
        return id;
    }

    private static String aligned(Score score, String left, String right) {
        StringBuilder bars = new StringBuilder();
        for (int bar = 1; bar <= score.bars(); bar++) {
            long down = (bar - 1L) * score.barTicks();
            if (hits(score, left, down) && hits(score, right, down)) {
                if (!bars.isEmpty()) {
                    bars.append(',');
                }
                bars.append(bar);
            }
        }
        return bars.toString();
    }

    private static boolean hits(Score score, String layer, long tick) {
        for (NoteEvent note : score.notes()) {
            if (note.layer().equals(layer) && note.startTick() == tick) {
                return true;
            }
        }
        return false;
    }
}
