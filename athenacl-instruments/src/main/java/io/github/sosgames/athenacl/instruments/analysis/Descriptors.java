// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.render.loudness.LoudnessMeter;

/**
 * Measurements on one dry probe. Pitch is the pitch class of the steady
 * tone, after the attack, and an octave miss is recorded beside it.
 * A loop click is a repeating spike at a loop boundary, not a bright edge.
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
            double roughness,
            int octaveOffset) {
    }

    public static Measurement measure(float[] samples, int rate, double nominalHz,
                                      double clickSigma, double clipDbfs, double inharmonicCents) {
        double fullScale = Math.pow(10.0, -0.3 / 20.0);
        double peak = 0;
        int run = 0;
        int clips = 0;
        for (float sample : samples) {
            double abs = Math.abs(sample);
            if (abs > peak) {
                peak = abs;
            }
            if (abs >= fullScale) {
                run++;
                if (run >= 3) {
                    clips++;
                }
            } else {
                run = 0;
            }
        }
        if (peak >= fullScale || truePeak(samples, fullScale) || flatTop(samples, peak)) {
            clips = Math.max(clips, 8);
        }
        double peakDb = peak <= 1e-9 ? -120 : 20.0 * Math.log10(peak);
        Envelope envelope = envelope(samples, rate);
        int steady = steadyStart(samples.length, rate, envelope.attackMs);
        int hold = Math.min(samples.length, Math.max(steady + rate / 5, (int) (1.85 * rate)));
        float[] timbre = normalize(samples, rate, peak);
        double target = harmonicF0(timbre, steady, hold, rate, nominalHz);
        double f0 = yinNear(timbre, steady, Math.min(hold, steady + 16384), rate, target);
        int octave = 0;
        double cents = 0;
        if (Double.isFinite(f0) && f0 > 0 && nominalHz > 0) {
            double raw = 1200.0 * Math.log(f0 / nominalHz) / Math.log(2.0);
            octave = (int) Math.round(raw / 1200.0);
            cents = raw - octave * 1200.0;
            if (cents > 600) {
                cents -= 1200;
                octave++;
            } else if (cents < -600) {
                cents += 1200;
                octave--;
            }
        }
        double fundamental = Double.isFinite(f0) && f0 > 0 ? f0 : nominalHz;
        Spectrum spectrum = windowSpectrum(timbre, steady, hold, rate, fundamental, inharmonicCents);
        if (nominalHz > 0 && fundamental > 0
                && Math.abs(Math.log(fundamental / nominalHz) / Math.log(2.0)) > 0.08) {
            Spectrum atNote = windowSpectrum(timbre, steady, hold, rate, nominalHz, inharmonicCents);
            if (atNote.inharmonic < spectrum.inharmonic) {
                spectrum = atNote;
            }
        }
        int clicks = envelope.died ? 0 : clicks(samples, steady, hold, clickSigma);
        double loudness = loudness(samples, rate);
        return new Measurement(cents, spectrum.inharmonic, spectrum.centroidRatio, spectrum.flatness,
                spectrum.oddEven, envelope.attackMs, envelope.decayMs, envelope.died, peakDb, clips, clicks,
                loudness, spectrum.roughness, octave);
    }

    private static int steadyStart(int length, int rate, double attackMs) {
        int steady = (int) Math.round(rate * (attackMs / 1000.0 + 0.04));
        int latest = Math.max(0, length - rate / 5);
        if (steady > latest) {
            steady = latest / 2;
        }
        return Math.max(0, Math.min(steady, length - 1));
    }

    /** Scale toward -23 LUFS without pushing the copy into clipping. Pitch does not move. */
    private static float[] normalize(float[] samples, int rate, double peak) {
        double lu = loudness(samples, rate);
        if (!Double.isFinite(lu) || lu <= -50 || peak < 1e-6) {
            return samples;
        }
        double gain = Math.pow(10.0, (-23.0 - lu) / 20.0);
        gain = Math.min(gain, 0.95 / peak);
        if (Math.abs(gain - 1.0) < 0.04) {
            return samples;
        }
        float[] out = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = (float) (samples[i] * gain);
        }
        return out;
    }

    /**
     * A hard clip stays at one value for a stretch. The synth may turn that
     * stretch down, so it is not always near 0 dBFS. A sine peak is not flat.
     */
    private static boolean flatTop(float[] samples, double peak) {
        if (peak < 0.05) {
            return false;
        }
        double tol = Math.max(1.0e-4, peak * 5.0e-4);
        int up = 0;
        int down = 0;
        int longest = 0;
        for (float sample : samples) {
            if (sample >= peak - tol) {
                up++;
                if (up > longest) {
                    longest = up;
                }
            } else {
                up = 0;
            }
            if (sample <= -peak + tol) {
                down++;
                if (down > longest) {
                    longest = down;
                }
            } else {
                down = 0;
            }
        }
        return longest >= 24;
    }

    private static boolean truePeak(float[] samples, double fullScale) {
        for (int i = 1; i < samples.length - 2; i++) {
            double p0 = samples[i - 1];
            double p1 = samples[i];
            double p2 = samples[i + 1];
            double p3 = samples[i + 2];
            for (int k = 1; k <= 3; k++) {
                double t = k / 4.0;
                double t2 = t * t;
                double t3 = t2 * t;
                double v = 0.5 * ((2 * p1)
                        + (-p0 + p2) * t
                        + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                        + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
                if (Math.abs(v) >= fullScale) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Picks the octave whose first harmonics carry the energy, then stays there.
     * A missing fundamental still loses to the octave that lines the partials up.
     */
    private static double harmonicF0(float[] samples, int start, int end, int rate, double nominalHz) {
        int n = Math.min(8192, end - start);
        if (n < 256 || nominalHz <= 0) {
            return nominalHz;
        }
        double bestScore = -1;
        double best = nominalHz;
        for (int oct = -3; oct <= 3; oct++) {
            double f = nominalHz * Math.pow(2.0, oct);
            if (f < 30 || f > 4000) {
                continue;
            }
            double score = harmonicScore(samples, start, n, rate, f);
            score *= 1.0 - 0.02 * Math.abs(oct);
            if (score > bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best;
    }

    private static double harmonicScore(float[] samples, int start, int n, int rate, double f0) {
        double score = 0;
        for (int k = 1; k <= 6; k++) {
            double w = 2.0 * Math.PI * f0 * k / rate;
            double sr = 0;
            double si = 0;
            for (int i = 0; i < n; i++) {
                double x = samples[start + i];
                sr += x * Math.cos(w * i);
                si += x * Math.sin(w * i);
            }
            score += Math.hypot(sr, si) / k;
        }
        return score;
    }

    /** YIN inside a few percent of {@code targetHz}, so it cannot jump an octave. */
    private static double yinNear(float[] samples, int start, int end, int rate, double targetHz) {
        if (targetHz <= 0) {
            return targetHz;
        }
        int n = end - start;
        if (n < 64) {
            return targetHz;
        }
        int center = (int) Math.round(rate / targetHz);
        int minLag = Math.max(2, (int) Math.round(center * 0.94));
        int maxLag = Math.min(n / 2 - 1, (int) Math.round(center * 1.06));
        if (maxLag <= minLag) {
            return targetHz;
        }
        double[] diff = new double[maxLag + 1];
        for (int tau = minLag; tau <= maxLag; tau++) {
            double sum = 0;
            int count = n - tau;
            for (int i = 0; i < count; i++) {
                double delta = samples[start + i] - samples[start + i + tau];
                sum += delta * delta;
            }
            diff[tau] = sum;
        }
        double[] cmnd = new double[maxLag + 1];
        double running = 0;
        for (int tau = 1; tau <= maxLag; tau++) {
            running += diff[tau];
            cmnd[tau] = running == 0 ? 1 : diff[tau] * tau / running;
        }
        int best = minLag;
        for (int tau = minLag + 1; tau <= maxLag; tau++) {
            if (cmnd[tau] < cmnd[best]) {
                best = tau;
            }
        }
        if (cmnd[best] > 0.5) {
            return targetHz;
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
        return tau <= 0 ? targetHz : rate / tau;
    }

    private static Spectrum windowSpectrum(float[] samples, int start, int end, int rate,
                                           double fundamental, double inharmonicCents) {
        int used = Math.min(16384, Math.max(0, end - start));
        if (used < 64) {
            return new Spectrum(0, 0, 0, 0, 0);
        }
        double[] re = new double[nextPow2(used)];
        double[] im = new double[re.length];
        for (int i = 0; i < used; i++) {
            double hann = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / Math.max(1, used - 1));
            re[i] = samples[start + i] * hann;
        }
        fft(re, im);
        return spectrum(re, im, rate, fundamental, inharmonicCents);
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

    /**
     * Counts spikes that repeat at one phase of a detected loop, after the attack.
     * A saw edge or a square reset is steep on one side, so it is not a click.
     * No stable loop means there is nothing to join.
     */
    private static int clicks(float[] samples, int start, int end, double sigma) {
        int from = Math.max(1, start);
        int to = Math.min(samples.length - 1, Math.min(end, from + 16384));
        if (to - from < 256) {
            return 0;
        }
        double level = rms(samples, from, to);
        if (level < 0.02) {
            return 0;
        }
        double medianStep = medianStep(samples, from, to);
        double limit = Math.max(0.05, Math.max(level * 1.1, sigma * medianStep));
        int[] lags = loopLags(samples, from, to);
        int best = 0;
        for (int lag : lags) {
            best = Math.max(best, phaseClicks(samples, from, to, lag, limit));
        }
        return best;
    }

    private static double medianStep(float[] samples, int from, int to) {
        int span = to - from - 1;
        if (span < 8) {
            return 0;
        }
        int stride = Math.max(1, span / 512);
        double[] steps = new double[(span + stride - 1) / stride];
        int n = 0;
        for (int i = from; i < to - 1 && n < steps.length; i += stride) {
            steps[n++] = Math.abs(samples[i + 1] - samples[i]);
        }
        java.util.Arrays.sort(steps, 0, n);
        return n == 0 ? 0 : steps[n / 2];
    }

    private static int[] loopLags(float[] samples, int from, int to) {
        int n = to - from;
        int maxLag = Math.min(n / 3, 2400);
        int minLag = 16;
        if (maxLag <= minLag) {
            return new int[0];
        }
        int stride = 4;
        double energy = 0;
        int count0 = 0;
        for (int i = 0; i < n; i += stride) {
            double v = samples[from + i];
            energy += v * v;
            count0++;
        }
        if (energy < 1e-8 || count0 == 0) {
            return new int[0];
        }
        double best = 0;
        int bestLag = minLag;
        double[] corr = new double[maxLag + 1];
        for (int tau = minLag; tau <= maxLag; tau += 2) {
            double sum = 0;
            int c = 0;
            for (int i = 0; i + tau < n; i += stride) {
                double d = samples[from + i] - samples[from + i + tau];
                sum += d * d;
                c++;
            }
            double scale = c / (double) count0;
            double r = 1.0 - sum / (2.0 * energy * Math.max(1e-9, scale));
            corr[tau] = r;
            if (r > best) {
                best = r;
                bestLag = tau;
            }
        }
        if (best < 0.72) {
            return new int[0];
        }
        java.util.ArrayList<Integer> lags = new java.util.ArrayList<>();
        for (int tau = minLag + 2; tau <= maxLag - 2; tau += 2) {
            if (corr[tau] >= 0.72 && corr[tau] >= best * 0.9
                    && corr[tau] >= corr[tau - 2] && corr[tau] >= corr[tau + 2]) {
                lags.add(tau);
            }
        }
        if (lags.isEmpty()) {
            lags.add(bestLag);
        }
        int[] out = new int[lags.size()];
        for (int i = 0; i < lags.size(); i++) {
            out[i] = lags.get(i);
        }
        return out;
    }

    private static int phaseClicks(float[] samples, int from, int to, int lag, double limit) {
        if (lag < 2) {
            return 0;
        }
        int[] hits = new int[lag];
        for (int i = from; i + 1 < to; i++) {
            double left = samples[i] - samples[i - 1];
            double right = samples[i + 1] - samples[i];
            // A spike pokes out of both neighbors. A saw reset keeps going the same way.
            if (left * right < 0 && Math.abs(left) >= limit && Math.abs(right) >= limit) {
                hits[(i - from) % lag]++;
            }
        }
        int best = 0;
        for (int hit : hits) {
            if (hit > best) {
                best = hit;
            }
        }
        return best;
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
