// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.loudness;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EBU R128 loudness for a PCM file. Integrated loudness uses 400 ms blocks stepped by 100 ms,
 * an absolute gate at -70 LUFS, then a relative gate 10 LU below that. Short-term is the last
 * full 3 second window. True peak is a 4× oversampled peak. Integrated loudness is meant to
 * stay within 0.2 LU of ffmpeg's {@code ebur128} filter on the same WAV.
 */
public final class LoudnessMeter {
    private static final double ABSOLUTE_ENERGY = Math.pow(10.0, (-70.0 + 0.691) / 10.0);
    private static final double RELATIVE_GATE = Math.pow(10.0, -10.0 / 10.0);

    private LoudnessMeter() {
    }

    public record Reading(double integrated, double shortTerm, double truePeak) {
        public String line() {
            return "loudness.integrated=" + tenths(integrated)
                    + " LUFS truePeak=" + tenths(truePeak)
                    + " dBTP shortTerm=" + tenths(shortTerm) + " LUFS";
        }

        private static String tenths(double value) {
            if (Double.isInfinite(value)) {
                return "-inf";
            }
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }

    public static Reading measure(Path wav) throws IOException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
            AudioFormat source = in.getFormat();
            AudioFormat target = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
            AudioInputStream pcm = AudioSystem.isConversionSupported(target, source)
                    ? AudioSystem.getAudioInputStream(target, in)
                    : in;
            byte[] bytes = pcm.readAllBytes();
            int channels = pcm.getFormat().getChannels();
            int rate = Math.round(pcm.getFormat().getSampleRate());
            int frame = channels * 2;
            int frames = bytes.length / frame;
            short[] interleaved = new short[frames * channels];
            for (int i = 0; i < frames; i++) {
                for (int c = 0; c < channels; c++) {
                    int at = (i * channels + c) * 2;
                    int lo = bytes[at] & 0xFF;
                    int hi = bytes[at + 1];
                    interleaved[i * channels + c] = (short) (lo | (hi << 8));
                }
            }
            return measure(interleaved, channels, rate);
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException(e);
        }
    }

    public static Reading measure(short[] interleaved, int channels, int rate) {
        int frames = interleaved.length / channels;
        double[] b = new double[5];
        double[] a = new double[5];
        coefficients(rate, b, a);
        double[][] filtered = new double[channels][frames];
        double[][] state = new double[channels][5];
        for (int i = 0; i < frames; i++) {
            for (int c = 0; c < channels; c++) {
                double x = interleaved[i * channels + c] / 32767.0;
                double v0 = x - a[1] * state[c][1] - a[2] * state[c][2] - a[3] * state[c][3] - a[4] * state[c][4];
                filtered[c][i] = b[0] * v0 + b[1] * state[c][1] + b[2] * state[c][2]
                        + b[3] * state[c][3] + b[4] * state[c][4];
                state[c][4] = state[c][3];
                state[c][3] = state[c][2];
                state[c][2] = state[c][1];
                state[c][1] = v0;
            }
        }
        int hop = (rate + 5) / 10;
        int block = hop * 4;
        List<Double> gated = new ArrayList<>();
        for (int start = 0; start + block <= frames; start += hop) {
            double energy = energy(filtered, start, block);
            if (energy >= ABSOLUTE_ENERGY) {
                gated.add(energy);
            }
        }
        double integrated = loudness(gatedMean(gated));
        int shortFrames = hop * 30;
        double shortEnergy = frames == 0 ? 0 : energy(filtered, Math.max(0, frames - Math.min(frames, shortFrames)),
                Math.min(frames, shortFrames));
        int end = shortFrames;
        while (end <= frames) {
            shortEnergy = energy(filtered, end - shortFrames, shortFrames);
            end += hop * 10;
        }
        double peak = truePeak(interleaved, channels);
        double truePeakDb = peak <= 0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(peak);
        return new Reading(integrated, toLoudness(shortEnergy), truePeakDb);
    }

    private static double gatedMean(List<Double> absolute) {
        if (absolute.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double energy : absolute) {
            sum += energy;
        }
        double relative = (sum / absolute.size()) * RELATIVE_GATE;
        double kept = 0;
        int count = 0;
        for (double energy : absolute) {
            if (energy >= relative) {
                kept += energy;
                count++;
            }
        }
        return count == 0 ? 0 : kept / count;
    }

    private static double energy(double[][] filtered, int start, int length) {
        double sum = 0;
        for (double[] channel : filtered) {
            double channelSum = 0;
            for (int i = 0; i < length; i++) {
                double sample = channel[start + i];
                channelSum += sample * sample;
            }
            sum += channelSum;
        }
        return sum / length;
    }

    private static double loudness(double energy) {
        return energy <= 0 ? Double.NEGATIVE_INFINITY : toLoudness(energy);
    }

    private static double toLoudness(double energy) {
        return energy <= 0 ? Double.NEGATIVE_INFINITY : 10.0 * Math.log10(energy) - 0.691;
    }

    private static void coefficients(int rate, double[] b, double[] a) {
        double f0 = 1681.974450955533;
        double gain = 3.999843853973347;
        double q = 0.7071752369554196;
        double k = Math.tan(Math.PI * f0 / rate);
        double vh = Math.pow(10.0, gain / 20.0);
        double vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1.0 + k / q + k * k;
        double[] pb = new double[3];
        double[] pa = new double[] {1.0, 0, 0};
        pb[0] = (vh + vb * k / q + k * k) / a0;
        pb[1] = 2.0 * (k * k - vh) / a0;
        pb[2] = (vh - vb * k / q + k * k) / a0;
        pa[1] = 2.0 * (k * k - 1.0) / a0;
        pa[2] = (1.0 - k / q + k * k) / a0;

        f0 = 38.13547087602444;
        q = 0.5003270373238773;
        k = Math.tan(Math.PI * f0 / rate);
        double[] rb = new double[] {1.0, -2.0, 1.0};
        double[] ra = new double[] {1.0, 0, 0};
        ra[1] = 2.0 * (k * k - 1.0) / (1.0 + k / q + k * k);
        ra[2] = (1.0 - k / q + k * k) / (1.0 + k / q + k * k);

        b[0] = pb[0] * rb[0];
        b[1] = pb[0] * rb[1] + pb[1] * rb[0];
        b[2] = pb[0] * rb[2] + pb[1] * rb[1] + pb[2] * rb[0];
        b[3] = pb[1] * rb[2] + pb[2] * rb[1];
        b[4] = pb[2] * rb[2];
        a[0] = pa[0] * ra[0];
        a[1] = pa[0] * ra[1] + pa[1] * ra[0];
        a[2] = pa[0] * ra[2] + pa[1] * ra[1] + pa[2] * ra[0];
        a[3] = pa[1] * ra[2] + pa[2] * ra[1];
        a[4] = pa[2] * ra[2];
    }

    private static double truePeak(short[] interleaved, int channels) {
        int taps = 49;
        int factor = 4;
        int delay = (taps + factor - 1) / factor;
        double[][] coeff = new double[factor][delay];
        int[] counts = new int[factor];
        for (int j = 0; j < taps; j++) {
            double m = j - (taps - 1) / 2.0;
            double c = 1.0;
            if (Math.abs(m) > 0.000001) {
                c = Math.sin(m * Math.PI / factor) / (m * Math.PI / factor);
            }
            c *= 0.5 * (1 - Math.cos(2 * Math.PI * j / (taps - 1)));
            if (Math.abs(c) > 0.000001) {
                int phase = j % factor;
                int slot = counts[phase]++;
                coeff[phase][slot] = c;
            }
        }
        int[] index = new int[taps];
        int[][] phaseIndex = new int[factor][delay];
        int[] filled = new int[factor];
        for (int j = 0; j < taps; j++) {
            double m = j - (taps - 1) / 2.0;
            double c = 1.0;
            if (Math.abs(m) > 0.000001) {
                c = Math.sin(m * Math.PI / factor) / (m * Math.PI / factor);
            }
            c *= 0.5 * (1 - Math.cos(2 * Math.PI * j / (taps - 1)));
            if (Math.abs(c) > 0.000001) {
                int phase = j % factor;
                int slot = filled[phase]++;
                phaseIndex[phase][slot] = j / factor;
                index[j] = slot;
            }
        }
        float[][] z = new float[channels][delay];
        int zi = 0;
        double peak = 0;
        int frames = interleaved.length / channels;
        for (int frame = 0; frame < frames; frame++) {
            for (int c = 0; c < channels; c++) {
                z[c][zi] = interleaved[frame * channels + c] / 32767.0f;
                for (int phase = 0; phase < factor; phase++) {
                    double acc = 0;
                    for (int t = 0; t < counts[phase]; t++) {
                        int i = zi - phaseIndex[phase][t];
                        if (i < 0) {
                            i += delay;
                        }
                        acc += z[c][i] * coeff[phase][t];
                    }
                    peak = Math.max(peak, Math.abs(acc));
                }
            }
            zi++;
            if (zi == delay) {
                zi = 0;
            }
        }
        return peak;
    }
}
