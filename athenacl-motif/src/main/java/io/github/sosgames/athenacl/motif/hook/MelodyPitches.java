// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.hook;

import io.github.sosgames.athenacl.events.TimeBase;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a one-line melody from a MIDI file. The critic scores these pitches directly,
 * so a chromatic leap stays a leap. {@code MotifImport} instead folds everything into
 * scale degrees, and that would hide the leap this check is looking for.
 */
public final class MelodyPitches {
    private MelodyPitches() {
    }

    public record Note(int midi, String duration) {
    }

    public static List<Note> read(Path path) throws IOException {
        try {
            Sequence sequence = MidiSystem.getSequence(path.toFile());
            List<Held> held = new ArrayList<>();
            List<Note> notes = new ArrayList<>();
            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    MidiMessage message = event.getMessage();
                    if (!(message instanceof ShortMessage shortMessage)) {
                        continue;
                    }
                    int command = shortMessage.getCommand();
                    int pitch = shortMessage.getData1();
                    int velocity = shortMessage.getData2();
                    boolean noteOff = command == ShortMessage.NOTE_OFF
                            || (command == ShortMessage.NOTE_ON && velocity == 0);
                    if (command == ShortMessage.NOTE_ON && velocity > 0) {
                        held.add(new Held(pitch, event.getTick()));
                    } else if (noteOff) {
                        for (int h = 0; h < held.size(); h++) {
                            Held start = held.get(h);
                            if (start.pitch != pitch) {
                                continue;
                            }
                            held.remove(h);
                            long ticks = Math.max(1, event.getTick() - start.tick);
                            notes.add(new Note(pitch, duration(ticks)));
                            break;
                        }
                    }
                }
            }
            if (notes.isEmpty()) {
                throw new IOException("midi file has no notes");
            }
            return notes;
        } catch (javax.sound.midi.InvalidMidiDataException e) {
            throw new IOException(e.getMessage());
        }
    }

    public static int[] midis(List<Note> notes) {
        int[] midis = new int[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            midis[i] = notes.get(i).midi();
        }
        return midis;
    }

    public static String[] durations(List<Note> notes) {
        String[] durations = new String[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            durations[i] = notes.get(i).duration();
        }
        return durations;
    }

    private static String duration(long ticks) {
        long quarters = Math.max(1, Math.round(ticks / (double) TimeBase.PPQ));
        if (quarters >= 4) {
            return "1/1";
        }
        if (quarters == 2) {
            return "1/2";
        }
        if (quarters == 1) {
            return "1/4";
        }
        return "1/" + (4 / quarters);
    }

    private record Held(int pitch, long tick) {
    }
}
