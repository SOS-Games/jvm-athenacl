// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.loudness;

import io.github.sosgames.athenacl.events.TempoMap;
import io.github.sosgames.athenacl.render.WavRenderer;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trims a rendered WAV so each state's integrated loudness sits on a contour.
 * One render is sliced with the same integer tempo math as the WAV writer, then a
 * per-state gain is applied. The event log is left alone, so the musical hash does not move.
 */
public final class StateLoudness {
    private static final int RATE = 44100;
    private static final int CHANNELS = 2;
    private static final double SAMPLE_CEILING = 0.70;

    private StateLoudness() {
    }

    public record Span(int startFrame, int endFrame, String state) {
    }

    public static byte[] fit(byte[] wav, int bpm, long barTicks, List<String> stateByBar,
                              double reference, Map<String, Double> offsets) throws IOException {
        if (stateByBar.isEmpty() || offsets == null || offsets.isEmpty()) {
            return wav;
        }
        short[] samples = decode(wav);
        List<Span> spans = spans(samples.length / CHANNELS, bpm, barTicks, stateByBar);
        for (int pass = 0; pass < 3; pass++) {
            Map<String, Double> measured = integrated(samples, spans);
            boolean close = true;
            Map<String, Double> gains = new LinkedHashMap<>();
            for (String state : order(spans)) {
                double lufs = measured.getOrDefault(state, Double.NEGATIVE_INFINITY);
                if (!Double.isFinite(lufs)) {
                    close = false;
                    continue;
                }
                double error = reference + offsets.getOrDefault(state, 0.0) - lufs;
                if (Math.abs(error) > 0.25) {
                    close = false;
                }
                double gain = Math.pow(10.0, error / 20.0);
                gain = Math.min(gain, headroom(samples, spans, state));
                if (Math.abs(gain - 1.0) > 0.001) {
                    gains.put(state, gain);
                }
            }
            if (close || gains.isEmpty()) {
                break;
            }
            apply(samples, spans, gains);
        }
        return encode(samples);
    }

    /**
     * How many LU the overlay bars sit above the same state's other bars.
     * Null when a state never has both. The gap is a power ratio of those slices.
     */
    public static Double overlayDelta(byte[] wav, int bpm, long barTicks, List<String> stateByBar,
                                      List<Boolean> overlayByBar) throws IOException {
        if (stateByBar.isEmpty() || overlayByBar.size() != stateByBar.size()) {
            return null;
        }
        short[] samples = decode(wav);
        int frames = samples.length / CHANNELS;
        TempoMap map = new TempoMap(bpm);
        Map<String, double[]> energy = new LinkedHashMap<>();
        for (int bar = 0; bar < stateByBar.size(); bar++) {
            String state = stateByBar.get(bar);
            if (state == null || state.isBlank()) {
                continue;
            }
            int start = Math.max(0, Math.min(frames, frameAt(map, bar * barTicks)));
            int end = bar == stateByBar.size() - 1
                    ? frames
                    : Math.max(start, Math.min(frames, frameAt(map, (bar + 1L) * barTicks)));
            double sum = 0;
            int count = 0;
            for (int i = start * CHANNELS; i < end * CHANNELS; i++) {
                double sample = samples[i] / 32768.0;
                sum += sample * sample;
                count++;
            }
            double[] pair = energy.computeIfAbsent(state, key -> new double[4]);
            int side = Boolean.TRUE.equals(overlayByBar.get(bar)) ? 2 : 0;
            pair[side] += sum;
            pair[side + 1] += count;
        }
        Double widest = null;
        for (double[] pair : energy.values()) {
            if (pair[1] <= 0 || pair[3] <= 0 || pair[0] <= 0 || pair[2] <= 0) {
                continue;
            }
            double delta = 10.0 * Math.log10((pair[2] / pair[3]) / (pair[0] / pair[1]));
            if (widest == null || Math.abs(delta) > Math.abs(widest)) {
                widest = delta;
            }
        }
        return widest;
    }

    /** Integrated loudness minus {@code reference}, in first-heard state order. */
    public static Map<String, Double> offsets(byte[] wav, int bpm, long barTicks, List<String> stateByBar,
                                               double reference) throws IOException {
        short[] samples = decode(wav);
        List<Span> spans = spans(samples.length / CHANNELS, bpm, barTicks, stateByBar);
        Map<String, Double> measured = integrated(samples, spans);
        Map<String, Double> result = new LinkedHashMap<>();
        for (String state : order(spans)) {
            double lufs = measured.getOrDefault(state, Double.NEGATIVE_INFINITY);
            result.put(state, lufs - reference);
        }
        return result;
    }

    private static List<String> order(List<Span> spans) {
        List<String> names = new ArrayList<>();
        for (Span span : spans) {
            if (!names.contains(span.state())) {
                names.add(span.state());
            }
        }
        return names;
    }

    static List<Span> spans(int frames, int bpm, long barTicks, List<String> stateByBar) {
        TempoMap map = new TempoMap(bpm);
        List<Span> spans = new ArrayList<>();
        int cursor = 0;
        while (cursor < stateByBar.size()) {
            String state = stateByBar.get(cursor);
            int last = cursor;
            while (last + 1 < stateByBar.size() && state.equals(stateByBar.get(last + 1))) {
                last++;
            }
            int start = frameAt(map, cursor * barTicks);
            int end = last == stateByBar.size() - 1 ? frames : frameAt(map, (last + 1L) * barTicks);
            start = Math.max(0, Math.min(start, frames));
            end = Math.max(start, Math.min(end, frames));
            if (end > start) {
                spans.add(new Span(start, end, state));
            }
            cursor = last + 1;
        }
        return spans;
    }

    private static int frameAt(TempoMap map, long tick) {
        return (int) (map.toMicros(tick) * RATE / 1_000_000L);
    }

    private static Map<String, Double> integrated(short[] samples, List<Span> spans) throws IOException {
        Map<String, Double> measured = new LinkedHashMap<>();
        for (String state : order(spans)) {
            int length = 0;
            for (Span span : spans) {
                if (state.equals(span.state())) {
                    length += span.endFrame() - span.startFrame();
                }
            }
            short[] slice = new short[length * CHANNELS];
            int dest = 0;
            for (Span span : spans) {
                if (!state.equals(span.state())) {
                    continue;
                }
                int from = span.startFrame() * CHANNELS;
                int count = (span.endFrame() - span.startFrame()) * CHANNELS;
                System.arraycopy(samples, from, slice, dest, count);
                dest += count;
            }
            measured.put(state, LoudnessMeter.measure(slice, CHANNELS, RATE).integrated());
        }
        return measured;
    }

    private static double headroom(short[] samples, List<Span> spans, String state) {
        int peak = 1;
        for (Span span : spans) {
            if (!state.equals(span.state())) {
                continue;
            }
            int from = span.startFrame() * CHANNELS;
            int to = span.endFrame() * CHANNELS;
            for (int i = from; i < to; i++) {
                peak = Math.max(peak, Math.abs(samples[i]));
            }
        }
        return SAMPLE_CEILING * 32767.0 / peak;
    }

    private static void apply(short[] samples, List<Span> spans, Map<String, Double> gains) {
        for (Span span : spans) {
            Double gain = gains.get(span.state());
            if (gain == null) {
                continue;
            }
            int from = span.startFrame() * CHANNELS;
            int to = span.endFrame() * CHANNELS;
            for (int i = from; i < to; i++) {
                int scaled = (int) Math.round(samples[i] * gain);
                if (scaled > 32767) {
                    scaled = 32767;
                } else if (scaled < -32768) {
                    scaled = -32768;
                }
                samples[i] = (short) scaled;
            }
        }
    }

    private static short[] decode(byte[] wav) throws IOException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            byte[] raw = in.readAllBytes();
            int frame = Math.max(1, in.getFormat().getFrameSize());
            int frames = raw.length / frame;
            short[] samples = new short[frames * CHANNELS];
            boolean big = in.getFormat().isBigEndian();
            int channels = in.getFormat().getChannels();
            for (int i = 0; i < frames; i++) {
                for (int c = 0; c < CHANNELS; c++) {
                    int source = Math.min(c, channels - 1);
                    int index = i * frame + source * 2;
                    int lo = raw[index] & 0xff;
                    int hi = raw[index + 1];
                    int value = big ? ((raw[index] << 8) | (raw[index + 1] & 0xff)) : (hi << 8) | lo;
                    samples[i * CHANNELS + c] = (short) value;
                }
            }
            return samples;
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
    }

    private static byte[] encode(short[] samples) throws IOException {
        int frames = samples.length / CHANNELS;
        byte[] pcm = new byte[frames * WavRenderer.FORMAT.getFrameSize()];
        for (int i = 0; i < samples.length; i++) {
            pcm[i * 2] = (byte) samples[i];
            pcm[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), WavRenderer.FORMAT, frames),
                javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }
}
