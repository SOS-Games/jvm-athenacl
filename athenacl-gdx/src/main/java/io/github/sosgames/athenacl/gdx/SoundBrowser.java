// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import io.github.sosgames.athenacl.instruments.Kit;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.analysis.InstrumentProfile;
import io.github.sosgames.athenacl.instruments.analysis.ProbeRenderer;
import io.github.sosgames.athenacl.instruments.analysis.SoundfontScan;
import io.github.sosgames.athenacl.render.soundbank.InstrumentIndex;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCatalog;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists every preset in every soundfont installed in the cache. The names sit on the left and
 * scroll. The description stays on the right and names the soundfont. A click repeats that one
 * dry note. Analysis for a font is read from disk, then filled in the background for any preset
 * the cache does not have yet.
 */
final class SoundBrowser {
    private static final int DEVICE_RATE = 44100;
    private static final String[] FAMILIES = {
            "piano", "chromatic percussion", "organ", "guitar", "bass", "strings", "ensemble", "brass",
            "reed", "pipe", "synth lead", "synth pad", "synth effects", "ethnic", "percussive", "sound effects"
    };

    private final Table root;
    private final Label description;
    private final Table list;
    private final Skin skin;
    private final Click click;
    private final List<Line> lines = new ArrayList<>();
    private Choice selected;

    /** One preset in one installed soundfont. Playback uses that file, not the other bank. */
    record Choice(String fontId, String font, Path file, InstrumentIndex.Preset preset) {
    }

    @FunctionalInterface
    interface Click {
        void play(Choice sound);
    }

    SoundBrowser(Skin skin, BitmapFont font, Click click) {
        this.skin = skin;
        this.click = click;
        Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);
        description = new Label("Loading the soundpack.", style);
        description.setWrap(true);
        description.setAlignment(Align.topLeft);
        list = new Table();
        ScrollPane sounds = new ScrollPane(list);
        sounds.setFadeScrollBars(false);
        sounds.setScrollingDisabled(true, false);
        root = new Table();
        root.add(sounds).width(Value.percentWidth(0.42f, root)).growY().padRight(16);
        root.add(description).grow().top().pad(8);
        root.setVisible(false);
    }

    Table root() {
        return root;
    }

    void load() {
        try {
            List<Font> fonts = installed();
            Map<String, InstrumentProfile.Row> kitRows = profileRows();
            Map<Integer, String> melodic = new HashMap<>();
            Map<Integer, String> drums = new HashMap<>();
            try {
                Kit kit = Kits.load(Kits.find("gm-lite"));
                for (Kit.Instrument instrument : kit.instruments()) {
                    if (instrument.percussion()) {
                        drums.putIfAbsent(instrument.drumkit(), instrument.name());
                    } else if (instrument.program() >= 0) {
                        melodic.putIfAbsent(instrument.program(), instrument.name());
                    }
                }
            } catch (Exception ignored) {
                // The list still plays. A missing kit profile leaves those rows to the soundfont scan.
            }
            List<Line> built = new ArrayList<>();
            int pending = 0;
            for (Font font : fonts) {
                Map<String, InstrumentProfile.Row> scanned = new HashMap<>();
                try {
                    for (SoundfontScan.Hit hit : SoundfontScan.open(font.file, SoundfontScan.directory(), font.id)) {
                        scanned.put(SoundfontScan.key(hit.bank(), hit.program(), hit.percussion()), hit.row());
                    }
                } catch (Exception ignored) {
                    // A bad cache is filled again below.
                }
                for (InstrumentIndex.Preset preset : font.presets) {
                    Choice sound = new Choice(font.id, font.label, font.file, preset);
                    InstrumentProfile.Row row = scanned.get(
                            SoundfontScan.key(preset.bank(), preset.program(), preset.percussion()));
                    if (row == null && "generaluser".equals(font.id)) {
                        String name = preset.percussion()
                                ? drums.get(preset.program())
                                : melodic.get(preset.program());
                        row = name == null ? null : kitRows.get(name);
                    }
                    boolean waiting = row == null;
                    if (waiting) {
                        pending++;
                    }
                    built.add(new Line(sound, describe(sound, row, waiting), label(sound, row)));
                }
            }
            built.sort(Comparator.comparing((Line line) -> line.sound.font(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(line -> soundName(line.sound.preset()), String.CASE_INSENSITIVE_ORDER));
            String head = count(built) + " presets\nChoose a sound.";
            if (pending > 0) {
                head = count(built) + " presets\nAnalyzing the installed soundfonts.";
            }
            List<Line> shown = built;
            String opening = head;
            Gdx.app.postRunnable(() -> show(shown, opening));
            for (Font font : fonts) {
                SoundfontScan.fill(font.file, SoundfontScan.directory(), font.id, hit -> apply(font, hit));
            }
            Gdx.app.postRunnable(this::refreshHeader);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "The soundpack could not be read." : e.getMessage();
            Gdx.app.postRunnable(() -> description.setText(message));
        }
    }

    private void apply(Font font, SoundfontScan.Hit hit) {
        Gdx.app.postRunnable(() -> {
            for (Line line : lines) {
                InstrumentIndex.Preset preset = line.sound.preset();
                if (!font.id.equals(line.sound.fontId())
                        || preset.bank() != hit.bank()
                        || preset.program() != hit.program()
                        || preset.percussion() != hit.percussion()) {
                    continue;
                }
                line.text = describe(line.sound, hit.row(), false);
                line.label = label(line.sound, hit.row());
                if (line.button != null) {
                    line.button.setText(line.label);
                }
                if (line.sound.equals(selected)) {
                    description.setText(line.text);
                }
            }
        });
    }

    private void refreshHeader() {
        if (selected != null) {
            return;
        }
        description.setText(count(lines) + " presets\nChoose a sound.");
    }

    private static int count(List<Line> rows) {
        return rows.size();
    }

    private void show(List<Line> rows, String head) {
        lines.clear();
        lines.addAll(rows);
        list.clear();
        for (Line line : lines) {
            TextButton button = new TextButton(line.label, skin);
            line.button = button;
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    selected = line.sound;
                    description.setText(line.text);
                    click.play(line.sound);
                }
            });
            list.add(button).growX().height(32).pad(2).row();
        }
        description.setText(head);
    }

    private static final class Line {
        private final Choice sound;
        private String text;
        private String label;
        private TextButton button;

        private Line(Choice sound, String text, String label) {
            this.sound = sound;
            this.text = text;
            this.label = label;
        }
    }

    /** One installed soundfont file, whether or not it is one of the named catalog banks. */
    private record Font(String id, String label, Path file, List<InstrumentIndex.Preset> presets) {
    }

    static byte[] preview(Path bank, InstrumentIndex.Preset preset) throws Exception {
        int note = preset.percussion() ? 36 : 60;
        float[] audio = ProbeRenderer.render(bank, preset.bank(), preset.program(), preset.percussion(),
                new int[] {note}, 80);
        return wav(resample(audio, ProbeRenderer.RATE, DEVICE_RATE), DEVICE_RATE);
    }

    private static List<Font> installed() throws Exception {
        Path cache = SoundbankPaths.cache(null);
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(cache)) {
            try (var listing = Files.list(cache)) {
                listing.filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".sf2") && Files.isRegularFile(path);
                }).forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        List<Font> fonts = new ArrayList<>();
        for (Path file : files) {
            SoundbankCatalog.Entry known = null;
            for (SoundbankCatalog.Entry entry : SoundbankCatalog.all()) {
                if (entry.fileName().equalsIgnoreCase(file.getFileName().toString())) {
                    known = entry;
                    break;
                }
            }
            String id = known == null ? stem(file) : known.id();
            String label = known == null ? stem(file) : fontLabel(known.id());
            fonts.add(new Font(id, label, file, InstrumentIndex.list(file)));
        }
        if (fonts.isEmpty()) {
            throw new java.io.IOException("Install a soundfont. The cache is " + cache + ".");
        }
        return fonts;
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String fontLabel(String id) {
        if ("generaluser".equals(id)) {
            return "GeneralUser GS";
        }
        if ("musescore-general".equals(id)) {
            return "MuseScore General";
        }
        return id;
    }

    private static Map<String, InstrumentProfile.Row> profileRows() {
        Map<String, InstrumentProfile.Row> rows = new HashMap<>();
        try {
            InstrumentProfile.Report report = InstrumentProfile.openCached(
                    Kits.find("gm-lite"), Path.of("instruments", "profiles"));
            if (report == null) {
                return rows;
            }
            for (InstrumentProfile.Row row : report.rows()) {
                rows.put(row.name(), row);
            }
        } catch (Exception ignored) {
            return rows;
        }
        return rows;
    }

    private static String describe(Choice sound, InstrumentProfile.Row row, boolean waiting) {
        InstrumentIndex.Preset preset = sound.preset();
        String name = soundName(preset);
        String head = name + "\nSoundfont: " + sound.font();
        if (row == null) {
            String state = waiting ? "Analyzing this preset." : "This preset has not been analyzed.";
            return head + "\n" + family(preset) + "\n" + state;
        }
        return head + "\n" + InstrumentProfile.text(row);
    }

    private static String label(Choice sound, InstrumentProfile.Row row) {
        InstrumentIndex.Preset preset = sound.preset();
        String name = soundName(preset);
        String prefix = sound.font() + "    " + name;
        if (row == null) {
            return prefix + "    " + family(preset);
        }
        String detail = row.soundClass() == null || row.soundClass().isBlank() ? row.verdict() : row.soundClass();
        if (row.verdict() != null && !row.verdict().isBlank() && detail != null && !detail.equals(row.verdict())) {
            detail = detail + ", " + row.verdict();
        }
        if (row.octaveOffset() != 0) {
            detail = detail + ", octave " + row.octaveOffset();
        }
        if (detail == null || detail.isBlank()) {
            return prefix;
        }
        return prefix + "    " + detail;
    }

    private static String soundName(InstrumentIndex.Preset preset) {
        if (preset.name() == null || preset.name().isBlank()) {
            return "preset";
        }
        return preset.name();
    }

    private static String family(InstrumentIndex.Preset preset) {
        if (preset.percussion()) {
            return "drum kit";
        }
        int index = preset.program() / 8;
        if (index < 0 || index >= FAMILIES.length) {
            return "preset";
        }
        return FAMILIES[index];
    }

    private static float[] resample(float[] input, int from, int to) {
        if (from == to || input.length == 0) {
            return input;
        }
        int length = (int) ((long) input.length * to / from);
        float[] output = new float[Math.max(1, length)];
        for (int i = 0; i < output.length; i++) {
            double position = i * (from / (double) to);
            int left = (int) position;
            int right = Math.min(input.length - 1, left + 1);
            double fraction = position - left;
            output[i] = (float) (input[left] * (1.0 - fraction) + input[right] * fraction);
        }
        return output;
    }

    private static byte[] wav(float[] samples, int rate) throws Exception {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            float sample = Math.max(-1f, Math.min(1f, samples[i]));
            short value = (short) Math.round(sample * 32767f);
            pcm[i * 2] = (byte) value;
            pcm[i * 2 + 1] = (byte) (value >> 8);
        }
        int data = pcm.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + data);
        out.write(new byte[] {'R', 'I', 'F', 'F'});
        writeInt(out, 36 + data);
        out.write(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, rate);
        writeInt(out, rate * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        out.write(new byte[] {'d', 'a', 't', 'a'});
        writeInt(out, data);
        out.write(pcm);
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
        out.write(value >> 16);
        out.write(value >> 24);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
    }
}
