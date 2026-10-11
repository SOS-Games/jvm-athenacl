// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TempoMap;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.nio.file.Path;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Pulls PCM from Gervill for the score. The event-log hash is the canonical snapshot. This file
 * is for listening, and on one JDK the same notes come back as the same bytes.
 */
public final class WavRenderer {
    public static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);
    private static final long TAIL_MICROS = 400_000;

    private WavRenderer() {
    }

    public static byte[] write(Score score) throws IOException, MidiUnavailableException {
        return write(score, null);
    }

    public static byte[] write(Score score, Path soundbank) throws IOException, MidiUnavailableException {
        return write(score, soundbank, null);
    }

    /** {@code slots} loads only those instruments. A null list keeps the single-bank path. */
    public static byte[] write(Score score, Path soundbank, List<SlotSpec> slots)
            throws IOException, MidiUnavailableException {
        TempoMap tempo = new TempoMap(score.bpm());
        long lastTick = 0;
        for (NoteEvent note : score.notes()) {
            lastTick = Math.max(lastTick, note.startTick() + note.durationTicks());
        }
        long frames = (tempo.toMicros(lastTick) + TAIL_MICROS) * 44100L / 1_000_000L;
        int bytes = (int) frames * FORMAT.getFrameSize();
        byte[] pcm = new byte[bytes];
        try (GervillStreams streams = openStream(soundbank, slots)) {
            Receiver receiver = streams.receiver();
            try {
                send(receiver, score, tempo);
            } catch (InvalidMidiDataException e) {
                throw new IOException(e);
            }
            AudioInputStream audio = streams.stream();
            int offset = 0;
            while (offset < pcm.length) {
                int read = audio.read(pcm, offset, pcm.length - offset);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    break;
                }
                offset += read;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, frames),
                AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    /** Length of the WAV this renderer would write, including the short tail after the last note. */
    public static double durationSeconds(Score score) {
        TempoMap tempo = new TempoMap(score.bpm());
        long lastTick = 0;
        for (NoteEvent note : score.notes()) {
            lastTick = Math.max(lastTick, note.startTick() + note.durationTicks());
        }
        return (tempo.toMicros(lastTick) + TAIL_MICROS) / 1_000_000.0;
    }

    private static GervillStreams openStream(Path soundbank, List<SlotSpec> slots)
            throws IOException, MidiUnavailableException {
        try {
            if (slots != null) {
                GervillStreams streams = GervillStreams.open(FORMAT, null);
                streams.loadSlots(slots);
                return streams;
            }
            return GervillStreams.open(FORMAT, soundbank);
        } catch (InvalidMidiDataException e) {
            throw new IOException(e);
        }
    }

    private static void send(Receiver receiver, Score score, TempoMap tempo)
            throws InvalidMidiDataException {
        for (ControlEvent control : score.controls()) {
            if (control instanceof ControlEvent.ProgramChange program) {
                receiver.send(new ShortMessage(ShortMessage.PROGRAM_CHANGE, program.channel(),
                        program.program(), 0), tempo.toMicros(program.startTick()));
            } else if (control instanceof ControlEvent.ControlChange change) {
                receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, change.channel(),
                        change.controller(), change.value()), tempo.toMicros(change.startTick()));
            }
        }
        for (NoteEvent note : score.notes()) {
            long on = tempo.toMicros(note.startTick());
            long off = tempo.toMicros(note.startTick() + note.durationTicks());
            receiver.send(new ShortMessage(ShortMessage.NOTE_ON, note.channel(), note.pitch(),
                    note.velocity()), on);
            receiver.send(new ShortMessage(ShortMessage.NOTE_OFF, note.channel(), note.pitch(), 0), off);
        }
    }
}
