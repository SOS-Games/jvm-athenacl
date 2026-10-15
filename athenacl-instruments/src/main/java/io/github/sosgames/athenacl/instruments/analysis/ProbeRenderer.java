// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.render.GervillStreams;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Renders one dry probe through a fresh Gervill synth.
 * Reverb, chorus, and auto gain stay off. The same note, velocity, and soundfont
 * produce the same samples on one JDK.
 */
public final class ProbeRenderer {
    public static final int RATE = 48000;
    public static final double HOLD_SECONDS = 2.0;
    public static final double RELEASE_SECONDS = 1.5;

    private ProbeRenderer() {
    }

    public static float[] render(Path soundbank, int bank, int program, boolean percussion,
                                 int[] notes, int velocity) throws IOException {
        try (GervillStreams streams = GervillStreams.openProbe(soundbank)) {
            Receiver receiver = streams.receiver();
            int channel = percussion ? 9 : 0;
            long holdUs = Math.round(HOLD_SECONDS * 1_000_000.0);
            if (!percussion) {
                receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 0, 0), 0);
                receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 32, bank & 127), 0);
            }
            receiver.send(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, program & 127, 0), 0);
            receiver.send(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 7, 127), 0);
            for (int note : notes) {
                int pitch = Math.max(0, Math.min(127, note));
                receiver.send(new ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), 0);
                receiver.send(new ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), holdUs);
            }
            int frames = (int) Math.round((HOLD_SECONDS + RELEASE_SECONDS) * RATE);
            return read(streams.stream(), frames);
        } catch (MidiUnavailableException | InvalidMidiDataException e) {
            throw new IOException(e.getMessage() == null ? "probe failed" : e.getMessage(), e);
        }
    }

    private static float[] read(AudioInputStream stream, int frames) throws IOException {
        AudioFormat format = stream.getFormat();
        int channels = Math.max(1, format.getChannels());
        int frameBytes = format.getFrameSize() > 0 ? format.getFrameSize() : channels * 2;
        byte[] raw = new byte[frames * frameBytes];
        int offset = 0;
        while (offset < raw.length) {
            int n = stream.read(raw, offset, raw.length - offset);
            if (n < 0) {
                break;
            }
            offset += n;
        }
        float[] out = new float[frames];
        boolean floating = format.getEncoding() == AudioFormat.Encoding.PCM_FLOAT;
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(
                format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            int base = i * frameBytes;
            if (base + (floating ? 4 : 2) > offset) {
                break;
            }
            buffer.position(base);
            if (floating) {
                out[i] = buffer.getFloat();
            } else {
                out[i] = buffer.getShort() / 32768.0f;
            }
        }
        return out;
    }
}
