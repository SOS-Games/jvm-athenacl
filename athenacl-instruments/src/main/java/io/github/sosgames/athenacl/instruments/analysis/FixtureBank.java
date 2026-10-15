// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.events.EventLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the CC0 fixture soundfont used by {@code kit analyze}.
 * Seven presets: a band-limited saw, inharmonic partials, a hard-clipped sine,
 * a looping sine with a bad join, a noise burst, a slow pad, and a pluck.
 * The file is the oracle. Nothing here is copied from a commercial bank.
 */
public final class FixtureBank {
    public static final int RATE = 48000;
    public static final double C4 = 440.0 * Math.pow(2.0, (60 - 69) / 12.0);
    private static final int TAIL = 46;

    private FixtureBank() {
    }

    public static String write(Path path) throws IOException {
        List<Tone> tones = List.of(
                tone("saw", 0, saw(), false, -12000, -12000, 0, -3600),
                tone("inharmonic", 1, inharmonic(), false, -12000, -12000, 0, -3600),
                tone("clipped", 2, clipped(), false, -12000, -12000, 0, -3600),
                tone("loopclick", 3, loopClick(), true, -12000, -12000, 0, -3600),
                tone("noise", 4, noise(), false, -12000, -12000, 0, -12000),
                tone("pad", 5, sine(2.6, 0.5), false, -1200, -12000, 0, -1200),
                tone("pluck", 6, pluck(), false, -12000, -2400, 1000, -12000));
        byte[] riff = riff(tones);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.write(path, riff);
        return EventLog.sha256(riff);
    }

    private static Tone tone(String name, int program, short[] data, boolean loop,
                             int attack, int decay, int sustain, int release) {
        return new Tone(name, program, data, loop, attack, decay, sustain, release);
    }

    private static short[] saw() {
        int n = seconds(2.6);
        double[] x = new double[n];
        for (int k = 1; k <= 48; k++) {
            double amp = 1.0 / k;
            for (int i = 0; i < n; i++) {
                x[i] += amp * Math.sin(2.0 * Math.PI * C4 * k * i / RATE);
            }
        }
        return scale(x, 0.35);
    }

    private static short[] inharmonic() {
        int n = seconds(2.6);
        double[] ratios = {1.0, 2.47, 3.91, 5.33, 7.10};
        double[] amps = {1.0, 0.85, 0.65, 0.45, 0.30};
        double[] x = new double[n];
        for (int k = 0; k < ratios.length; k++) {
            for (int i = 0; i < n; i++) {
                x[i] += amps[k] * Math.sin(2.0 * Math.PI * C4 * ratios[k] * i / RATE);
            }
        }
        return scale(x, 0.45);
    }

    private static short[] clipped() {
        int n = seconds(2.6);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double s = Math.sin(2.0 * Math.PI * C4 * i / RATE) * 8.0;
            if (s > 1.0) {
                s = 1.0;
            } else if (s < -1.0) {
                s = -1.0;
            }
            out[i] = (short) Math.round(s * 32767.0);
        }
        return out;
    }

    private static short[] loopClick() {
        int n = 400;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) Math.round(Math.sin(2.0 * Math.PI * C4 * i / RATE) * 16000.0);
        }
        out[0] = 20000;
        out[n - 1] = -20000;
        return out;
    }

    private static short[] noise() {
        int n = seconds(0.35);
        short[] out = new short[n];
        int state = 0xA3C59AC3;
        for (int i = 0; i < n; i++) {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            double v = ((state >>> 1) & 0xFFFF) / 32768.0 - 1.0;
            out[i] = (short) Math.round(v * 6000.0);
        }
        return out;
    }

    private static short[] sine(double seconds, double peak) {
        int n = seconds(seconds);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = Math.sin(2.0 * Math.PI * C4 * i / RATE);
        }
        return scale(x, peak);
    }

    private static short[] pluck() {
        int n = seconds(2.6);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-8.0 * i / RATE);
            x[i] = Math.sin(2.0 * Math.PI * C4 * i / RATE) * env;
        }
        return scale(x, 0.5);
    }

    private static int seconds(double seconds) {
        return (int) Math.round(seconds * RATE);
    }

    private static short[] scale(double[] x, double peak) {
        double max = 1e-9;
        for (double v : x) {
            max = Math.max(max, Math.abs(v));
        }
        double gain = peak / max;
        short[] out = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            int s = (int) Math.round(x[i] * gain * 32767.0);
            if (s > 32767) {
                s = 32767;
            } else if (s < -32768) {
                s = -32768;
            }
            out[i] = (short) s;
        }
        return out;
    }

    private static byte[] riff(List<Tone> tones) {
        Bin samples = new Bin();
        List<Placed> placed = new ArrayList<>();
        int cursor = 0;
        for (Tone tone : tones) {
            int start = cursor;
            for (short sample : tone.data) {
                samples.i16(sample);
            }
            int end = start + tone.data.length;
            for (int i = 0; i < TAIL; i++) {
                samples.i16(0);
            }
            int loopStart = start;
            int loopEnd = tone.loop ? end : start + 8;
            placed.add(new Placed(tone.name, start, end, loopStart, loopEnd));
            cursor = end + TAIL;
        }
        byte[] info = list("INFO",
                chunk("ifil", new Bin().u16(2).u16(1).toByteArray()),
                chunk("isng", cString("EMU8000")),
                chunk("INAM", cString("fixtures")));
        byte[] sdta = list("sdta", chunk("smpl", samples.toByteArray()));
        byte[] pdta = list("pdta",
                phdr(tones), pbag(tones.size(), 1), pmod(), pgen(tones.size()),
                inst(tones), ibag(tones.size(), 9), imod(), igen(tones), shdr(placed));
        Bin body = new Bin();
        body.s("sfbk");
        body.bytes(info);
        body.bytes(sdta);
        body.bytes(pdta);
        return chunk("RIFF", body.toByteArray());
    }

    private static byte[] phdr(List<Tone> tones) {
        Bin bin = new Bin();
        for (int i = 0; i < tones.size(); i++) {
            Tone tone = tones.get(i);
            bin.str20(tone.name);
            bin.u16(tone.program);
            bin.u16(0);
            bin.u16(i);
            bin.u32(0);
            bin.u32(0);
            bin.u32(0);
        }
        bin.str20("EOP");
        bin.u16(0);
        bin.u16(0);
        bin.u16(tones.size());
        bin.u32(0);
        bin.u32(0);
        bin.u32(0);
        return chunk("phdr", bin.toByteArray());
    }

    private static byte[] pbag(int zones, int gensEach) {
        Bin bin = new Bin();
        for (int i = 0; i <= zones; i++) {
            bin.u16(i * gensEach);
            bin.u16(0);
        }
        return chunk("pbag", bin.toByteArray());
    }

    private static byte[] pmod() {
        Bin bin = new Bin();
        bin.u16(0);
        bin.u16(0);
        bin.i16(0);
        bin.u16(0);
        bin.u16(0);
        return chunk("pmod", bin.toByteArray());
    }

    private static byte[] pgen(int zones) {
        Bin bin = new Bin();
        for (int i = 0; i < zones; i++) {
            gen(bin, 41, i);
        }
        gen(bin, 0, 0);
        return chunk("pgen", bin.toByteArray());
    }

    private static byte[] inst(List<Tone> tones) {
        Bin bin = new Bin();
        for (int i = 0; i < tones.size(); i++) {
            bin.str20(tones.get(i).name);
            bin.u16(i);
        }
        bin.str20("EOI");
        bin.u16(tones.size());
        return chunk("inst", bin.toByteArray());
    }

    private static byte[] ibag(int zones, int gensEach) {
        return pbag(zones, gensEach).length == 0 ? new byte[0] : rename(pbag(zones, gensEach), "ibag");
    }

    private static byte[] imod() {
        return rename(pmod(), "imod");
    }

    private static byte[] igen(List<Tone> tones) {
        Bin bin = new Bin();
        for (int i = 0; i < tones.size(); i++) {
            Tone tone = tones.get(i);
            gen(bin, 43, (127 << 8));
            gen(bin, 58, 60);
            gen(bin, 48, 0);
            gen(bin, 34, tone.attack);
            gen(bin, 36, tone.decay);
            gen(bin, 37, tone.sustain);
            gen(bin, 38, tone.release);
            gen(bin, 54, tone.loop ? 1 : 0);
            gen(bin, 53, i);
        }
        gen(bin, 0, 0);
        return chunk("igen", bin.toByteArray());
    }

    private static byte[] shdr(List<Placed> placed) {
        Bin bin = new Bin();
        for (Placed sample : placed) {
            bin.str20(sample.name);
            bin.u32(sample.start);
            bin.u32(sample.end);
            bin.u32(sample.loopStart);
            bin.u32(sample.loopEnd);
            bin.u32(RATE);
            bin.u8(60);
            bin.i8(0);
            bin.u16(0);
            bin.u16(1);
        }
        bin.str20("EOS");
        bin.u32(0);
        bin.u32(0);
        bin.u32(0);
        bin.u32(0);
        bin.u32(0);
        bin.u8(0);
        bin.i8(0);
        bin.u16(0);
        bin.u16(0);
        return chunk("shdr", bin.toByteArray());
    }

    private static void gen(Bin bin, int oper, int amount) {
        bin.u16(oper);
        bin.i16(amount);
    }

    private static byte[] rename(byte[] chunk, String id) {
        byte[] copy = chunk.clone();
        byte[] name = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, copy, 0, 4);
        return copy;
    }

    private static byte[] cString(String text) {
        byte[] raw = text.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[raw.length + 1];
        System.arraycopy(raw, 0, out, 0, raw.length);
        if ((out.length & 1) != 0) {
            byte[] even = new byte[out.length + 1];
            System.arraycopy(out, 0, even, 0, out.length);
            return even;
        }
        return out;
    }

    private static byte[] list(String type, byte[]... chunks) {
        Bin inner = new Bin();
        inner.s(type);
        for (byte[] chunk : chunks) {
            inner.bytes(chunk);
        }
        return chunk("LIST", inner.toByteArray());
    }

    private static byte[] chunk(String id, byte[] data) {
        Bin bin = new Bin();
        bin.s(id);
        bin.u32(data.length);
        bin.bytes(data);
        if ((data.length & 1) != 0) {
            bin.u8(0);
        }
        return bin.toByteArray();
    }

    private record Tone(String name, int program, short[] data, boolean loop,
                         int attack, int decay, int sustain, int release) {
    }

    private record Placed(String name, int start, int end, int loopStart, int loopEnd) {
    }

    private static final class Bin {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Bin s(String fourcc) {
            for (int i = 0; i < 4; i++) {
                out.write(fourcc.charAt(i));
            }
            return this;
        }

        Bin u32(int v) {
            out.write(v);
            out.write(v >>> 8);
            out.write(v >>> 16);
            out.write(v >>> 24);
            return this;
        }

        Bin u16(int v) {
            out.write(v);
            out.write(v >>> 8);
            return this;
        }

        Bin i16(int v) {
            return u16(v);
        }

        Bin u8(int v) {
            out.write(v);
            return this;
        }

        Bin i8(int v) {
            return u8(v);
        }

        Bin str20(String name) {
            byte[] raw = name.getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < 20; i++) {
                out.write(i < raw.length ? raw[i] : 0);
            }
            return this;
        }

        Bin bytes(byte[] data) {
            out.writeBytes(data);
            return this;
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
