// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

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
 * Reads a composed melody from a MIDI file and writes it back as motif YAML.
 * The first note is degree 0. Later notes are scale steps in C major, the key the file is read in.
 */
public final class MotifImport {
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};

    private MotifImport() {
    }

    public static Motif read(Path path) throws IOException {
        try {
            Sequence sequence = MidiSystem.getSequence(path.toFile());
            List<Held> held = new ArrayList<>();
            List<MotifNote> notes = new ArrayList<>();
            int tonic = -1;
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
                    boolean noteOff = command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0);
                    if (command == ShortMessage.NOTE_ON && velocity > 0) {
                        held.add(new Held(pitch, event.getTick()));
                    } else if (noteOff) {
                        for (int h = 0; h < held.size(); h++) {
                            Held start = held.get(h);
                            if (start.pitch != pitch) {
                                continue;
                            }
                            held.remove(h);
                            if (tonic < 0) {
                                tonic = pitch;
                            }
                            long ticks = Math.max(1, event.getTick() - start.tick);
                            notes.add(new MotifNote(degree(pitch, tonic), duration(ticks)));
                            break;
                        }
                    }
                }
            }
            if (notes.isEmpty()) {
                throw new IOException("midi file has no notes");
            }
            return new Motif("m1", 7, notes);
        } catch (javax.sound.midi.InvalidMidiDataException e) {
            throw new IOException(e.getMessage());
        }
    }

    public static String yaml(Motif motif) {
        StringBuilder text = new StringBuilder();
        text.append("id: ").append(motif.id()).append('\n');
        text.append("notes:\n");
        for (MotifNote note : motif.notes()) {
            text.append("  - degree: ").append(note.degree()).append('\n');
            text.append("    dur: \"").append(note.duration()).append("\"\n");
        }
        return text.toString();
    }

    private static int degree(int pitch, int tonic) {
        int pc = Math.floorMod(pitch - tonic, 12);
        for (int i = 0; i < MAJOR.length; i++) {
            if (MAJOR[i] == pc) {
                return i;
            }
        }
        int best = 0;
        for (int i = 0; i < MAJOR.length; i++) {
            if (MAJOR[i] <= pc) {
                best = i;
            }
        }
        return best;
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
