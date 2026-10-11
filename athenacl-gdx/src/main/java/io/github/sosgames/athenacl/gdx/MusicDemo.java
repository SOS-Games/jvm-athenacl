// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.LiveClock;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeSwitch;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.instruments.ThemeMix;

import javax.sound.sampled.AudioFormat;
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
 * Space renders the space theme and takes over as soon as that audio is ready.
 * The line under the scheduler is how far through the current track you are.
 * F3 writes {@code build/athenacl-snapshot.txt}. F4 hides the scheduler line.
 */
public class MusicDemo extends ApplicationAdapter {
    private static final int SWAP_BAR = 8;
    private static final String SPACE = "scifi-space";

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Skin skin;
    private Stage stage;
    private TextButton spaceButton;
    private AudioDevice device;
    private volatile LiveClock clock;
    private Thread playback;
    private Thread preload;
    private Thread spaceRender;
    private volatile boolean stop;
    private volatile boolean started;
    private volatile String error;
    private volatile byte[] standbyWav;
    private volatile boolean playStandby;
    private volatile boolean spaceBusy;
    private volatile byte[] queuedWav;
    private volatile LiveClock queuedClock;
    private volatile String nowPlaying = "fantasy-dungeon";
    private volatile double heardSeconds;
    private volatile double trackSeconds;
    private boolean overlay = true;
    private final ThemeSwitch swap = new ThemeSwitch("fantasy-dungeon", SWAP_BAR);

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        skin = buttonSkin(font);
        stage = new Stage(new ScreenViewport());
        spaceButton = new TextButton("Space", skin);
        spaceButton.setPosition(16, 16);
        spaceButton.setSize(180, 40);
        spaceButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                requestSpace();
            }
        });
        stage.addActor(spaceButton);
        Gdx.input.setInputProcessor(stage);
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
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
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
        if (clock != null && started && !SPACE.equals(nowPlaying)) {
            clock.poll(Gdx.graphics.getDeltaTime());
            if (!spaceBusy && !playStandby && standbyWav != null && "fantasy-combat".equals(swap.poll(clock.bar()))) {
                try {
                    Theme combat = Themes.load(Path.of("themes/fantasy-combat.yaml"));
                    clock = LiveClock.start(combat, combat.timeline(), 1);
                    playStandby = true;
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
        } else if (clock != null && started) {
            clock.poll(Gdx.graphics.getDeltaTime());
        }
        ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1f);
        float width = Gdx.graphics.getWidth();
        if (overlay) {
            batch.begin();
            font.draw(batch, line(), 16, Gdx.graphics.getHeight() - 24f);
            batch.end();
        }
        drawProgress(width);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    private void drawProgress(float width) {
        float barWidth = Math.min(640f, width - 32f);
        float fraction = trackSeconds <= 0 ? 0f : (float) Math.min(1.0, heardSeconds / trackSeconds);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, nowPlaying + "  " + timestamp(heardSeconds) + " / " + timestamp(trackSeconds), 16, 112);
        batch.end();
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.2f, 0.24f, 1f);
        shapes.rect(16, 72, barWidth, 12);
        shapes.setColor(0.45f, 0.78f, 0.86f, 1f);
        shapes.rect(16, 72, barWidth * fraction, 12);
        shapes.end();
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

    private void requestSpace() {
        if (spaceBusy || SPACE.equals(nowPlaying) || device == null) {
            return;
        }
        spaceBusy = true;
        spaceButton.setText("Rendering...");
        spaceButton.setDisabled(true);
        spaceRender = new Thread(this::renderSpace, "athenacl-space");
        spaceRender.setDaemon(true);
        spaceRender.start();
    }

    private void renderSpace() {
        try {
            Theme theme = Themes.load(Path.of("themes/scifi-space.yaml"));
            LiveClock next = LiveClock.start(theme,
                    ControlLog.readTimeline(Path.of("samples/timelines/dungeon-tour.json")), 1);
            byte[] wav = ThemeMix.wav(theme, next.performance().score(), states(next));
            queuedClock = next;
            queuedWav = wav;
            Gdx.app.postRunnable(() -> {
                spaceButton.setText("Space");
                spaceButton.setDisabled(false);
            });
        } catch (Exception e) {
            spaceBusy = false;
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Gdx.app.postRunnable(() -> {
                spaceButton.setText("Space");
                spaceButton.setDisabled(false);
            });
        }
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
            Theme theme = Themes.load(Path.of("themes/fantasy-dungeon.yaml"));
            byte[] wav = ThemeMix.wav(theme, clock.performance().score(), states(clock));
            String name = "fantasy-dungeon";
            preload = new Thread(this::preloadCombat, "athenacl-kit");
            preload.setDaemon(true);
            preload.start();
            while (!stop) {
                if (queuedWav != null) {
                    wav = queuedWav;
                    name = SPACE;
                    LiveClock next = queuedClock;
                    queuedWav = null;
                    if (next != null) {
                        clock = next;
                    }
                    playStandby = false;
                }
                play(wav, name);
                if (stop) {
                    return;
                }
                if (queuedWav != null) {
                    continue;
                }
                if (playStandby && standbyWav != null && wav != standbyWav) {
                    wav = standbyWav;
                    name = "fantasy-combat";
                    continue;
                }
                break;
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void play(byte[] wav, String name) throws Exception {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat format = audio.getFormat();
            int channels = Math.max(1, format.getChannels());
            float rate = format.getFrameRate();
            long frames = audio.getFrameLength();
            trackSeconds = frames > 0 && rate > 0 ? frames / rate : 0;
            heardSeconds = 0;
            nowPlaying = name;
            long heardFrames = 0;
            byte[] buffer = new byte[4096];
            int read;
            while (!stop && (read = audio.read(buffer)) > 0) {
                if (queuedWav != null && wav != queuedWav) {
                    return;
                }
                if (wav != standbyWav && playStandby && standbyWav != null && queuedWav == null) {
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
                heardFrames += samples / channels;
                if (rate > 0) {
                    heardSeconds = heardFrames / rate;
                }
            }
        }
    }

    private static String timestamp(double seconds) {
        int total = (int) Math.floor(Math.max(0, seconds));
        int minutes = total / 60;
        int remain = total % 60;
        return minutes + ":" + (remain < 10 ? "0" : "") + remain;
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

    private static Skin buttonSkin(BitmapFont font) {
        Skin skin = new Skin();
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
        pixmap.dispose();
        skin.add("default", font);
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = skin.newDrawable("white", new Color(0.16f, 0.28f, 0.36f, 1f));
        style.over = skin.newDrawable("white", new Color(0.22f, 0.4f, 0.5f, 1f));
        style.down = skin.newDrawable("white", new Color(0.12f, 0.5f, 0.58f, 1f));
        style.disabled = skin.newDrawable("white", new Color(0.14f, 0.16f, 0.18f, 1f));
        style.fontColor = Color.WHITE;
        style.disabledFontColor = new Color(0.7f, 0.75f, 0.78f, 1f);
        skin.add("default", style);
        return skin;
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
        if (spaceRender != null) {
            try {
                spaceRender.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (device != null) {
            device.dispose();
        }
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }
}
