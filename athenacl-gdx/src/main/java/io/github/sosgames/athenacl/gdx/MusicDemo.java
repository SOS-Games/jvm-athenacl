// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.LiveClock;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays the ambush and shows the scheduler line. F3 writes {@code build/athenacl-snapshot.txt}.
 * F4 hides the line. The notes are built before the first sample, then the speakers play that WAV.
 */
public class MusicDemo extends ApplicationAdapter {
    private SpriteBatch batch;
    private BitmapFont font;
    private AudioDevice device;
    private LiveClock clock;
    private Thread playback;
    private volatile boolean stop;
    private volatile boolean started;
    private volatile String error;
    private boolean overlay = true;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        try {
            Theme theme = Themes.load(Path.of("themes/fantasy-dungeon.yaml"));
            clock = LiveClock.start(theme, ControlLog.readTimeline(Path.of("samples/timelines/ambush.json")), 1);
            device = Gdx.audio.newAudioDevice(44100, false);
            playback = new Thread(this::playScore, "athenacl-speakers");
            playback.setDaemon(true);
            playback.start();
        } catch (Exception e) {
            error = e.getMessage();
        }
    }

    @Override
    public void render() {
        if (clock != null && Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            writeSnapshot();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            overlay = !overlay;
        }
        if (clock != null && started) {
            clock.poll(Gdx.graphics.getDeltaTime());
        }
        ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1f);
        if (!overlay) {
            return;
        }
        batch.begin();
        font.draw(batch, line(), 16, 48);
        batch.end();
    }

    private String line() {
        if (error != null) {
            return error;
        }
        if (!started) {
            return "rendering";
        }
        return clock.snapshot();
    }

    private void playScore() {
        try {
            SoundbankResolver.Selection selection = SoundbankResolver.select(soundbankRequest(), SoundbankPaths.cache(null));
            byte[] wav = WavRenderer.write(clock.performance().score(), selection.file());
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
                byte[] buffer = new byte[4096];
                int read;
                while (!stop && (read = audio.read(buffer)) > 0) {
                    int samples = read / 2;
                    if (samples == 0) {
                        continue;
                    }
                    short[] pcm = new short[samples];
                    for (int i = 0; i < samples; i++) {
                        int low = buffer[i * 2] & 0xff;
                        int high = buffer[i * 2 + 1];
                        pcm[i] = (short) ((high << 8) | low);
                    }
                    started = true;
                    device.writeSamples(pcm, 0, samples);
                }
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    /** Same lookup as the CLI: {@code -Dathenacl.soundbank}, then {@code ATHENACL_SOUNDBANK}, then {@code local.properties}. */
    private static String soundbankRequest() {
        String prop = System.getProperty("athenacl.soundbank");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("ATHENACL_SOUNDBANK");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        Path file = Path.of("local.properties");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                if (line.substring(0, eq).trim().equals("athenacl.soundbank")) {
                    String value = line.substring(eq + 1).trim();
                    return value.isBlank() ? null : value;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private void writeSnapshot() {
        try {
            Path path = Path.of("build/athenacl-snapshot.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, clock.snapshot() + System.lineSeparator());
        } catch (IOException e) {
            error = e.getMessage();
        }
    }

    @Override
    public void dispose() {
        stop = true;
        if (playback != null) {
            try {
                playback.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (device != null) {
            device.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
        if (font != null) {
            font.dispose();
        }
    }
}
