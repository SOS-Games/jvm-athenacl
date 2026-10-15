// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.instruments.Kit;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCatalog;
import io.github.sosgames.athenacl.render.soundbank.SoundbankInstaller;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Probes a kit and writes {@code <kit>.profile.json}. A second run with the same
 * soundfont, program, probe version, and JDK reads that file instead of rendering.
 * The hash is the file bytes. It does not include the wall clock.
 */
public final class InstrumentProfile {
    public static final String SCHEMA = "athenacl.instrument-profile/1";
    private static final int[] VELOCITIES = {40, 80, 120};
    private static final int[] INTERVALS = {12, 7, 4, 1};
    private static final ObjectMapper JSON = new ObjectMapper();

    private InstrumentProfile() {
    }

    public record Row(
            String name,
            String verdict,
            List<String> reasons,
            List<String> tags,
            double pitchErrorCents,
            double attackMs,
            String source,
            double centroidRatio,
            double flatness,
            double decayMs,
            boolean died,
            double roughness,
            double loudnessLu,
            String soundClass,
            int octaveOffset,
            String note) {
        public Row {
            reasons = List.copyOf(reasons);
            tags = List.copyOf(tags);
        }
    }

    public record Report(
            String kitId,
            List<Row> rows,
            int bad,
            boolean cached,
            String hash,
            Path file) {
        public Report {
            rows = List.copyOf(rows);
        }

        public int analyzed() {
            return rows.size();
        }
    }

    public static Report analyze(Path kitFile, Path outDir) throws IOException, KitException {
        return analyze(kitFile, outDir, List.of(), null);
    }

    /**
     * Reads a profile that is already on disk. A missing or stale file returns null.
     * Lint calls {@link #analyze} in that case and writes one row per instrument.
     */
    public static Report openCached(Path kitFile, Path outDir) throws IOException, KitException {
        Kit kit = Kits.load(kitFile);
        Thresholds thresholds = Thresholds.load(Path.of("instruments", "analysis.yaml"));
        return readCache(profileFile(outDir, kit.id()), kit, kit.instruments(), thresholds.probeVersion());
    }

    public static Report analyze(Path kitFile, Path outDir, List<String> only, Integer probeVersion)
            throws IOException, KitException {
        Kit kit = Kits.load(kitFile);
        Thresholds thresholds = Thresholds.load(Path.of("instruments", "analysis.yaml"));
        int version = probeVersion == null ? thresholds.probeVersion() : probeVersion;
        List<Kit.Instrument> instruments = select(kit, only);
        Path file = profileFile(outDir, kit.id());
        boolean partial = !only.isEmpty() && instruments.size() != kit.instruments().size();
        if (!partial) {
            Report cached = readCache(file, kit, instruments, version);
            if (cached != null) {
                return cached;
            }
        }
        List<Row> rows = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        List<Integer> banks = new ArrayList<>();
        List<Integer> programs = new ArrayList<>();
        for (Kit.Instrument instrument : instruments) {
            Resolved resolved = resolve(kit, instrument);
            hashes.add(resolved.sha256);
            banks.add(resolved.bank);
            programs.add(resolved.program);
            if (instrument.analysisVerdict() != null) {
                String cls = instrument.soundClass() == null ? SoundClass.SUSTAINED : instrument.soundClass();
                rows.add(new Row(instrument.name(), instrument.analysisVerdict(), List.of(), List.of(),
                        0, 0, "override", 0, 0, 0, false, 0, 0, cls, 0, ""));
                continue;
            }
            rows.add(measure(instrument, resolved, thresholds));
        }
        int bad = 0;
        for (Row row : rows) {
            if ("bad".equals(row.verdict())) {
                bad++;
            }
        }
        String json = json(kit.id(), version, instruments, hashes, banks, programs, rows);
        String hash;
        if (partial) {
            hash = EventLog.sha256(json.getBytes(StandardCharsets.UTF_8));
        } else {
            write(file, json);
            hash = EventLog.sha256(Files.readAllBytes(file));
        }
        return new Report(kit.id(), rows, bad, false, hash, partial ? null : file);
    }

    public static Descriptors.Measurement probe(Path kitFile, String name, int midi, int velocity)
            throws IOException, KitException {
        Kit kit = Kits.load(kitFile);
        Kit.Instrument instrument = kit.instrument(name);
        if (instrument == null) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "argv[2]", "instrument not found", "Pass a name from the kit."));
        }
        Thresholds thresholds = Thresholds.load(Path.of("instruments", "analysis.yaml"));
        Resolved resolved = resolve(kit, instrument);
        float[] audio = ProbeRenderer.render(resolved.file, resolved.bank, resolved.program,
                instrument.percussion(), new int[] {midi}, velocity);
        return Descriptors.measure(audio, ProbeRenderer.RATE, hz(midi),
                thresholds.loopClickSigma(), thresholds.clipDbfs(), thresholds.inharmonicCents());
    }

    public static String text(Row row) {
        List<String> lines = new ArrayList<>();
        lines.add(row.name() + " is " + row.verdict() + ".");
        if (row.soundClass() != null && !row.soundClass().isBlank()) {
            lines.add("Class: " + row.soundClass() + ".");
        }
        if (row.octaveOffset() != 0) {
            lines.add("Octave offset is " + row.octaveOffset() + ".");
        }
        if (!row.reasons().isEmpty()) {
            lines.add("Reasons: " + String.join(", ", row.reasons()) + ".");
        }
        if (!row.tags().isEmpty()) {
            lines.add("Tags: " + String.join(", ", row.tags()) + ".");
        }
        if (row.attackMs() >= 80) {
            lines.add("It has a slow attack.");
        }
        if (row.tags().contains("bright")) {
            lines.add("The tone is bright.");
        }
        if (row.tags().contains("plucky")) {
            lines.add("The note dies quickly.");
        }
        if (row.tags().contains("noisy")) {
            lines.add("The sound is noisy.");
        }
        if (hasReason(row, "inharmonicity")) {
            lines.add("The partials do not line up.");
        }
        if (hasReason(row, "clipping")) {
            lines.add("The waveform is clipped.");
        }
        if (hasReason(row, "loopClicks")) {
            lines.add("The loop clicks.");
        }
        if (row.note() != null && row.note().startsWith("loud")) {
            lines.add("The note is loud.");
        }
        if (lines.size() > 8) {
            lines = lines.subList(0, 8);
        }
        return String.join("\n", lines);
    }

    public static String rowLine(Row row, Boolean cached) {
        StringBuilder sb = new StringBuilder();
        sb.append("instrument=").append(row.name());
        sb.append(" verdict=").append(row.verdict());
        if (!row.reasons().isEmpty()) {
            sb.append(" reasons=").append(String.join(",", row.reasons()));
        }
        if (!row.tags().isEmpty()) {
            sb.append(" tags=").append(String.join(",", row.tags()));
        }
        sb.append(" pitch.errorCents=").append(num(row.pitchErrorCents(), 1));
        if (row.soundClass() != null && !row.soundClass().isBlank()) {
            sb.append(" class=").append(row.soundClass());
        }
        sb.append(" octave_offset=").append(row.octaveOffset());
        if (row.note() != null && row.note().startsWith("loud")) {
            sb.append(" note=loud");
            int trim = row.note().indexOf("trim=");
            if (trim >= 0) {
                sb.append(' ').append(row.note().substring(trim));
            }
        }
        if ("override".equals(row.source())) {
            sb.append(" source=override");
        }
        if (cached != null) {
            sb.append(" cached=").append(cached);
        }
        return sb.toString();
    }

    private static Row measure(Kit.Instrument instrument, Resolved resolved, Thresholds thresholds)
            throws IOException {
        List<Integer> pitches = pitches(instrument);
        int mid = pitches.get(pitches.size() / 2);
        List<Double> pitchErrors = new ArrayList<>();
        List<Double> octaves = new ArrayList<>();
        List<Double> centroids = new ArrayList<>();
        List<Double> attacks = new ArrayList<>();
        List<Double> decays = new ArrayList<>();
        int died = 0;
        int takes = 0;
        List<Double> inharmonics = new ArrayList<>();
        double flatness = 0;
        int clips = 0;
        int clicks = 0;
        double roughness = 0;
        double peakDb = -120;
        Double quiet = null;
        Double loud = null;
        for (int pitch : pitches) {
            for (int velocity : VELOCITIES) {
                Descriptors.Measurement measurement = renderOne(instrument, resolved, thresholds,
                        new int[] {pitch}, velocity);
                pitchErrors.add(measurement.pitchErrorCents());
                octaves.add((double) measurement.octaveOffset());
                centroids.add(measurement.centroidRatio());
                attacks.add(measurement.attackMs());
                decays.add(measurement.decayMs());
                takes++;
                if (measurement.died()) {
                    died++;
                }
                inharmonics.add(measurement.inharmonicEnergy());
                flatness = Math.max(flatness, measurement.flatness());
                clips = Math.max(clips, measurement.clipCount());
                clicks = Math.max(clicks, measurement.loopClicks());
                roughness = Math.max(roughness, measurement.roughness());
                peakDb = Math.max(peakDb, measurement.peakDbfs());
                if (pitch == mid && velocity == 40) {
                    quiet = measurement.loudnessLu();
                }
                if (pitch == mid && velocity == 120) {
                    loud = measurement.loudnessLu();
                }
            }
        }
        if (!instrument.percussion()) {
            for (int interval : INTERVALS) {
                int upper = Math.min(127, mid + interval);
                Descriptors.Measurement measurement = renderOne(instrument, resolved, thresholds,
                        new int[] {mid, upper}, 80);
                roughness = Math.max(roughness, measurement.roughness());
            }
        }
        double inharmonic = median(inharmonics);
        double pitch = quantize(median(pitchErrors), 1);
        int octave = (int) Math.round(median(octaves));
        double centroid = median(centroids);
        double attack = median(attacks);
        double decay = median(decays);
        boolean noisy = flatness >= thresholds.flatness();
        boolean mostlyDied = takes > 0 && died * 2 >= takes;
        String soundClass = SoundClass.classify(instrument.name(), instrument.program(),
                instrument.percussion(), instrument.soundClass(), attack, decay, mostlyDied, flatness);
        ClassVerdict.Decision decision = ClassVerdict.judge(soundClass, pitch, inharmonic, flatness,
                clips, clicks, roughness, mostlyDied, peakDb, thresholds);
        List<String> reasons = new ArrayList<>(decision.reasons());
        List<String> soft = new ArrayList<>(decision.soft());
        if (quiet != null && loud != null && Double.isFinite(quiet) && Double.isFinite(loud)
                && loud - quiet < thresholds.velocityMinLu()) {
            soft.add(ClassVerdict.rule("velocity", soundClass));
        }
        List<String> tags = new ArrayList<>();
        if (centroid >= thresholds.brightRatio()) {
            tags.add("bright");
        } else if (centroid > 0 && centroid <= thresholds.warmRatio()) {
            tags.add("warm");
        }
        if (attack >= thresholds.padAttackMs()) {
            tags.add("pad");
        }
        if (attack <= thresholds.pluckAttackMs() && takes > 0 && died * 2 >= takes
                && decay > 0 && decay <= thresholds.pluckDecayMs()) {
            tags.add("plucky");
        }
        if (noisy) {
            tags.add("noisy");
        }
        if (decision.note().startsWith("loud")) {
            tags.add("loud");
        }
        String verdict = !reasons.isEmpty() ? "bad" : !soft.isEmpty() ? "caution" : "ok";
        double level = loud != null && Double.isFinite(loud) ? loud : 0;
        return new Row(instrument.name(), verdict, reasons, tags, pitch, quantize(attack, 1), "measured",
                quantize(centroid, 2), quantize(flatness, 2), quantize(decay, 1), mostlyDied,
                quantize(roughness, 2), quantize(level, 1), soundClass, octave, decision.note());
    }

    private static Descriptors.Measurement renderOne(Kit.Instrument instrument, Resolved resolved,
                                                      Thresholds thresholds, int[] notes, int velocity)
            throws IOException {
        float[] audio = ProbeRenderer.render(resolved.file, resolved.bank, resolved.program,
                instrument.percussion(), notes, velocity);
        double nominal = hz(notes[0]);
        return Descriptors.measure(audio, ProbeRenderer.RATE, nominal,
                thresholds.loopClickSigma(), thresholds.clipDbfs(), thresholds.inharmonicCents());
    }

    private static List<Integer> pitches(Kit.Instrument instrument) {
        if (instrument.percussion() && instrument.keyLow() == null) {
            return List.of(36, 38, 42);
        }
        int low = instrument.keyLow() == null ? 48 : PitchName.midi(instrument.keyLow());
        int high = instrument.keyHigh() == null ? 84 : PitchName.midi(instrument.keyHigh());
        if (high < low) {
            int swap = low;
            low = high;
            high = swap;
        }
        List<Integer> list = new ArrayList<>();
        for (int pitch = low; pitch <= high; pitch += 6) {
            list.add(pitch);
        }
        if (list.isEmpty()) {
            list.add(low);
        } else if (list.get(list.size() - 1) != high && high - low >= 6) {
            list.add(high);
        }
        return list;
    }

    private static Report readCache(Path file, Kit kit, List<Kit.Instrument> instruments, int version)
            throws IOException, KitException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        if (!SCHEMA.equals(root.path("schema").asText())
                || !kit.id().equals(root.path("kit").asText())
                || root.path("probeVersion").asInt() != version
                || root.path("jdk").asInt() != jdk()) {
            return null;
        }
        JsonNode rows = root.path("instruments");
        if (!rows.isArray() || rows.size() != instruments.size()) {
            return null;
        }
        List<Row> parsed = new ArrayList<>();
        for (int i = 0; i < instruments.size(); i++) {
            Kit.Instrument instrument = instruments.get(i);
            JsonNode row = rows.get(i);
            Resolved resolved = resolve(kit, instrument);
            if (!instrument.name().equals(row.path("name").asText())
                    || !resolved.sha256.equalsIgnoreCase(row.path("sha256").asText())
                    || resolved.bank != row.path("bank").asInt()
                    || resolved.program != row.path("program").asInt()) {
                return null;
            }
            List<String> reasons = texts(row.path("reasons"));
            List<String> tags = texts(row.path("tags"));
            parsed.add(new Row(
                    instrument.name(),
                    row.path("verdict").asText("ok"),
                    reasons,
                    tags,
                    row.path("pitch.errorCents").asDouble(0),
                    row.path("attack.ms").asDouble(0),
                    row.path("source").asText("measured"),
                    row.path("centroid.ratio").asDouble(0),
                    row.path("flatness").asDouble(0),
                    row.path("decay.ms").asDouble(0),
                    row.path("died").asBoolean(false),
                    row.path("roughness").asDouble(0),
                    row.path("loudness.lufs").asDouble(0),
                    row.path("class").asText(SoundClass.SUSTAINED),
                    row.path("octave_offset").asInt(0),
                    row.path("note").asText("")));
        }
        int bad = 0;
        for (Row row : parsed) {
            if ("bad".equals(row.verdict())) {
                bad++;
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        return new Report(kit.id(), parsed, bad, true, EventLog.sha256(bytes), file);
    }

    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static String json(String kitId, int version, List<Kit.Instrument> instruments,
                               List<String> hashes, List<Integer> banks, List<Integer> programs,
                               List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schema\": \"").append(SCHEMA).append("\",\n");
        sb.append("  \"kit\": \"").append(escape(kitId)).append("\",\n");
        sb.append("  \"probeVersion\": ").append(version).append(",\n");
        sb.append("  \"jdk\": ").append(jdk()).append(",\n");
        sb.append("  \"instruments\": [\n");
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escape(row.name())).append("\",\n");
            sb.append("      \"sha256\": \"").append(hashes.get(i)).append("\",\n");
            sb.append("      \"bank\": ").append(banks.get(i)).append(",\n");
            sb.append("      \"program\": ").append(programs.get(i)).append(",\n");
            sb.append("      \"verdict\": \"").append(escape(row.verdict())).append("\",\n");
            sb.append("      \"reasons\": ").append(array(row.reasons())).append(",\n");
            sb.append("      \"tags\": ").append(array(row.tags())).append(",\n");
            sb.append("      \"pitch.errorCents\": ").append(num(row.pitchErrorCents(), 1)).append(",\n");
            sb.append("      \"attack.ms\": ").append(num(row.attackMs(), 1)).append(",\n");
            sb.append("      \"centroid.ratio\": ").append(num(row.centroidRatio(), 2)).append(",\n");
            sb.append("      \"flatness\": ").append(num(row.flatness(), 2)).append(",\n");
            sb.append("      \"decay.ms\": ").append(num(row.decayMs(), 1)).append(",\n");
            sb.append("      \"died\": ").append(row.died()).append(",\n");
            sb.append("      \"roughness\": ").append(num(row.roughness(), 2)).append(",\n");
            sb.append("      \"loudness.lufs\": ").append(num(row.loudnessLu(), 1)).append(",\n");
            sb.append("      \"class\": \"").append(escape(row.soundClass() == null ? "" : row.soundClass())).append("\",\n");
            sb.append("      \"octave_offset\": ").append(row.octaveOffset()).append(",\n");
            sb.append("      \"note\": \"").append(escape(row.note() == null ? "" : row.note())).append("\",\n");
            sb.append("      \"source\": \"").append(escape(row.source())).append("\"\n");
            sb.append("    }");
            if (i + 1 < rows.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String array(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(Path file, String json) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path profileFile(Path outDir, String kitId) {
        Path dir = outDir == null ? Path.of("instruments", "profiles") : outDir;
        return dir.resolve(kitId + ".profile.json");
    }

    private static List<Kit.Instrument> select(Kit kit, List<String> only) throws KitException {
        if (only == null || only.isEmpty()) {
            return kit.instruments();
        }
        List<Kit.Instrument> chosen = new ArrayList<>();
        for (String name : only) {
            Kit.Instrument instrument = kit.instrument(name.trim());
            if (instrument == null) {
                throw new KitException(new KitException.Issue(
                        "NOT_FOUND", "--instruments", "instrument not found",
                        "Pass a name from the kit."));
            }
            chosen.add(instrument);
        }
        return chosen;
    }

    private record Resolved(Path file, String sha256, int bank, int program) {
    }

    private static Resolved resolve(Kit kit, Kit.Instrument instrument) throws IOException, KitException {
        Kit.Source source = kit.source(instrument.source());
        if (source == null) {
            throw new KitException(new KitException.Issue(
                    "KIT_UNKNOWN_SOURCE", "/instruments/" + instrument.name() + "/source",
                    "source is not in this kit",
                    "Name a source from the sources block."));
        }
        Path file = source.path();
        if (file == null && source.catalogId() != null) {
            SoundbankCatalog.Entry entry = SoundbankCatalog.byId(source.catalogId());
            if (entry != null) {
                file = SoundbankPaths.cache(null).resolve(entry.fileName());
            }
        }
        if (file == null || !Files.isRegularFile(file)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "/sources/" + source.key(),
                    "soundfont not found",
                    "Install the soundfont or set path."));
        }
        String sha256 = SoundbankInstaller.sha256(file);
        if (source.sha256() != null && !source.sha256().isBlank()
                && !sha256.equalsIgnoreCase(source.sha256())) {
            throw new KitException(new KitException.Issue(
                    "KIT_CHECKSUM", "/sources/" + source.key(),
                    "checksum does not match",
                    "The file hashed to " + sha256 + "."));
        }
        int program = instrument.percussion() ? instrument.drumkit() : instrument.program();
        return new Resolved(file, sha256, instrument.bank(), program);
    }

    private static double hz(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static double quantize(double value, int places) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private static String num(double value, int places) {
        return String.format(Locale.US, "%." + places + "f", quantize(value, places));
    }

    private static boolean hasReason(Row row, String code) {
        for (String reason : row.reasons()) {
            if (reason.equals(code) || reason.startsWith(code + "@")) {
                return true;
            }
        }
        return false;
    }

    private static int jdk() {
        return Runtime.version().feature();
    }
}
