// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A normal MIDI file at 960 ticks per quarter. Not the byte-exact athenaCL writer. */
public final class SmfWriter {
    private SmfWriter() {
    }

    public static byte[] write(Score score) throws IOException {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, TimeBase.PPQ, 1);
            Track track = sequence.getTracks()[0];
            List<MidiEvent> events = new ArrayList<>();
            events.add(new MidiEvent(tempo(score.bpm()), 0));
            events.add(new MidiEvent(meter(score.numerator(), score.denominator()), 0));
            for (ControlEvent control : score.controls()) {
                if (control instanceof ControlEvent.ProgramChange program) {
                    events.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE,
                            program.channel(), program.program(), 0), program.startTick()));
                }
            }
            for (NoteEvent note : score.notes()) {
                events.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, note.channel(),
                        note.pitch(), note.velocity()), note.startTick()));
                events.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, note.channel(),
                        note.pitch(), 0), note.startTick() + note.durationTicks()));
            }
            events.sort(Comparator.comparingLong(MidiEvent::getTick).thenComparingInt(SmfWriter::kind));
            for (MidiEvent event : events) {
                track.add(event);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MidiSystem.write(sequence, 1, out);
            return out.toByteArray();
        } catch (InvalidMidiDataException e) {
            throw new IOException(e);
        }
    }

    private static int kind(MidiEvent event) {
        if (!(event.getMessage() instanceof ShortMessage message)) {
            return 0;
        }
        return message.getCommand() == ShortMessage.NOTE_OFF ? 1 : 2;
    }

    private static MetaMessage tempo(int bpm) throws InvalidMidiDataException {
        int micros = 60_000_000 / bpm;
        byte[] data = new byte[] {
                (byte) ((micros >> 16) & 0xFF),
                (byte) ((micros >> 8) & 0xFF),
                (byte) (micros & 0xFF)
        };
        return new MetaMessage(0x51, data, 3);
    }

    private static MetaMessage meter(int numerator, int denominator) throws InvalidMidiDataException {
        int dd = 0;
        int value = denominator;
        while (value > 1) {
            value >>= 1;
            dd++;
        }
        return new MetaMessage(0x58, new byte[] {(byte) numerator, (byte) dd, 24, 8}, 4);
    }
}
