// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays a WAV on the default speakers. {@code play --dry-run} reads the length and does not
 * open the device. This is ordinary Java sound, not the libGDX window.
 */
public final class SpeakerPlayer {
    private SpeakerPlayer() {
    }

    public record Played(double seconds, boolean dryRun) {
    }

    public static Played play(Path wav, boolean dryRun)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        try (InputStream in = Files.newInputStream(wav)) {
            return play(in, dryRun);
        }
    }

    public static Played play(byte[] wav, boolean dryRun)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        return play(new ByteArrayInputStream(wav), dryRun);
    }

    private static Played play(InputStream in, boolean dryRun)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(in)) {
            double seconds = seconds(audio);
            if (!dryRun) {
                write(audio);
            }
            return new Played(seconds, dryRun);
        }
    }

    private static double seconds(AudioInputStream audio) {
        AudioFormat format = audio.getFormat();
        long frames = audio.getFrameLength();
        if (frames < 0 || format.getFrameRate() <= 0) {
            return 0;
        }
        return frames / format.getFrameRate();
    }

    private static void write(AudioInputStream audio) throws IOException, LineUnavailableException {
        AudioFormat format = audio.getFormat();
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        line.open(format);
        line.start();
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = audio.read(buffer)) != -1) {
                if (read > 0) {
                    line.write(buffer, 0, read);
                }
            }
            line.drain();
        } finally {
            line.stop();
            line.close();
        }
    }
}
