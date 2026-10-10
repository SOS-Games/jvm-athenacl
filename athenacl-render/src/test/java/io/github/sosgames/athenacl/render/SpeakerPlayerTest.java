// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerPlayerTest {
    @Test
    void scaleLengthIncludesTheTail() {
        NoteEvent last = new NoteEvent(1, 6720, 960, 72, "C5", 96, 0, "melody", 2, "4", "1/4", "0");
        Score score = new Score(List.of(), List.of(last), 120, 4, 4, 2);
        assertEquals(4.4, WavRenderer.durationSeconds(score), 1e-9);
    }

    @Test
    void dryRunReadsAWavWithoutOpeningSpeakers() throws Exception {
        int frames = 44100;
        byte[] pcm = new byte[WavRenderer.FORMAT.getFrameSize() * frames];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), WavRenderer.FORMAT, frames),
                AudioFileFormat.Type.WAVE, out);
        SpeakerPlayer.Played played = SpeakerPlayer.play(out.toByteArray(), true);
        assertTrue(played.dryRun());
        assertEquals(1.0, played.seconds(), 1e-6);
    }

    @Test
    void dryRunReadsAWavFile() throws Exception {
        int frames = 44100;
        byte[] pcm = new byte[WavRenderer.FORMAT.getFrameSize() * frames];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), WavRenderer.FORMAT, frames),
                AudioFileFormat.Type.WAVE, out);
        Path wav = Path.of("build/speaker-dry.wav");
        Files.createDirectories(wav.getParent());
        Files.write(wav, out.toByteArray());
        SpeakerPlayer.Played played = SpeakerPlayer.play(wav, true);
        assertTrue(played.dryRun());
        assertEquals(1.0, played.seconds(), 1e-6);
    }
}
