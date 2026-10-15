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
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.LiveClock;
import io.github.sosgames.athenacl.adaptive.Presets;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays each track through the MuseScore kit ({@code gm-full}).
 * The ambush can still swap to the combat theme at a bar line. That kit is rendered on a
 * background thread and becomes audible on the next bar after bar 8, until a track button
 * is pressed. Each button starts that piece from the beginning and repeats it until
 * another button is pressed. Genre buttons are the presets that do not already have a
 * track. The line under the scheduler is where you are in the current pass.
 * The window opens on a silent menu. Sounds lists the soundpack and hides the genres.
 * A sound click repeats until another sound or Back. Genres lists the tracks and
 * starts the ambush piece. Randomize picks a new seed and plays the current track again.
 * The seed is shown beside that button. Back returns to the menu and stops the speakers.
 * The analysis shows when the kit profile has a row for the same program.
 * F3 writes {@code build/athenacl-snapshot.txt}. F4 hides the scheduler line.
 */
public class MusicDemo extends ApplicationAdapter {
    private static final int SWAP_BAR = 8;
    private static final int COLUMNS = 5;
    /** Window playback uses MuseScore_General. Theme files still name their own kit. */
    private static final String KIT = "gm-full";

    private record Track(String name, String theme, String timeline, String preset) {
    }

    private static final Track[] TRACKS = tracks();

    private static Track[] tracks() {
        List<Track> list = new ArrayList<>();
        list.add(new Track("Ambush", "themes/fantasy-dungeon.yaml", "samples/timelines/ambush.json", null));
        list.add(new Track("Dungeon", "themes/fantasy-dungeon.yaml", "samples/timelines/dungeon-tour.json", null));
        list.add(new Track("Combat", "themes/fantasy-combat.yaml", null, null));
        list.add(new Track("Party", "themes/fantasy-party.yaml", null, null));
        list.add(new Track("Trading", "themes/fantasy-trading.yaml", null, null));
        list.add(new Track("Space", "themes/scifi-space.yaml", "samples/timelines/dungeon-tour.json", null));
        list.add(new Track("Space combat", "themes/scifi-combat.yaml", null, null));
        for (String id : Presets.ids()) {
            if ("fantasy".equals(id) || "scifi-space".equals(id)) {
                continue;
            }
            list.add(new Track(label(id), null, null, id));
        }
        return list.toArray(Track[]::new);
    }

    private static String label(String id) {
        return switch (id) {
            case "hiphop" -> "Hip-hop";
            case "lofi" -> "Lo-fi";
            default -> id.substring(0, 1).toUpperCase() + id.substring(1);
        };
    }

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
    private enum Page { MENU, SOUNDS, GENRES }

    private SoundBrowser sounds;
    private boolean soundsLoaded;
    /** Genres should start the ambush piece once its recording is ready. */
    private volatile boolean wantDefault;
    private String genreChoice;
    private Page page = Page.MENU;
    private TextButton menuSounds;
    private TextButton menuGenres;
    private TextButton back;
    private TextButton randomize;
    private Label seedLabel;
    /** The ensemble and the performance both use this. Randomize replaces it. */
    private volatile long seed = 1;

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        skin = buttonSkin(font);
        stage = new Stage(new ScreenViewport());
        float stride = (Gdx.graphics.getWidth() - 32f) / COLUMNS;
        for (int i = 0; i < TRACKS.length; i++) {
            Track track = TRACKS[i];
            TextButton button = new TextButton(track.name, skin);
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            button.setPosition(16 + column * stride, 16 + row * 44f);
            button.setSize(stride - 12f, 36);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    request(track);
                }
            });
            button.setVisible(false);
            buttons.put(track.name, button);
            stage.addActor(button);
        }
        menuSounds = menuButton("Sounds", Page.SOUNDS);
        menuGenres = menuButton("Genres", Page.GENRES);
        back = new TextButton("Back", skin);
        back.setSize(140, 36);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                show(Page.MENU);
            }
        });
        randomize = new TextButton("Randomize", skin);
        randomize.setSize(160, 36);
        randomize.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                randomizeSeed();
            }
        });
        seedLabel = new Label("Seed 1", new Label.LabelStyle(font, Color.WHITE));
        stage.addActor(menuSounds);
        stage.addActor(menuGenres);
        stage.addActor(back);
        stage.addActor(randomize);
        stage.addActor(seedLabel);
        sounds = new SoundBrowser(skin, font, this::preview);
        stage.addActor(sounds.root());
        show(Page.MENU);
        Gdx.input.setInputProcessor(stage);
        try {
            Theme theme = load(TRACKS[0]);
            clock = LiveClock.start(theme, ControlLog.readTimeline(Path.of(TRACKS[0].timeline)), seed);
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && page != Page.MENU) {
            show(Page.MENU);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            overlay = !overlay;
        }
        layout();
        if (clock != null && started && !picked && page == Page.GENRES) {
            clock.poll(Gdx.graphics.getDeltaTime());
            if (!playStandby && standbyWav != null && "fantasy-combat".equals(swap.poll(clock.bar()))) {
                try {
                    Theme combat = load(track("Combat"));
                    clock = LiveClock.start(combat, combat.timeline(), seed);
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

    private TextButton menuButton(String label, Page target) {
        TextButton button = new TextButton(label, skin);
        button.setSize(280, 48);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                show(target);
            }
        });
        return button;
    }

    private void show(Page next) {
        page = next;
        boolean menu = next == Page.MENU;
        boolean genres = next == Page.GENRES;
        boolean soundPage = next == Page.SOUNDS;
        menuSounds.setVisible(menu);
        menuGenres.setVisible(menu);
        back.setVisible(!menu);
        randomize.setVisible(genres);
        seedLabel.setVisible(genres);
        for (TextButton button : buttons.values()) {
            button.setVisible(genres);
        }
        sounds.root().setVisible(soundPage);
        if (menu || soundPage) {
            wantDefault = false;
            queuedWav = null;
            queuedId++;
            nowPlaying = "";
        }
        if (genres && !picked) {
            wantDefault = true;
        } else if (genres && genreChoice != null) {
            byte[] ready = cache.get(genreChoice);
            if (ready != null) {
                handoff(genreChoice, ready, clocks.get(genreChoice));
            }
        }
        if (soundPage && !soundsLoaded) {
            soundsLoaded = true;
            Thread loading = new Thread(sounds::load, "athenacl-sounds");
            loading.setDaemon(true);
            loading.start();
        }
    }

    private void layout() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        float x = width / 2f - 140f;
        float y = height / 2f;
        menuSounds.setPosition(x, y + 12f);
        menuGenres.setPosition(x, y - 52f);
        back.setPosition(16, height - 72f);
        randomize.setPosition(168, height - 72f);
        seedLabel.setPosition(340, height - 60f);
        if (page == Page.SOUNDS) {
            sounds.root().setPosition(16, 88);
            sounds.root().setSize(width - 32f, Math.max(120f, height - 172f));
        }
    }

    private void drawProgress(float width) {
        float barWidth = Math.min(640f, width - 32f);
        double into = trackSeconds <= 0 ? 0 : heardSeconds % trackSeconds;
        float fraction = trackSeconds <= 0 ? 0f : (float) (into / trackSeconds);
        float barY = page == Page.GENRES
                ? 28 + ((TRACKS.length + COLUMNS - 1) / COLUMNS) * 44f
                : 28f;
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, nowPlaying + "  " + timestamp(heardSeconds), 16, barY + 36);
        batch.end();
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.2f, 0.24f, 1f);
        shapes.rect(16, barY, barWidth, 12);
        shapes.setColor(0.45f, 0.78f, 0.86f, 1f);
        shapes.rect(16, barY, barWidth * fraction, 12);
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
        genreChoice = track.name;
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
                long used = seed;
                byte[] wav = cache.get(track.name);
                LiveClock next = clocks.get(track.name);
                if (wav == null) {
                    Gdx.app.postRunnable(() -> showRendering(track.name));
                    next = open(track, used);
                    wav = ThemeMix.wav(load(track), next.performance().score(), states(next), used);
                    if (used != seed) {
                        continue;
                    }
                    cache.put(track.name, wav);
                    clocks.put(track.name, next);
                    if (used != seed) {
                        cache.remove(track.name);
                        clocks.remove(track.name);
                        continue;
                    }
                    Gdx.app.postRunnable(this::showLabels);
                }
                if (wanted == track) {
                    handoffTrack(track.name, wav, next);
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

    private static LiveClock open(Track track, long seed) throws Exception {
        Theme theme = load(track);
        List<Theme.Control> controls = track.timeline == null
                ? theme.timeline()
                : ControlLog.readTimeline(Path.of(track.timeline));
        return LiveClock.start(theme, controls, seed);
    }

    /** Picks a new seed, drops the recordings made with the old one, and plays the current track again. */
    private void randomizeSeed() {
        long next = seed;
        while (next == seed) {
            next = 1 + ThreadLocalRandom.current().nextInt(9999);
        }
        seed = next;
        seedLabel.setText("Seed " + seed);
        cache.clear();
        clocks.clear();
        standbyWav = null;
        playStandby = false;
        String name = genreChoice != null ? genreChoice : "Ambush";
        request(track(name));
    }

    /** Theme file, or a short tour of a preset. Playback always uses {@link #KIT}. */
    private static Theme load(Track track) throws Exception {
        Theme theme;
        if (track.preset != null) {
            Presets.Preset preset = Presets.get(track.preset);
            String yaml = """
                    id: %s
                    preset: %s
                    bars: 14
                    meter: "%s"
                    timeline:
                      - {bar: 1, state: peaceful, intensity: 0.65}
                      - {bar: 5, state: combat, intensity: 1}
                      - {bar: 9, state: partying, intensity: 0.85}
                    """.formatted(track.preset, track.preset, preset.meter());
            theme = Themes.parse(track.preset + ".yaml", yaml);
        } else {
            theme = Themes.load(Path.of(track.theme));
        }
        return theme.withKits(List.of(KIT));
    }

    private static Track track(String name) {
        for (Track track : TRACKS) {
            if (track.name.equals(name)) {
                return track;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private void handoff(String name, byte[] wav, LiveClock next) {
        queuedName = name;
        queuedClock = next;
        queuedWav = wav;
        queuedId++;
    }

    /** A finished genre recording stays cached while the menu or the sound list is open. */
    private void handoffTrack(String name, byte[] wav, LiveClock next) {
        if (page != Page.GENRES) {
            return;
        }
        handoff(name, wav, next);
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
            long used = seed;
            Theme combat = load(track("Combat"));
            LiveClock next = LiveClock.start(combat, combat.timeline(), used);
            byte[] wav = ThemeMix.wav(combat, next.performance().score(), states(next), used);
            if (used != seed) {
                return;
            }
            standbyWav = wav;
            cache.put("Combat", wav);
            clocks.put("Combat", next);
            swap.preloaded();
            Track asked = wanted;
            if (asked != null && "Combat".equals(asked.name)) {
                handoffTrack("Combat", wav, next);
                wanted = null;
                Gdx.app.postRunnable(this::showLabels);
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void playScore() {
        try {
            Theme theme = load(TRACKS[0]);
            long used = seed;
            byte[] wav = ThemeMix.wav(theme, clock.performance().score(), states(clock), used);
            if (used == seed) {
                cache.put("Ambush", wav);
            }
            preload = new Thread(this::preloadCombat, "athenacl-kit");
            preload.setDaemon(true);
            preload.start();
            String name = "Ambush";
            int id = queuedId;
            boolean fresh = false;
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
                    wantDefault = false;
                    fresh = true;
                }
                if (wantDefault && page == Page.GENRES && !picked && wav != null) {
                    name = "Ambush";
                    id = queuedId;
                    wantDefault = false;
                    fresh = true;
                }
                if (fresh && wav != null) {
                    fresh = false;
                    play(wav, name, id);
                    continue;
                }
                if (page == Page.GENRES && !picked && playStandby && standbyWav != null && wav != standbyWav) {
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

    private void preview(SoundBrowser.Choice sound) {
        error = null;
        Thread playing = new Thread(() -> {
            try {
                byte[] wav = SoundBrowser.preview(sound.file(), sound.preset());
                if (page != Page.SOUNDS) {
                    return;
                }
                handoff(sound.preset().name(), wav, clock);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }, "athenacl-preview");
        playing.setDaemon(true);
        playing.start();
    }

    private void play(byte[] wav, String name, int id) throws Exception {
        nowPlaying = name;
        heardSeconds = 0;
        long heardFrames = 0;
        while (!stop && queuedId == id) {
            if (leaveFor(wav)) {
                return;
            }
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
                AudioFormat format = audio.getFormat();
                int channels = Math.max(1, format.getChannels());
                float rate = format.getFrameRate();
                long frames = audio.getFrameLength();
                trackSeconds = frames > 0 && rate > 0 ? frames / rate : 0;
                byte[] buffer = new byte[4096];
                int read;
                while (!stop && queuedId == id && (read = audio.read(buffer)) > 0) {
                    if (leaveFor(wav)) {
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
    }

    /** Another button, or the ambush handing off to combat, replaces this recording. */
    private boolean leaveFor(byte[] wav) {
        if (queuedWav != null && wav != queuedWav) {
            return true;
        }
        return page == Page.GENRES && !picked && wav != standbyWav && playStandby && standbyWav != null
                && queuedWav == null;
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
