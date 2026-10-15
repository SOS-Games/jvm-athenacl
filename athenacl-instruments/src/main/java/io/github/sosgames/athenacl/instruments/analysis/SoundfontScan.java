// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.sosgames.athenacl.render.GervillStreams;
import io.github.sosgames.athenacl.render.soundbank.InstrumentIndex;
import io.github.sosgames.athenacl.render.soundbank.SoundbankInstaller;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Plays one dry note for every preset in a soundfont and stores the same kind of row a kit
 * profile uses. The file is reused while the soundfont bytes and the probe version stay the
 * same, so opening the sound list does not render the bank again. A kit profile still uses
 * the full pitch grid; this scan is one note so a whole installed bank can finish.
 */
public final class SoundfontScan {
    public static final String SCHEMA = "athenacl.soundfont-profile/1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int NOTE = 60;
    private static final int DRUM = 36;
    private static final int VELOCITY = 80;

    private SoundfontScan() {
    }

    /** One analyzed preset. {@code row} is what the sound page prints. */
    public record Hit(int bank, int program, boolean percussion, String name, InstrumentProfile.Row row,
                      boolean levelKnown) {
    }

    public static String key(int bank, int program, boolean percussion) {
        return bank + "/" + program + "/" + (percussion ? "d" : "m");
    }

    public static Path directory() {
        return Path.of("instruments", "profiles", "soundfonts");
    }

    /** Rows already stored for this file. Empty when the cache is missing or the soundfont changed. */
    public static List<Hit> open(Path soundfont, Path outDir, String id) throws IOException {
        Cache cache = read(file(outDir, id), soundfont);
        return cache == null ? List.of() : List.copyOf(cache.hits.values());
    }

    /**
     * Renders presets that are not in the cache yet, and any saved preset that has no level yet.
     * {@code onRow} runs after each new preset, on this thread. A second call with a full cache
     * does not open the synth.
     */
    public static List<Hit> fill(Path soundfont, Path outDir, String id, Consumer<Hit> onRow)
            throws IOException, InvalidMidiDataException {
        Path dest = file(outDir, id);
        Thresholds thresholds = Thresholds.load(Path.of("instruments", "analysis.yaml"));
        String sha = SoundbankInstaller.sha256(soundfont);
        Cache cache = read(dest, soundfont);
        LinkedHashMap<String, Hit> hits = cache == null || cache.version != thresholds.probeVersion()
                ? new LinkedHashMap<>()
                : cache.hits;
        List<InstrumentIndex.Preset> presets = InstrumentIndex.list(soundfont);
        List<InstrumentIndex.Preset> pending = new ArrayList<>();
        for (InstrumentIndex.Preset preset : presets) {
            Hit saved = hits.get(key(preset.bank(), preset.program(), preset.percussion()));
            if (saved == null || !saved.levelKnown()) {
                pending.add(preset);
            }
        }
        if (!pending.isEmpty()) {
            long cursor = 0;
            try (GervillStreams streams = GervillStreams.openProbe(soundfont)) {
                for (InstrumentIndex.Preset preset : pending) {
                    Hit hit = probe(streams, cursor, preset, thresholds);
                    cursor += ProbeRenderer.spanUs();
                    hits.put(key(hit.bank(), hit.program(), hit.percussion()), hit);
                    write(dest, id, sha, thresholds.probeVersion(), hits);
                    if (onRow != null) {
                        onRow.accept(hit);
                    }
                }
            } catch (MidiUnavailableException e) {
                throw new IOException(e.getMessage() == null ? "probe failed" : e.getMessage(), e);
            }
        }
        List<Hit> ordered = new ArrayList<>();
        for (InstrumentIndex.Preset preset : presets) {
            Hit hit = hits.get(key(preset.bank(), preset.program(), preset.percussion()));
            if (hit != null) {
                ordered.add(hit);
            }
        }
        return ordered;
    }

    private static Hit probe(GervillStreams streams, long cursor, InstrumentIndex.Preset preset,
                             Thresholds thresholds) {
        String name = preset.name() == null || preset.name().isBlank()
                ? "program " + preset.program() : preset.name().trim();
        int note = preset.percussion() ? DRUM : NOTE;
        try {
            float[] audio = ProbeRenderer.renderAt(streams, cursor, preset.bank(), preset.program(),
                    preset.percussion(), new int[] {note}, VELOCITY);
            Descriptors.Measurement measurement = Descriptors.measure(audio, ProbeRenderer.RATE, hz(note),
                    thresholds.loopClickSigma(), thresholds.clipDbfs(), thresholds.inharmonicCents());
            InstrumentProfile.Row row = InstrumentProfile.summarize(name, preset.program(), preset.percussion(),
                    null, measurement.pitchErrorCents(), measurement.octaveOffset(),
                    measurement.centroidRatio(), measurement.attackMs(), measurement.decayMs(),
                    measurement.died(), measurement.flatness(), measurement.inharmonicEnergy(),
                    measurement.clipCount(), measurement.loopClicks(), measurement.roughness(),
                    measurement.peakDbfs(), null, measurement.loudnessLu(), thresholds);
            return new Hit(preset.bank(), preset.program(), preset.percussion(), name, row, true);
        } catch (Exception e) {
            return new Hit(preset.bank(), preset.program(), preset.percussion(), name,
                    InstrumentProfile.unfinished(name), true);
        }
    }

    private static double hz(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private static Path file(Path outDir, String id) {
        Path dir = outDir == null ? directory() : outDir;
        String safe = id == null ? "soundfont" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (safe.isBlank()) {
            safe = "soundfont";
        }
        return dir.resolve(safe + ".json");
    }

    private record Cache(int version, LinkedHashMap<String, Hit> hits) {
    }

    private static Cache read(Path file, Path soundfont) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        if (root == null || !SCHEMA.equals(text(root, "schema"))) {
            return null;
        }
        String sha = SoundbankInstaller.sha256(soundfont);
        if (!sha.equalsIgnoreCase(text(root, "sha256"))) {
            return null;
        }
        int version = root.path("probeVersion").asInt(0);
        LinkedHashMap<String, Hit> hits = new LinkedHashMap<>();
        for (JsonNode node : root.path("presets")) {
            int bank = node.path("bank").asInt();
            int program = node.path("program").asInt();
            boolean percussion = node.path("percussion").asBoolean();
            String name = text(node, "name");
            InstrumentProfile.Row row = new InstrumentProfile.Row(
                    text(node, "rowName").isBlank() ? name : text(node, "rowName"),
                    text(node, "verdict"),
                    strings(node.path("reasons")),
                    strings(node.path("tags")),
                    node.path("pitchErrorCents").asDouble(),
                    node.path("attackMs").asDouble(),
                    text(node, "source").isBlank() ? "measured" : text(node, "source"),
                    node.path("centroidRatio").asDouble(),
                    node.path("flatness").asDouble(),
                    node.path("decayMs").asDouble(),
                    node.path("died").asBoolean(),
                    node.path("roughness").asDouble(),
                    node.path("loudnessLu").asDouble(),
                    text(node, "soundClass"),
                    node.path("octaveOffset").asInt(),
                    text(node, "note"));
            boolean levelKnown = node.path("levelKnown").asBoolean(false);
            hits.put(key(bank, program, percussion), new Hit(bank, program, percussion, name, row, levelKnown));
        }
        return new Cache(version, hits);
    }

    private static void write(Path file, String id, String sha, int version, LinkedHashMap<String, Hit> hits)
            throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("schema", SCHEMA);
        root.put("id", id == null ? "" : id);
        root.put("sha256", sha);
        root.put("probeVersion", version);
        ArrayNode presets = root.putArray("presets");
        List<Hit> ordered = new ArrayList<>(hits.values());
        ordered.sort((a, b) -> {
            int bank = Integer.compare(a.bank(), b.bank());
            if (bank != 0) {
                return bank;
            }
            int program = Integer.compare(a.program(), b.program());
            if (program != 0) {
                return program;
            }
            return Boolean.compare(a.percussion(), b.percussion());
        });
        for (Hit hit : ordered) {
            InstrumentProfile.Row row = hit.row();
            ObjectNode node = presets.addObject();
            node.put("bank", hit.bank());
            node.put("program", hit.program());
            node.put("percussion", hit.percussion());
            node.put("name", hit.name());
            node.put("rowName", row.name());
            node.put("verdict", row.verdict());
            ArrayNode reasons = node.putArray("reasons");
            for (String reason : row.reasons()) {
                reasons.add(reason);
            }
            ArrayNode tags = node.putArray("tags");
            for (String tag : row.tags()) {
                tags.add(tag);
            }
            node.put("pitchErrorCents", row.pitchErrorCents());
            node.put("attackMs", row.attackMs());
            node.put("source", row.source());
            node.put("centroidRatio", row.centroidRatio());
            node.put("flatness", row.flatness());
            node.put("decayMs", row.decayMs());
            node.put("died", row.died());
            node.put("roughness", row.roughness());
            node.put("loudnessLu", row.loudnessLu());
            node.put("soundClass", row.soundClass() == null ? "" : row.soundClass());
            node.put("octaveOffset", row.octaveOffset());
            node.put("note", row.note() == null ? "" : row.note());
            node.put("levelKnown", hit.levelKnown());
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.writeString(tmp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.asText(""));
            }
        }
        return values;
    }
}
