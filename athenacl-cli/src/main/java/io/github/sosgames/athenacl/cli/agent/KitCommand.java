// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.Suggestions;
import io.github.sosgames.athenacl.instruments.Kit;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.KitSession;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.analysis.InstrumentProfile;
import io.github.sosgames.athenacl.render.soundbank.InstrumentIndex;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankRejected;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;
import picocli.CommandLine;

import javax.sound.midi.InvalidMidiDataException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code kit validate} checks checksums, licenses, and slots.
 * {@code kit analyze} probes each instrument and writes a profile.
 * {@code kit describe} prints one instrument's verdict from that profile.
 * {@code kit scan} lists every preset in a soundfont.
 */
@CommandLine.Command(name = "kit", description = "Validate, analyze, or scan an instrument kit.",
        subcommands = {KitCommand.Validate.class, KitCommand.Analyze.class, KitCommand.Describe.class,
                KitCommand.Scan.class,
                KitAnalysisCommands.Lint.class, KitAnalysisCommands.Compat.class, KitAnalysisCommands.Fit.class})
public class KitCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "kit";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: kit validate <kit.yaml> | kit analyze <kit.yaml> | kit describe <name> | kit scan <soundfont> | kit lint <theme.yaml>",
                "Try kit scan generaluser.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "validate", description = "Check sources, licenses, and slot assignment.")
    public static class Validate implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<kit.yaml>")
        Path file;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;
        @CommandLine.Option(names = "--allow-unlicensed")
        boolean allowUnlicensed;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit validate";
            if (file == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: kit validate <kit.yaml>",
                        "Try kit validate instruments/gm-lite.yaml.");
            }
            try {
                KitSession session = KitSession.open(file, SoundbankPaths.cache(cache), allowUnlicensed);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("kit", session.kit().id());
                fields.put("sources", Integer.toString(session.sourceCount()));
                fields.put("instruments", Integer.toString(session.instruments().size()));
                fields.put("slots.collisions", Integer.toString(session.collisions()));
                fields.put("fallbacksTaken", Integer.toString(session.fallbacksTaken()));
                fields.put("warnings", Integer.toString(session.warnings().size()));
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.kit/1",
                        new LinkedHashMap<>(fields), List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                        "Check that the kit file and the soundfont cache are readable.");
            }
        }
    }

    @CommandLine.Command(name = "analyze", description = "Probe a kit and write its profile.")
    public static class Analyze implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<kit.yaml>")
        Path file;
        @CommandLine.Option(names = "--out", paramLabel = "<dir>")
        Path out;
        @CommandLine.Option(names = "--instruments", paramLabel = "<names>")
        String instruments;
        @CommandLine.Option(names = "--probe-version", paramLabel = "<n>")
        Integer probeVersion;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit analyze";
            if (file == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: kit analyze <kit.yaml>",
                        "Try kit analyze samples/kits/analysis.yaml.");
            }
            try {
                InstrumentProfile.Report report = InstrumentProfile.analyze(
                        file, out, names(instruments), probeVersion);
                StringBuilder text = new StringBuilder();
                for (InstrumentProfile.Row row : report.rows()) {
                    text.append(InstrumentProfile.rowLine(row, report.cached())).append('\n');
                }
                text.append("analyzed=").append(report.analyzed())
                        .append(" bad=").append(report.bad())
                        .append(" cached=").append(report.cached())
                        .append(" hash=").append(report.hash());
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("analyzed", report.analyzed());
                data.put("bad", report.bad());
                data.put("cached", report.cached());
                data.put("hash", report.hash());
                data.put("instruments", report.rows().stream().map(InstrumentProfile.Row::name).toList());
                return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.kit/1",
                        data, List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]",
                        e.getMessage() == null ? "probe failed" : e.getMessage(),
                        "Check that the soundfont can be read.");
            }
        }
    }

    @CommandLine.Command(name = "describe", description = "Print one instrument's verdict.")
    public static class Describe implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<name>")
        String name;
        @CommandLine.Option(names = "--kit", paramLabel = "<id>", defaultValue = "gm-lite")
        String kitId;
        @CommandLine.Option(names = "--out", paramLabel = "<dir>")
        Path out;
        @CommandLine.Option(names = "--text")
        boolean text;
        @CommandLine.Option(names = "--where", paramLabel = "<field=value>")
        String where;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit describe";
            Path file = Kits.find(kitId);
            try {
                Kit loaded = Kits.load(file);
                if (name != null && loaded.instrument(name) == null) {
                    List<String> names = new ArrayList<>();
                    for (Kit.Instrument instrument : loaded.instruments()) {
                        names.add(instrument.name());
                    }
                    String near = Suggestions.nearest(name, names, 4);
                    String fix = near == null
                            ? "Pass a name from the kit."
                            : "Did you mean " + near + "?";
                    return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                            "instrument not found", fix);
                }
                InstrumentProfile.Report report = InstrumentProfile.analyze(file, out);
                List<InstrumentProfile.Row> rows = new ArrayList<>();
                for (InstrumentProfile.Row row : report.rows()) {
                    if (name != null && !row.name().equals(name)) {
                        continue;
                    }
                    if (where != null && !matches(row, where)) {
                        continue;
                    }
                    rows.add(row);
                }
                if (name != null && rows.isEmpty()) {
                    return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                            "instrument not found", "Pass a name from the kit.");
                }
                StringBuilder printed = new StringBuilder();
                List<Map<String, Object>> dataRows = new ArrayList<>();
                for (InstrumentProfile.Row row : rows) {
                    if (printed.length() > 0) {
                        printed.append('\n');
                    }
                    printed.append(text ? InstrumentProfile.text(row) : InstrumentProfile.rowLine(row, null));
                    dataRows.add(data(row));
                }
                Object data = rows.size() == 1 ? dataRows.get(0) : Map.of("instruments", dataRows);
                return parent.finish(ExitCode.OK, printed.toString(), "athenacl.result.kit/1",
                        data, List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit",
                        e.getMessage() == null ? "probe failed" : e.getMessage(),
                        "Check that the soundfont can be read.");
            }
        }

        private static boolean matches(InstrumentProfile.Row row, String where) {
            int eq = where.indexOf('=');
            if (eq < 0) {
                return false;
            }
            String key = where.substring(0, eq);
            String value = where.substring(eq + 1);
            return switch (key) {
                case "verdict" -> row.verdict().equals(value);
                case "tag", "tags" -> row.tags().contains(value);
                case "reason", "reasons" -> row.reasons().stream()
                        .anyMatch(reason -> reason.equals(value) || reason.startsWith(value + "@"));
                default -> false;
            };
        }

        private static Map<String, Object> data(InstrumentProfile.Row row) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("instrument", row.name());
            data.put("verdict", row.verdict());
            data.put("reasons", row.reasons());
            data.put("tags", row.tags());
            data.put("pitchErrorCents", row.pitchErrorCents());
            data.put("class", row.soundClass());
            data.put("octaveOffset", row.octaveOffset());
            if ("override".equals(row.source())) {
                data.put("source", "override");
            }
            return data;
        }
    }

    private static List<String> names(String instruments) {
        if (instruments == null || instruments.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String name : instruments.split(",")) {
            if (!name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    @CommandLine.Command(name = "scan", description = "List every preset in a soundfont.")
    public static class Scan implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", paramLabel = "<soundfont>")
        String source;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;
        @CommandLine.Option(names = "--write", paramLabel = "<yaml>")
        Path write;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit scan";
            if (source == null || source.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: kit scan <soundfont>",
                        "Try kit scan generaluser.");
            }
            Path file = Path.of(source);
            if (!Files.isRegularFile(file)) {
                try {
                    file = SoundbankResolver.select(source, SoundbankPaths.cache(cache)).file();
                } catch (IOException | SoundbankRejected e) {
                    return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", e.getMessage(),
                            "Install the soundbank, or pass the .sf2 path.");
                }
            }
            if (file == null || !Files.isRegularFile(file)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "soundfont not found",
                        "Install the soundbank, or pass the .sf2 path.");
            }
            try {
                List<InstrumentIndex.Preset> presets = InstrumentIndex.list(file);
                int percussion = 0;
                for (InstrumentIndex.Preset preset : presets) {
                    if (preset.percussion()) {
                        percussion++;
                    }
                }
                Map<String, String> summary = new LinkedHashMap<>();
                summary.put("presets", Integer.toString(presets.size()));
                summary.put("percussion", Integer.toString(percussion));
                StringBuilder text = new StringBuilder(KeyValue.line(summary));
                for (InstrumentIndex.Preset preset : presets) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("bank", Integer.toString(preset.bank()));
                    row.put("program", Integer.toString(preset.program()));
                    row.put("name", preset.name());
                    row.put("percussion", Boolean.toString(preset.percussion()));
                    text.append('\n').append(KeyValue.record("preset", row));
                }
                if (write != null) {
                    AtomicFiles.write(write, kitYaml(source, presets).getBytes(StandardCharsets.UTF_8), parent.dryRun);
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("presets", presets.size());
                data.put("percussion", percussion);
                return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.kit/1", data, List.of(), List.of());
            } catch (InvalidMidiDataException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "SOUNDBANK_INVALID", "argv[2]", e.getMessage(),
                        "Pass an SF2 file.");
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                        "Check that the soundfont can be read.");
            }
        }

        private static String kitYaml(String id, List<InstrumentIndex.Preset> presets) {
            String kitId = id.replace('\\', '/');
            int slash = kitId.lastIndexOf('/');
            if (slash >= 0) {
                kitId = kitId.substring(slash + 1);
            }
            if (kitId.endsWith(".sf2")) {
                kitId = kitId.substring(0, kitId.length() - 4);
            }
            StringBuilder yaml = new StringBuilder();
            yaml.append("kit: ").append(slug(kitId)).append('\n');
            yaml.append("version: 1\n");
            yaml.append("instruments:\n");
            for (InstrumentIndex.Preset preset : presets) {
                yaml.append("  ").append(slug(preset.name())).append('-').append(preset.program());
                if (preset.percussion()) {
                    yaml.append(": {drumkit: ").append(preset.program()).append("}\n");
                } else {
                    yaml.append(": {bank: ").append(preset.bank())
                            .append(", program: ").append(preset.program()).append("}\n");
                }
            }
            return yaml.toString();
        }

        private static String slug(String name) {
            StringBuilder slug = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char c = Character.toLowerCase(name.charAt(i));
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                    slug.append(c);
                } else if (!slug.isEmpty() && slug.charAt(slug.length() - 1) != '-') {
                    slug.append('-');
                }
            }
            while (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
                slug.setLength(slug.length() - 1);
            }
            return slug.isEmpty() ? "preset" : slug.toString();
        }
    }

    private static int fail(AgentCli parent, KitException e) {
        KitException.Issue issue = e.issues().get(0);
        ExitCode code = "NOT_FOUND".equals(issue.code()) || "KIT_NOT_FOUND".equals(issue.code())
                ? ExitCode.NOT_FOUND
                : ExitCode.INVALID_INPUT;
        return parent.fail(code, issue.code(), issue.path(), issue.message(), issue.fix());
    }
}
