// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The notes a path becomes before they are written as MIDI.
 * Each pitch group is a chord. Its share of {@code totalSeconds} comes from its duration weight.
 */
public final class EventList {
    private final List<AthenaEvent> events = new ArrayList<>();

    public static EventList fromPath(PolyPath path, double totalSeconds, int velocity, int pan) {
        EventList list = new EventList();
        double sum = path.durSum();
        double time = 0;
        for (Multiset group : path.groups()) {
            double dur = totalSeconds * group.dur() / sum;
            for (double ps : group.pitches()) {
                list.events.add(new AthenaEvent(time, dur, dur, 1, velocity, ps, pan, path.name()));
            }
            time += dur;
        }
        return list;
    }

    public List<AthenaEvent> events() {
        return List.copyOf(events);
    }

    public List<MidiScore.Note> notes() {
        List<MidiScore.Note> notes = new ArrayList<>();
        for (AthenaEvent event : events) {
            if (event.acc() == 0 || event.amp() == 0) {
                continue;
            }
            notes.add(new MidiScore.Note(event.time(), event.sus(), (int) event.amp(),
                    Pitch.psToMidi(event.ps()), (int) event.pan()));
        }
        return notes;
    }
}
