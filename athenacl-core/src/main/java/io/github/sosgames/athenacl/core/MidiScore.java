// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.Py;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * Writes a Standard MIDI file the way athenaCL's score writer does: 960 ticks per quarter,
 * format 1, a meta track, then one track per part. Channel 10 is left for drums.
 * A note-off is a note-on with velocity 0. Times are seconds, turned into ticks with
 * half-to-even rounding. Parts that share a channel are not shifted: the original
 * scores the notes before it sets that flag, so the file never shows the shift.
 */
public final class MidiScore {
    public static final int PPQ = 960;

    public record Note(double startSec, double durSec, int velocity, int pitch, int pan) {
    }

    public record Track(String name, int program, Integer channel, List<Note> notes) {
    }

    public record Bytes(byte[] midi, int tracks) {
    }

    private MidiScore() {
    }

    public static Bytes write(List<Track> tracks, String title, int tempo) {
        TreeMap<Integer, Part> parts = new TreeMap<>();
        int[] open = openChannels();
        int next = 0;
        for (Track track : tracks) {
            int channel = track.channel == null ? open[next++] : track.channel;
            if (next >= open.length) {
                next = 0;
            }
            Part part = parts.computeIfAbsent(channel, key -> new Part());
            part.add(0, meta(0x03, track.name));
            part.add(0, control(channel, 0x07, 127));
            part.add(0, program(channel, track.program));
            for (Note note : track.notes) {
                if (note.velocity == 0) {
                    continue;
                }
                int start = ticks(note.startSec, tempo);
                int end = start + ticks(note.durSec, tempo);
                byte status = (byte) (0x90 | (channel - 1));
                part.add(start, control(channel, 0x0A, note.pan));
                part.add(start, new byte[] {status, (byte) note.pitch, (byte) note.velocity});
                part.add(end, new byte[] {status, (byte) note.pitch, 0});
            }
        }
        Part meta = new Part();
        meta.add(0, meta(0x03, title));
        meta.add(0, tempoEvent(tempo));
        meta.add(0, new byte[] {(byte) 0xFF, 0x58, 0x04, 0x04, 0x02, 0x18, 0x08});
        meta.add(0, meta(0x01, "created with athenaCL"));

        int music = 0;
        for (Part part : parts.values()) {
            if (part.times() > 1) {
                music++;
            }
        }
        Buf out = new Buf();
        out.write("MThd");
        out.write(intToLong(6));
        out.write(intToWord(1));
        out.write(intToWord(1 + music));
        out.write(intToWord(PPQ));
        writeTrack(out, meta);
        for (Part part : parts.values()) {
            if (part.times() > 1) {
                writeTrack(out, part);
            }
        }
        return new Bytes(out.toByteArray(), music);
    }

    private static int ticks(double seconds, int tempo) {
        double tickPerS = (tempo / 60.0) * PPQ;
        return (int) Py.round(tickPerS * seconds);
    }

    private static int[] openChannels() {
        int[] open = new int[15];
        int n = 0;
        for (int channel = 1; channel <= 16; channel++) {
            if (channel != 10) {
                open[n++] = channel;
            }
        }
        return open;
    }

    private static byte[] program(int channel, int program) {
        return new byte[] {(byte) (0xC0 | (channel - 1)), (byte) program};
    }

    private static byte[] control(int channel, int controller, int value) {
        return new byte[] {(byte) (0xB0 | (channel - 1)), (byte) controller, (byte) value};
    }

    private static byte[] meta(int type, String text) {
        byte[] body = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] len = varint(body.length);
        byte[] event = new byte[2 + len.length + body.length];
        event[0] = (byte) 0xFF;
        event[1] = (byte) type;
        System.arraycopy(len, 0, event, 2, len.length);
        System.arraycopy(body, 0, event, 2 + len.length, body.length);
        return event;
    }

    private static byte[] tempoEvent(int tempo) {
        byte[] micros = intTo3Byte((int) (6_0000_000.0 / tempo));
        return new byte[] {(byte) 0xFF, 0x51, 0x03, micros[0], micros[1], micros[2]};
    }

    private static void writeTrack(Buf out, Part part) {
        out.write("MTrk");
        int sizeAt = out.size();
        out.write(intToLong(0));
        int dataAt = out.size();
        int last = 0;
        for (var entry : part.at.entrySet()) {
            int delta = Math.max(0, entry.getKey() - last);
            for (byte[] event : entry.getValue()) {
                out.write(varint(delta));
                out.write(event);
                delta = 0;
            }
            last = entry.getKey();
        }
        out.write(varint(0));
        out.write(new byte[] {(byte) 0xFF, 0x2F, 0x00});
        out.poke(sizeAt, intToLong(out.size() - dataAt));
    }

    private static byte[] intToWord(int value) {
        return new byte[] {(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    private static byte[] intToLong(int value) {
        return new byte[] {
                (byte) ((value >> 24) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    private static byte[] intTo3Byte(int value) {
        byte[] full = intToLong(value);
        return new byte[] {full[1], full[2], full[3]};
    }

    private static byte[] varint(int value) {
        byte[] tail = new byte[] {(byte) (value & 0x7F)};
        int rest = value >>> 7;
        while (rest != 0) {
            byte[] next = new byte[tail.length + 1];
            next[0] = (byte) ((rest & 0x7F) | 0x80);
            System.arraycopy(tail, 0, next, 1, tail.length);
            tail = next;
            rest >>>= 7;
        }
        return tail;
    }

    private static final class Part {
        private final TreeMap<Integer, List<byte[]>> at = new TreeMap<>();

        void add(int tick, byte[] event) {
            at.computeIfAbsent(Math.max(0, tick), key -> new ArrayList<>()).add(event);
        }

        int times() {
            return at.size();
        }
    }

    private static final class Buf {
        private byte[] data = new byte[256];
        private int size;

        int size() {
            return size;
        }

        void write(String text) {
            write(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }

        void write(byte[] bytes) {
            if (size + bytes.length > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, size + bytes.length));
            }
            System.arraycopy(bytes, 0, data, size, bytes.length);
            size += bytes.length;
        }

        void poke(int at, byte[] bytes) {
            System.arraycopy(bytes, 0, data, at, bytes.length);
        }

        byte[] toByteArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
