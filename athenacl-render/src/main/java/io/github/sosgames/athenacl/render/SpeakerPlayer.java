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
import java.nio.file.Path;

/**
 * Plays a WAV on the default speakers. {@code play --dry-run} reads the length and does not
 * open the device. A file is opened as a file: Java's WAV reader rewinds the stream, and a
 * plain file stream cannot. This is ordinary Java sound, not the libGDX window.
 */
public final class SpeakerPlayer {
    private SpeakerPlayer() {
    }

    public record Played(double seconds, boolean dryRun) {
    }

    public static Played play(Path wav, boolean dryRun)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(wav.toFile())) {
            return play(audio, dryRun);
        }
    }

    public static Played play(byte[] wav, boolean dryRun)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            return play(audio, dryRun);
        }
    }

    private static Played play(AudioInputStream audio, boolean dryRun)
            throws IOException, LineUnavailableException {
        double seconds = seconds(audio);
        if (!dryRun) {
            write(audio);
        }
        return new Played(seconds, dryRun);
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
