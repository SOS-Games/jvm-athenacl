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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays the ambush through the theme's kit, then can swap to the combat theme at a bar line.
 * The next kit is rendered on a background thread and becomes audible on the next bar after bar 8,
 * until a track button is pressed. Each button starts that piece from the beginning.
 * The line under the scheduler is how far through the current track you are.
 * F3 writes {@code build/athenacl-snapshot.txt}. F4 hides the scheduler line.
 */
public class MusicDemo extends ApplicationAdapter {
    private static final int SWAP_BAR = 8;

    private record Track(String name, String theme, String timeline) {
    }

    private static final Track[] TRACKS = {
            new Track("Ambush", "themes/fantasy-dungeon.yaml", "samples/timelines/ambush.json"),
            new Track("Dungeon", "themes/fantasy-dungeon.yaml", "samples/timelines/dungeon-tour.json"),
            new Track("Combat", "themes/fantasy-combat.yaml", null),
            new Track("Party", "themes/fantasy-party.yaml", null),
            new Track("Trading", "themes/fantasy-trading.yaml", null),
            new Track("Space", "themes/scifi-space.yaml", "samples/timelines/dungeon-tour.json"),
            new Track("Space combat", "themes/scifi-combat.yaml", null)
    };

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Skin skin;
    private Stage stage;
    private final Map<String, TextButton> buttons = new java.util.LinkedHashMap<>();
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Map<String, LiveClock> clocks = new ConcurrentHashMap<>();
    private AudioDevice device;
    private volatile LiveClock clock;
    private Thread playback;
    private Thread preload;
    private Thread worker;
    private volatile boolean stop;
    private volatile boolean started;
    private volatile boolean picked;
    private volatile String error;
    private volatile byte[] standbyWav;
    private volatile boolean playStandby;
    private volatile Track wanted;
    private volatile byte[] queuedWav;
    private volatile LiveClock queuedClock;
    private volatile String queuedName;
    private volatile int queuedId;
    private volatile String nowPlaying = "Ambush";
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
        for (int i = 0; i < TRACKS.length; i++) {
            Track track = TRACKS[i];
            TextButton button = new TextButton(track.name, skin);
            int column = i % 4;
            int row = i / 4;
            button.setPosition(16 + column * 270f, 16 + row * 44f);
            button.setSize(258, 36);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    request(track);
                }
            });
            buttons.put(track.name, button);
            stage.addActor(button);
        }
        Gdx.input.setInputProcessor(stage);
        try {
            Theme theme = Themes.load(Path.of(TRACKS[0].theme));
            clock = LiveClock.start(theme, ControlLog.readTimeline(Path.of(TRACKS[0].timeline)), 1);
            clocks.put(TRACKS[0].name, clock);
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
        if (clock != null && started && !picked) {
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
        } else if (clock != null && started) {
            clock.poll(Gdx.graphics.getDeltaTime());
        }
        TextButton.TextButtonStyle plain = skin.get(TextButton.TextButtonStyle.class);
        TextButton.TextButtonStyle hot = skin.get("playing", TextButton.TextButtonStyle.class);
        for (Track track : TRACKS) {
            TextButton button = buttons.get(track.name);
            if ("Rendering...".contentEquals(button.getText())) {
                continue;
            }
            button.setStyle(track.name.equals(nowPlaying) ? hot : plain);
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
        font.draw(batch, nowPlaying + "  " + timestamp(heardSeconds) + " / " + timestamp(trackSeconds), 16, 168);
        batch.end();
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.2f, 0.24f, 1f);
        shapes.rect(16, 128, barWidth, 12);
        shapes.setColor(0.45f, 0.78f, 0.86f, 1f);
        shapes.rect(16, 128, barWidth * fraction, 12);
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

    private void request(Track track) {
        if (device == null) {
            return;
        }
        picked = true;
        playStandby = false;
        byte[] ready = cache.get(track.name);
        if (ready != null) {
            wanted = null;
            handoff(track.name, ready, clocks.get(track.name));
            showLabels();
            return;
        }
        wanted = track;
        showRendering(track.name);
        if ("Combat".equals(track.name) && preload != null && preload.isAlive()) {
            return;
        }
        Thread running = worker;
        if (running != null && running.isAlive()) {
            return;
        }
        worker = new Thread(this::renderWanted, "athenacl-track");
        worker.setDaemon(true);
        worker.start();
    }

    private void renderWanted() {
        try {
            while (!stop) {
                Track track = wanted;
                if (track == null) {
                    return;
                }
                byte[] wav = cache.get(track.name);
                LiveClock next = clocks.get(track.name);
                if (wav == null) {
                    Gdx.app.postRunnable(() -> showRendering(track.name));
                    next = open(track);
                    wav = ThemeMix.wav(Themes.load(Path.of(track.theme)), next.performance().score(), states(next));
                    cache.put(track.name, wav);
                    clocks.put(track.name, next);
                    Gdx.app.postRunnable(this::showLabels);
                }
                if (wanted == track) {
                    handoff(track.name, wav, next);
                    wanted = null;
                    return;
                }
            }
        } catch (Exception e) {
            wanted = null;
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Gdx.app.postRunnable(this::showLabels);
        }
    }

    private static LiveClock open(Track track) throws Exception {
        Theme theme = Themes.load(Path.of(track.theme));
        List<Theme.Control> controls = track.timeline == null
                ? theme.timeline()
                : ControlLog.readTimeline(Path.of(track.timeline));
        return LiveClock.start(theme, controls, 1);
    }

    private void handoff(String name, byte[] wav, LiveClock next) {
        queuedName = name;
        queuedClock = next;
        queuedWav = wav;
        queuedId++;
    }

    private void showRendering(String name) {
        for (Track track : TRACKS) {
            buttons.get(track.name).setText(track.name.equals(name) ? "Rendering..." : track.name);
        }
    }

    private void showLabels() {
        for (Track track : TRACKS) {
            buttons.get(track.name).setText(track.name);
        }
    }

    private void preloadCombat() {
        try {
            Theme combat = Themes.load(Path.of("themes/fantasy-combat.yaml"));
            LiveClock next = LiveClock.start(combat, combat.timeline(), 1);
            byte[] wav = ThemeMix.wav(combat, next.performance().score(), states(next));
            standbyWav = wav;
            cache.put("Combat", wav);
            clocks.put("Combat", next);
            swap.preloaded();
            Track asked = wanted;
            if (asked != null && "Combat".equals(asked.name)) {
                handoff("Combat", wav, next);
                wanted = null;
                Gdx.app.postRunnable(this::showLabels);
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void playScore() {
        try {
            Theme theme = Themes.load(Path.of(TRACKS[0].theme));
            byte[] wav = ThemeMix.wav(theme, clock.performance().score(), states(clock));
            cache.put("Ambush", wav);
            preload = new Thread(this::preloadCombat, "athenacl-kit");
            preload.setDaemon(true);
            preload.start();
            String name = "Ambush";
            int id = queuedId;
            boolean fresh = true;
            while (!stop) {
                if (queuedWav != null) {
                    wav = queuedWav;
                    name = queuedName;
                    LiveClock next = queuedClock;
                    id = queuedId;
                    queuedWav = null;
                    if (next != null) {
                        clock = next;
                    }
                    playStandby = false;
                    fresh = true;
                }
                if (fresh && wav != null) {
                    fresh = false;
                    play(wav, name, id);
                    continue;
                }
                if (!picked && playStandby && standbyWav != null && wav != standbyWav) {
                    wav = standbyWav;
                    name = "Combat";
                    fresh = true;
                    continue;
                }
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void play(byte[] wav, String name, int id) throws Exception {
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
            while (!stop && queuedId == id && (read = audio.read(buffer)) > 0) {
                if (queuedWav != null && wav != queuedWav) {
                    return;
                }
                if (!picked && wav != standbyWav && playStandby && standbyWav != null && queuedWav == null) {
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
        TextButton.TextButtonStyle playing = new TextButton.TextButtonStyle(style);
        playing.up = skin.newDrawable("white", new Color(0.12f, 0.45f, 0.55f, 1f));
        skin.add("playing", playing);
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
        if (worker != null) {
            try {
                worker.join(2000);
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
