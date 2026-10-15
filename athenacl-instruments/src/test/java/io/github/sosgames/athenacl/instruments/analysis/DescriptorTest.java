// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorTest {
    private static final int RATE = 48000;
    private static final double C4 = 440.0 * Math.pow(2.0, (60 - 69) / 12.0);

    @Test
    void aSineIsCloseToItsPitch() {
        float[] samples = new float[RATE * 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (0.4 * Math.sin(2.0 * Math.PI * C4 * i / RATE));
        }
        Descriptors.Measurement measurement = Descriptors.measure(samples, RATE, C4, 8, -2, 50);
        assertTrue(Math.abs(measurement.pitchErrorCents()) < 2, "cents " + measurement.pitchErrorCents());
        assertTrue(measurement.inharmonicEnergy() < 0.1, "inharmonic " + measurement.inharmonicEnergy());
        assertEquals(0, measurement.clipCount());
        assertTrue(measurement.loopClicks() < 8, "clicks " + measurement.loopClicks());
    }

    @Test
    void inharmonicPartialsAreNotAHarmonicSeries() {
        float[] samples = new float[RATE * 2];
        double[] ratios = {1.0, 2.47, 3.91, 5.33};
        double[] amps = {1.0, 0.8, 0.6, 0.4};
        for (int k = 0; k < ratios.length; k++) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] += (float) (amps[k] * Math.sin(2.0 * Math.PI * C4 * ratios[k] * i / RATE));
            }
        }
        Descriptors.Measurement measurement = Descriptors.measure(samples, RATE, C4, 8, -2, 50);
        assertTrue(measurement.inharmonicEnergy() >= 0.25, "inharmonic " + measurement.inharmonicEnergy());
    }

    @Test
    void aHardClipCounts() {
        float[] samples = new float[RATE];
        for (int i = 0; i < samples.length; i++) {
            double s = Math.sin(2.0 * Math.PI * C4 * i / RATE) * 4;
            samples[i] = (float) Math.max(-1, Math.min(1, s));
        }
        Descriptors.Measurement measurement = Descriptors.measure(samples, RATE, C4, 8, -2, 50);
        assertTrue(measurement.clipCount() >= 8, "clips " + measurement.clipCount());
        assertTrue(measurement.peakDbfs() >= -2, "peak " + measurement.peakDbfs());
    }

    @Test
    void aJoinClickIsCounted() {
        float[] samples = new float[RATE * 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (0.3 * Math.sin(2.0 * Math.PI * C4 * i / RATE));
        }
        for (int i = RATE / 2; i < samples.length; i += 400) {
            samples[i] = 0.95f;
            if (i + 1 < samples.length) {
                samples[i + 1] = -0.95f;
            }
        }
        Descriptors.Measurement measurement = Descriptors.measure(samples, RATE, C4, 8, -2, 50);
        assertTrue(measurement.loopClicks() >= 8, "clicks " + measurement.loopClicks());
    }
}
