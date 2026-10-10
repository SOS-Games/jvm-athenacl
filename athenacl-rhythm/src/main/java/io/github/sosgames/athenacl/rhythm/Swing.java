// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Moves off-beat eighths later in the beat, then measures where they landed.
 * Only a note that sat exactly halfway through a quarter note is moved.
 * Downbeats stay put, so the grid is still easy to see.
 */
public final class Swing {
    private Swing() {
    }

    public static Score apply(Score score, Groove groove) {
        int target = (int) Math.round(groove.swing() * TimeBase.PPQ);
        int straight = TimeBase.PPQ / 2;
        if (target == straight) {
            return score;
        }
        long barTicks = score.barTicks();
        List<NoteEvent> notes = new ArrayList<>();
        int bars = score.bars();
        for (NoteEvent note : score.notes()) {
            long intoBeat = Math.floorMod(note.startTick(), TimeBase.PPQ);
            if (intoBeat != straight) {
                notes.add(note);
                continue;
            }
            long tick = note.startTick() - intoBeat + target;
            int bar = (int) (tick / barTicks) + 1;
            bars = Math.max(bars, bar);
            notes.add(new NoteEvent(
                    note.id(),
                    tick,
                    note.durationTicks(),
                    note.pitch(),
                    note.pitchName(),
                    note.velocity(),
                    note.channel(),
                    note.layer(),
                    bar,
                    beatText(tick % barTicks),
                    note.duration(),
                    note.seedPath()));
        }
        return new Score(score.controls(), notes, score.bpm(), score.numerator(), score.denominator(), bars);
    }

    /** Average off-beat placement, as a fraction of the quarter. Empty when no beat has both hits. */
    public static OptionalDouble measure(Score score) {
        TreeMap<Long, List<Long>> beats = new TreeMap<>();
        for (NoteEvent note : score.notes()) {
            if (note.channel() == 9) {
                continue;
            }
            long beatStart = note.startTick() - Math.floorMod(note.startTick(), TimeBase.PPQ);
            beats.computeIfAbsent(beatStart, key -> new ArrayList<>()).add(note.startTick());
        }
        double sum = 0;
        int count = 0;
        for (List<Long> onsets : beats.values()) {
            onsets.sort(Long::compare);
            Long down = null;
            Long off = null;
            long beatStart = onsets.get(0) - Math.floorMod(onsets.get(0), TimeBase.PPQ);
            for (long tick : onsets) {
                long into = tick - beatStart;
                if (into < 0 || into >= TimeBase.PPQ) {
                    continue;
                }
                if (down == null && into <= TimeBase.PPQ / 5) {
                    down = tick;
                } else if (off == null && into > TimeBase.PPQ / 4) {
                    off = tick;
                }
            }
            if (down != null && off != null) {
                sum += (off - beatStart) / (double) TimeBase.PPQ;
                count++;
            }
        }
        if (count == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sum / count);
    }

    public static String format(double swing) {
        return String.format(Locale.ROOT, "%.2f", swing);
    }

    static String beatText(long intoBar) {
        long quarters = intoBar / TimeBase.PPQ;
        long remain = intoBar % TimeBase.PPQ;
        if (remain == 0) {
            return Long.toString(quarters + 1);
        }
        double value = quarters + 1 + remain / (double) TimeBase.PPQ;
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
