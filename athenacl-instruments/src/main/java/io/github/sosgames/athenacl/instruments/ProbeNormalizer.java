// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.render.SlotSpec;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.render.loudness.LoudnessMeter;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Renders a fixed phrase and stores the extra instrument gain that lands on -20 LUFS.
 * Channel volume stays at 100, because that control only has a few decibels of room
 * above its usual setting. Manual {@code gainDb} is applied after that, on top.
 * The cache key is the soundfont path, the source patch, transpose, and the probe version.
 */
public final class ProbeNormalizer {
    public static final double TARGET = -20.0;
    private static final String VERSION = "v2";

    private ProbeNormalizer() {
    }

    public record Level(int cc7, double gainDb, double rawLufs, double probeLufs) {
    }

    public static Level measure(Playable playable) throws IOException, MidiUnavailableException {
        Path cache = cache(playable);
        if (Files.isRegularFile(cache)) {
            String[] parts = Files.readString(cache, StandardCharsets.UTF_8).trim().split(" ");
            if (parts.length == 4) {
                return new Level(Integer.parseInt(parts[0]), Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            }
        }
        int cc7 = 100;
        double raw = loudness(playable, cc7, 0);
        double gain = 0;
        double probe = raw;
        if (Double.isFinite(raw)) {
            gain = TARGET - raw;
            for (int pass = 0; pass < 6 && Math.abs(probe - TARGET) > 0.5; pass++) {
                if (pass > 0) {
                    gain += TARGET - probe;
                }
                gain = Math.max(-40, Math.min(40, gain));
                probe = loudness(playable, cc7, gain);
            }
        }
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, cc7 + " " + gain + " " + raw + " " + probe + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return new Level(cc7, gain, raw, probe);
    }

    public static int withGain(int cc7, double gainDb) {
        if (gainDb == 0) {
            return cc7;
        }
        int next = (int) Math.round(cc7 * Math.pow(10.0, gainDb / 40.0));
        return Math.max(1, Math.min(127, next));
    }

    public static double loudness(Playable playable, int cc7, double gainDb)
            throws IOException, MidiUnavailableException {
        byte[] wav = WavRenderer.write(phrase(playable, cc7), null, List.of(spec(playable, gainDb)));
        Path temp = Files.createTempFile("athenacl-probe", ".wav");
        try {
            Files.write(temp, wav);
            return LoudnessMeter.measure(temp).integrated();
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static SlotSpec spec(Playable playable) {
        double gain = 0;
        Path cache = cache(playable);
        if (Files.isRegularFile(cache)) {
            try {
                String[] parts = Files.readString(cache, StandardCharsets.UTF_8).trim().split(" ");
                if (parts.length == 4) {
                    gain = Double.parseDouble(parts[1]);
                }
            } catch (IOException ignored) {
                gain = 0;
            }
        }
        return spec(playable, gain);
    }

    public static SlotSpec spec(Playable playable, double gainDb) {
        return new SlotSpec(playable.file(), playable.sourceBank(), Math.max(0, playable.sourceProgram()),
                playable.preset(), playable.drumkit(), playable.slotBank(), playable.slotProgram(),
                playable.percussion(), gainDb);
    }

    private static Path cache(Playable playable) {
        return SoundbankPaths.cache(null).getParent().resolve("normalization").resolve(key(playable) + ".txt");
    }

    private static Score phrase(Playable playable, int cc7) {
        int channel = playable.percussion() ? 9 : 0;
        List<ControlEvent> controls = controls(playable, channel, cc7);
        List<NoteEvent> notes = new ArrayList<>();
        if (playable.percussion()) {
            int[] drums = {36, 38, 42, 36};
            for (int i = 0; i < drums.length; i++) {
                notes.add(note(i + 1L, i * 960L, drums[i], 100, channel));
            }
        } else {
            int[] pitches = {48, 48, 60, 60, 72, 72};
            int[] velocity = {64, 100, 64, 100, 64, 100};
            for (int i = 0; i < pitches.length; i++) {
                notes.add(note(i + 1L, i * 960L, pitches[i], velocity[i], channel));
            }
        }
        return new Score(controls, notes, 60, 4, 4, notes.size());
    }

    public static List<ControlEvent> controls(Playable playable, int channel, int cc7) {
        int volume = withGain(cc7, playable.gainDb());
        return List.of(
                new ControlEvent.ControlChange(0, channel, 0, playable.slotBank() >> 7),
                new ControlEvent.ControlChange(0, channel, 32, playable.slotBank() & 127),
                new ControlEvent.ProgramChange(0, channel, playable.slotProgram()),
                new ControlEvent.ControlChange(0, channel, 7, volume));
    }

    private static NoteEvent note(long id, long start, int pitch, int velocity, int channel) {
        return new NoteEvent(id, start, 960, pitch, "C4", velocity, channel, "probe", 1, "1", "1/4",
                "probe", null);
    }

    private static String key(Playable playable) {
        String raw = playable.file().toAbsolutePath() + "|" + playable.sourceBank() + "|"
                + playable.sourceProgram() + "|" + playable.preset() + "|" + playable.drumkit() + "|"
                + playable.transpose() + "|" + VERSION;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
