// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eighteen GeneralUser names that the old thresholds called bad.
 * Each row in {@code samples/kits/regressions.yaml} is a stand-in tone
 * judged as that preset's class. Ok and caution both pass.
 */
class RegressionVerdictTest {
    private static final int RATE = 48000;
    private static final double C4 = 440.0 * Math.pow(2.0, (60 - 69) / 12.0);

    @Test
    void theEighteenFalsePositivesAreNotBad() throws Exception {
        JsonNode root = new ObjectMapper(new YAMLFactory())
                .readTree(Files.readString(Path.of("samples/kits/regressions.yaml")));
        Thresholds thresholds = Thresholds.defaults();
        int count = 0;
        for (JsonNode row : root.path("cases")) {
            count++;
            String name = row.path("name").asText();
            String cls = row.path("class").asText();
            String kind = row.path("tone").asText();
            float[] tone = tone(kind);
            Descriptors.Measurement measured = Descriptors.measure(tone, RATE, C4, 8, -6, 50);
            ClassVerdict.Decision decision = ClassVerdict.judge(cls, measured.pitchErrorCents(),
                    measured.inharmonicEnergy(), measured.flatness(), measured.clipCount(),
                    measured.loopClicks(), measured.roughness(), measured.died(),
                    measured.peakDbfs(), thresholds);
            String detail = name + " kind=" + kind + " verdict=" + decision.verdict()
                    + " reasons=" + decision.reasons()
                    + " cents=" + measured.pitchErrorCents()
                    + " octave=" + measured.octaveOffset()
                    + " inharmonic=" + measured.inharmonicEnergy()
                    + " clips=" + measured.clipCount()
                    + " clicks=" + measured.loopClicks()
                    + " note=" + decision.note();
            assertNotEquals("bad", decision.verdict(), detail);
            if ("loud".equals(row.path("note").asText())) {
                assertTrue(decision.note().startsWith("loud"), detail);
            }
            if ("octave".equals(row.path("note").asText())) {
                assertTrue(measured.octaveOffset() != 0, detail);
                assertTrue(Math.abs(measured.pitchErrorCents()) <= 30, detail);
            }
        }
        assertTrue(count == 18, "cases " + count);
    }

    private static float[] tone(String kind) {
        float[] samples = new float[RATE * 2];
        switch (kind) {
            case "octave-down" -> fillSine(samples, C4 / 2.0, 0.4);
            case "octave-up" -> fillSine(samples, C4 * 2.0, 0.4);
            case "hot" -> fillSine(samples, C4, 0.63);
            case "attack-click" -> attackThenSine(samples);
            case "chorus" -> chorus(samples);
            case "square" -> square(samples);
            case "saw" -> saw(samples);
            case "noise" -> noise(samples);
            case "inharmonic" -> inharmonic(samples);
            default -> throw new IllegalArgumentException(kind);
        }
        return samples;
    }

    private static void fillSine(float[] samples, double hz, double amp) {
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) (amp * Math.sin(2.0 * Math.PI * hz * i / RATE));
        }
    }

    private static void attackThenSine(float[] samples) {
        samples[0] = 0.45f;
        samples[1] = -0.2f;
        samples[2] = 0.15f;
        for (int i = 180; i < samples.length; i++) {
            double env = Math.exp(-(i - 180) / (RATE * 0.45));
            samples[i] = (float) (0.35 * env * Math.sin(2.0 * Math.PI * C4 * i / RATE));
        }
    }

    private static void chorus(float[] samples) {
        double other = C4 * Math.pow(2.0, 8.0 / 1200.0);
        for (int i = 0; i < samples.length; i++) {
            double a = Math.sin(2.0 * Math.PI * C4 * i / RATE);
            double b = Math.sin(2.0 * Math.PI * other * i / RATE);
            samples[i] = (float) (0.22 * (a + b));
        }
    }

    private static void square(float[] samples) {
        for (int k = 0; k < 12; k++) {
            int n = 2 * k + 1;
            double amp = 0.4 / n;
            for (int i = 0; i < samples.length; i++) {
                samples[i] += (float) (amp * Math.sin(2.0 * Math.PI * C4 * n * i / RATE));
            }
        }
    }

    private static void saw(float[] samples) {
        for (int i = 0; i < samples.length; i++) {
            double phase = (C4 * i / RATE) % 1.0;
            samples[i] = (float) (0.4 * (2.0 * phase - 1.0));
        }
    }

    private static void noise(float[] samples) {
        int state = 0xA3C59AC3;
        int n = (int) (RATE * 0.35);
        for (int i = 0; i < n; i++) {
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            double v = ((state >>> 1) & 0xFFFF) / 32768.0 - 1.0;
            samples[i] = (float) (v * 0.2);
        }
    }

    private static void inharmonic(float[] samples) {
        double[] ratios = {1.0, 2.47, 3.91, 5.33};
        double[] amps = {1.0, 0.7, 0.45, 0.3};
        for (int k = 0; k < ratios.length; k++) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] += (float) (0.2 * amps[k]
                        * Math.sin(2.0 * Math.PI * C4 * ratios[k] * i / RATE));
            }
        }
    }
}
