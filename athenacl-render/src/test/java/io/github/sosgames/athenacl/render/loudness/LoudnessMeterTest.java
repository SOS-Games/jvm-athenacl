// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.loudness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoudnessMeterTest {
    @Test
    void integratedStaysWithinPointTwoOfFfmpeg(@TempDir Path dir) throws Exception {
        Path wav = dir.resolve("tone.wav");
        int rate = 44100;
        int frames = rate * 5;
        byte[] pcm = new byte[frames * 4];
        for (int i = 0; i < frames; i++) {
            double sample = 0.1 * Math.sin(2 * Math.PI * 997 * i / rate);
            int value = (int) Math.round(sample * 32767);
            for (int channel = 0; channel < 2; channel++) {
                int at = (i * 2 + channel) * 2;
                pcm[at] = (byte) value;
                pcm[at + 1] = (byte) (value >> 8);
            }
        }
        AudioFormat format = new AudioFormat(rate, 16, 2, true, false);
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), format, frames),
                AudioFileFormat.Type.WAVE, wav.toFile());
        LoudnessMeter.Reading reading = LoudnessMeter.measure(wav);
        Process process = new ProcessBuilder(
                "ffmpeg", "-hide_banner", "-nostats", "-i", wav.toString(),
                "-af", "ebur128", "-f", "null", "-")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        Matcher matcher = Pattern.compile("I:\\s*(-?[0-9.]+)\\s*LUFS").matcher(output);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        assertTrue(last != null, output);
        double ffmpeg = Double.parseDouble(last);
        assertTrue(Math.abs(reading.integrated() - ffmpeg) <= 0.2,
                "java " + reading.integrated() + " ffmpeg " + ffmpeg);
    }
}
