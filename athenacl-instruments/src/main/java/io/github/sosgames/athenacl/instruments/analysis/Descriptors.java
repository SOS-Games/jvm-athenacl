// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.render.loudness.LoudnessMeter;

/**
 * Measurements on one dry probe. Pitch uses YIN on the loudest stretch.
 * Partials come from one Fourier transform of that same stretch.
 * A loop click is a sample-to-sample jump many times the local spread.
 */
public final class Descriptors {
    private Descriptors() {
    }

    public record Measurement(
            double pitchErrorCents,
            double inharmonicEnergy,
            double centroidRatio,
            double flatness,
            double oddEven,
            double attackMs,
            double decayMs,
            boolean died,
            double peakDbfs,
            int clipCount,
            int loopClicks,
            double loudnessLu,
            double roughness) {
    }

    public static Measurement measure(float[] samples, int rate, double nominalHz,
                                      double clickSigma, double clipDbfs, double inharmonicCents) {
        double peak = 0;
        int clips = 0;
        double clipLimit = Math.pow(10.0, clipDbfs / 20.0);
        for (float sample : samples) {
            double abs = Math.abs(sample);
            if (abs > peak) {
                peak = abs;
            }
            if (abs >= clipLimit) {
                clips++;
            }
        }
        double peakDb = peak <= 1e-9 ? -120 : 20.0 * Math.log10(peak);
        int win = 16384;
        int at = loudest(samples, Math.min(win, samples.length));
        int used = Math.min(win, samples.length - at);
        double[] re = new double[nextPow2(Math.max(used, 16))];
        double[] im = new double[re.length];
        for (int i = 0; i < used; i++) {
            double hann = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / Math.max(1, used - 1));
            re[i] = samples[at + i] * hann;
        }
        fft(re, im);
        double f0 = yin(samples, at, at + used, rate, nominalHz);
        double cents = Double.isFinite(f0) && f0 > 0 && nominalHz > 0
                ? 1200.0 * Math.log(f0 / nominalHz) / Math.log(2.0)
                : 0;
        Spectrum spectrum = spectrum(re, im, rate, nominalHz, inharmonicCents);
        Envelope envelope = envelope(samples, rate);
        int clicks = clicks(samples, at, at + used, clickSigma);
        double loudness = loudness(samples, rate);
        return new Measurement(cents, spectrum.inharmonic, spectrum.centroidRatio, spectrum.flatness,
                spectrum.oddEven, envelope.attackMs, envelope.decayMs, envelope.died, peakDb, clips, clicks,
                loudness, spectrum.roughness);
    }

    private static int loudest(float[] samples, int win) {
        if (samples.length <= win) {
            return 0;
        }
        int step = Math.max(1, win / 8);
        int best = 0;
        double bestEnergy = -1;
        for (int i = 0; i + win <= samples.length; i += step) {
            double energy = 0;
            for (int n = 0; n < win; n += 8) {
                float s = samples[i + n];
                energy += s * (double) s;
            }
            if (energy > bestEnergy) {
                bestEnergy = energy;
                best = i;
            }
        }
        return best;
    }

    private static double yin(float[] samples, int start, int end, int rate, double nominalHz) {
        int n = end - start;
        if (n < 64 || nominalHz <= 0) {
            return nominalHz;
        }
        int minLag = Math.max(2, (int) (rate / 2000.0));
        int maxLag = Math.min(n / 2 - 1, (int) (rate / 40.0));
        if (maxLag <= minLag) {
            return nominalHz;
        }
        double[] diff = new double[maxLag + 1];
        for (int tau = 1; tau <= maxLag; tau++) {
            double sum = 0;
            int count = n - tau;
            for (int i = 0; i < count; i++) {
                double delta = samples[start + i] - samples[start + i + tau];
                sum += delta * delta;
            }
            diff[tau] = sum;
        }
        double[] cmnd = new double[maxLag + 1];
        cmnd[0] = 1;
        double running = 0;
        for (int tau = 1; tau <= maxLag; tau++) {
            running += diff[tau];
            cmnd[tau] = running == 0 ? 1 : diff[tau] * tau / running;
        }
        int best = -1;
        for (int tau = minLag; tau <= maxLag; tau++) {
            if (cmnd[tau] < 0.15) {
                while (tau + 1 <= maxLag && cmnd[tau + 1] < cmnd[tau]) {
                    tau++;
                }
                best = tau;
                break;
            }
        }
        if (best < 0) {
            best = minLag;
            for (int tau = minLag + 1; tau <= maxLag; tau++) {
                if (cmnd[tau] < cmnd[best]) {
                    best = tau;
                }
            }
            if (cmnd[best] > 0.5) {
                return nominalHz;
            }
        }
        double tau = best;
        if (best > minLag && best < maxLag) {
            double prev = cmnd[best - 1];
            double mid = cmnd[best];
            double next = cmnd[best + 1];
            double denom = 2.0 * (prev - 2.0 * mid + next);
            if (denom != 0) {
                tau = best + (prev - next) / denom;
            }
        }
        return tau <= 0 ? nominalHz : rate / tau;
    }

    private record Spectrum(double inharmonic, double centroidRatio, double flatness,
                             double oddEven, double roughness) {
    }

    private static Spectrum spectrum(double[] re, double[] im, int rate, double nominalHz, double inharmonicCents) {
        int n = re.length;
        int bins = n / 2;
        double[] mag = new double[bins];
        double max = 0;
        for (int i = 1; i < bins; i++) {
            mag[i] = Math.hypot(re[i], im[i]);
            if (mag[i] > max) {
                max = mag[i];
            }
        }
        double floor = max * 0.05;
        double centroidNum = 0;
        double centroidDen = 0;
        double logSum = 0;
        double powerSum = 0;
        int counted = 0;
        double inharmonic = 0;
        double harmonic = 0;
        double odd = 0;
        double even = 0;
        double[] peakHz = new double[64];
        double[] peakAmp = new double[64];
        int peaks = 0;
        for (int i = 2; i < bins - 1; i++) {
            double hz = i * (double) rate / n;
            if (hz < 40 || hz > 12000) {
                continue;
            }
            double p = mag[i] * mag[i] + 1e-20;
            logSum += Math.log(p);
            powerSum += p;
            counted++;
            centroidNum += hz * mag[i];
            centroidDen += mag[i];
            if (mag[i] >= floor && mag[i] >= mag[i - 1] && mag[i] >= mag[i + 1] && peaks < peakHz.length) {
                double freq = hz;
                peakHz[peaks] = freq;
                peakAmp[peaks] = mag[i];
                peaks++;
                if (nominalHz > 0) {
                    double ratio = freq / nominalHz;
                    double nearest = Math.max(1, Math.round(ratio));
                    double cents = Math.abs(1200.0 * Math.log(freq / (nearest * nominalHz)) / Math.log(2.0));
                    double power = mag[i] * mag[i];
                    if (cents > inharmonicCents) {
                        inharmonic += power;
                    } else {
                        harmonic += power;
                        if (((int) nearest) % 2 == 0) {
                            even += power;
                        } else {
                            odd += power;
                        }
                    }
                }
            }
        }
        double total = inharmonic + harmonic;
        double inharmonicRatio = total <= 0 ? 0 : inharmonic / total;
        double centroidRatio = centroidDen <= 0 || nominalHz <= 0 ? 0 : (centroidNum / centroidDen) / nominalHz;
        double flatness = 0;
        if (counted > 0 && powerSum > 0) {
            flatness = Math.exp(logSum / counted) / (powerSum / counted);
        }
        double oddEven = even <= 0 ? 0 : odd / even;
        return new Spectrum(inharmonicRatio, centroidRatio, flatness, oddEven, roughness(peakHz, peakAmp, peaks));
    }

    private static double roughness(double[] hz, double[] amp, int peaks) {
        double sum = 0;
        for (int i = 0; i < peaks; i++) {
            for (int j = i + 1; j < peaks; j++) {
                double fmin = Math.min(hz[i], hz[j]);
                if (fmin < 20) {
                    continue;
                }
                double df = Math.abs(hz[i] - hz[j]);
                double cb = 1.72 * Math.pow(fmin / 1000.0, 0.65) * 1000.0;
                double x = df / Math.max(1.0, cb);
                double diss = Math.exp(-3.5 * x) - Math.exp(-5.75 * x);
                if (diss > 0) {
                    sum += Math.sqrt(amp[i] * amp[j]) * diss;
                }
            }
        }
        return sum;
    }

    private record Envelope(double attackMs, double decayMs, boolean died) {
    }

    private static Envelope envelope(float[] samples, int rate) {
        int win = Math.max(8, rate / 100);
        int hop = Math.max(1, win / 2);
        int frames = 0;
        for (int i = 0; i + win <= samples.length; i += hop) {
            frames++;
        }
        if (frames == 0) {
            return new Envelope(0, 0, false);
        }
        double[] rms = new double[frames];
        int index = 0;
        double peak = 0;
        int peakAt = 0;
        for (int i = 0; i + win <= samples.length; i += hop) {
            double energy = 0;
            for (int n = 0; n < win; n++) {
                float s = samples[i + n];
                energy += s * (double) s;
            }
            rms[index] = Math.sqrt(energy / win);
            if (rms[index] > peak) {
                peak = rms[index];
                peakAt = index;
            }
            index++;
        }
        double attack = 0;
        if (peak > 1e-6) {
            for (int i = 0; i < frames; i++) {
                if (rms[i] >= peak * 0.8) {
                    attack = i * hop * 1000.0 / rate;
                    break;
                }
            }
        }
        double decay = frames * hop * 1000.0 / rate;
        for (int i = peakAt; i < frames; i++) {
            if (rms[i] <= peak * 0.1) {
                decay = (i - peakAt) * hop * 1000.0 / rate;
                break;
            }
        }
        int holdEnd = Math.min(samples.length, (int) (2.0 * rate));
        int from = Math.min(holdEnd, (int) (1.5 * rate));
        double tail = rms(samples, from, holdEnd);
        boolean died = peak > 1e-6 && tail <= peak * 0.25;
        return new Envelope(attack, decay, died);
    }

    private static double rms(float[] samples, int from, int to) {
        if (to <= from) {
            return 0;
        }
        double energy = 0;
        for (int i = from; i < to; i++) {
            float s = samples[i];
            energy += s * (double) s;
        }
        return Math.sqrt(energy / (to - from));
    }

    private static int clicks(float[] samples, int start, int end, double sigma) {
        int count = end - start - 1;
        if (count < 16) {
            return 0;
        }
        double mean = 0;
        for (int i = start; i < end - 1; i++) {
            mean += samples[i + 1] - samples[i];
        }
        mean /= count;
        double var = 0;
        for (int i = start; i < end - 1; i++) {
            double d = samples[i + 1] - samples[i] - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / count);
        if (std < 1e-6) {
            return 0;
        }
        double limit = Math.max(0.2, sigma * std);
        int clicks = 0;
        for (int i = start; i < end - 1; i++) {
            if (Math.abs(samples[i + 1] - samples[i]) > limit) {
                clicks++;
            }
        }
        return clicks;
    }

    private static double loudness(float[] samples, int rate) {
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float x = samples[i];
            if (x > 1f) {
                x = 1f;
            } else if (x < -1f) {
                x = -1f;
            }
            pcm[i] = (short) Math.round(x * 32767f);
        }
        try {
            return LoudnessMeter.measure(pcm, 1, rate).integrated();
        } catch (RuntimeException e) {
            return -70;
        }
    }

    private static int nextPow2(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >>> 1;
            for (; (j & bit) != 0; bit >>>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wlenRe = Math.cos(ang);
            double wlenIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wRe = 1;
                double wIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int j = i + k;
                    int m = j + len / 2;
                    double uRe = re[m] * wRe - im[m] * wIm;
                    double uIm = re[m] * wIm + im[m] * wRe;
                    re[m] = re[j] - uRe;
                    im[m] = im[j] - uIm;
                    re[j] += uRe;
                    im[j] += uIm;
                    double nextRe = wRe * wlenRe - wIm * wlenIm;
                    wIm = wRe * wlenIm + wIm * wlenRe;
                    wRe = nextRe;
                }
            }
        }
    }
}
