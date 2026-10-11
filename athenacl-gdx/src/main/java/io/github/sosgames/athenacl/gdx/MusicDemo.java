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
import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.LiveClock;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeSwitch;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.instruments.ThemeMix;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays the ambush through the theme's kit, then can swap to the combat theme at a bar line.
 * The next kit is rendered on a background thread and becomes audible on the next bar after bar 8.
 * F3 writes {@code build/athenacl-snapshot.txt}. F4 hides the line.
 */
public class MusicDemo extends ApplicationAdapter {
    private static final int SWAP_BAR = 8;

    private SpriteBatch batch;
    private BitmapFont font;
    private AudioDevice device;
    private LiveClock clock;
    private Thread playback;
    private Thread preload;
    private volatile boolean stop;
    private volatile boolean started;
    private volatile String error;
    private volatile byte[] standbyWav;
    private volatile boolean playStandby;
    private boolean overlay = true;
    private final ThemeSwitch swap = new ThemeSwitch("fantasy-dungeon", SWAP_BAR);

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        try {
            Theme theme = Themes.load(Path.of("themes/fantasy-dungeon.yaml"));
            clock = LiveClock.start(theme, ControlLog.readTimeline(Path.of("samples/timelines/ambush.json")), 1);
            device = Gdx.audio.newAudioDevice(44100, false);
            swap.arm("fantasy-combat");
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
            if (!playStandby && standbyWav != null && "fantasy-combat".equals(swap.poll(clock.bar()))) {
                try {
                    Theme combat = Themes.load(Path.of("themes/fantasy-combat.yaml"));
                    clock = LiveClock.start(combat, combat.timeline(), 1);
                    playStandby = true;
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
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

    private void preloadCombat() {
        try {
            Theme combat = Themes.load(Path.of("themes/fantasy-combat.yaml"));
            LiveClock next = LiveClock.start(combat, combat.timeline(), 1);
            standbyWav = ThemeMix.wav(combat, next.performance().score(), states(next));
            swap.preloaded();
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void playScore() {
        try {
            byte[] wav = ThemeMix.wav(Themes.load(Path.of("themes/fantasy-dungeon.yaml")),
                    clock.performance().score(), states(clock));
            preload = new Thread(this::preloadCombat, "athenacl-kit");
            preload.setDaemon(true);
            preload.start();
            play(wav);
            if (!stop && playStandby && standbyWav != null) {
                play(standbyWav);
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void play(byte[] wav) throws Exception {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            byte[] buffer = new byte[4096];
            int read;
            while (!stop && (read = audio.read(buffer)) > 0) {
                if (wav != standbyWav && playStandby && standbyWav != null) {
                    return;
                }
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
    }

    private static List<String> states(LiveClock clock) {
        List<String> names = new ArrayList<>();
        for (Director.BarInfo bar : clock.performance().bars()) {
            names.add(bar.state());
        }
        return names;
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
        if (preload != null) {
            try {
                preload.join(2000);
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
