// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.PhraseAnalysis;
import io.github.sosgames.athenacl.adaptive.Presets;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Shows a built-in genre preset. {@code preset show fantasy} names the instruments
 * and the GM limit: choir and taiko, no sung lyrics. {@code preset list} counts them.
 * {@code preset audition} plays eight bars and prints the genre character.
 * {@code --analyze} adds the harmony and lead counts and exits 6 when a preset
 * stays on fewer than three bass notes or the lead overlaps. {@code --all} does
 * that for every preset. The director never sees the preset itself;
 * {@code theme explain} shows the theme after the merge.
 */
@CommandLine.Command(name = "preset", description = "Show a built-in genre preset.",
        subcommands = {PresetCommand.Show.class, PresetCommand.ListPresets.class, PresetCommand.Audition.class})
public class PresetCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "preset";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: preset list | preset show <id> | preset audition <id> --bars 8 --seed 1",
                "Known ids: " + String.join(", ", Presets.ids()) + ".");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "show", description = "Print one preset's instruments and limits.")
    static class Show implements Callable<Integer> {
        @CommandLine.ParentCommand PresetCommand presetCommand;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<id>")
        String id;

        @Override
        public Integer call() {
            AgentCli parent = presetCommand.cli();
            parent.commandName = "preset show";
            if (id == null || id.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: preset show <id>",
                        "Known ids: " + String.join(", ", Presets.ids()) + ".");
            }
            Presets.Preset preset = Presets.get(id);
            if (preset == null) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                        "unknown preset " + id,
                        "Known ids: " + String.join(", ", Presets.ids()) + ".");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("preset", preset.id());
            fields.put("meter", preset.meter());
            fields.put("character", preset.character());
            fields.put("instruments", preset.instruments());
            fields.put("limits", preset.limits());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("preset", preset.id());
            data.put("meter", preset.meter());
            data.put("character", preset.character());
            data.put("limits", preset.limits());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.preset/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "list", description = "List the built-in genre presets.")
    static class ListPresets implements Callable<Integer> {
        @CommandLine.ParentCommand PresetCommand presetCommand;

        @Override
        public Integer call() {
            AgentCli parent = presetCommand.cli();
            parent.commandName = "preset list";
            List<String> ids = Presets.ids();
            StringBuilder text = new StringBuilder();
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("presets", Integer.toString(ids.size()));
            text.append(KeyValue.line(summary));
            for (String id : ids) {
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("preset", id);
                text.append('\n').append(KeyValue.line(fields));
            }
            text.append('\n');
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("presets", ids.size());
            data.put("ids", ids);
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.preset-list/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "audition", description = "Play a preset and print its character.")
    static class Audition implements Callable<Integer> {
        @CommandLine.ParentCommand PresetCommand presetCommand;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<id>")
        String id;
        @CommandLine.Option(names = "--bars", paramLabel = "<n>", defaultValue = "8")
        int bars;
        @CommandLine.Option(names = "--seed", paramLabel = "<n>", defaultValue = "1")
        long seed;
        @CommandLine.Option(names = "--analyze", description = "Print harmony and lead counts. Exit 6 when a preset stays on one chord or the lead overlaps.")
        boolean analyze;
        @CommandLine.Option(names = "--all", description = "Audition every preset.")
        boolean all;

        @Override
        public Integer call() {
            AgentCli parent = presetCommand.cli();
            parent.commandName = "preset audition";
            if (all) {
                return every(parent);
            }
            if (id == null || id.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: preset audition <id> --bars 8 --seed 1",
                        "Known ids: " + String.join(", ", Presets.ids()) + ".");
            }
            if (Presets.get(id) == null) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                        "unknown preset " + id,
                        "Known ids: " + String.join(", ", Presets.ids()) + ".");
            }
            Presets.Audition played;
            try {
                played = Presets.audition(id, bars, seed);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--bars", e.getMessage(),
                        "Use --bars 8 or another positive count.");
            } catch (io.github.sosgames.athenacl.adaptive.ThemeException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "preset", e.getMessage(),
                        "The preset could not be played.");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("preset", played.id());
            fields.put("bars", Integer.toString(played.bars()));
            fields.put("seed", Long.toString(played.seed()));
            fields.put("key", played.key());
            fields.put("bpm", Integer.toString(played.bpm()));
            fields.put("meter", played.meter());
            fields.put("character", played.character());
            fields.put("instruments", played.instruments());
            fields.put("notes", Integer.toString(played.notes()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("preset", played.id());
            data.put("bars", played.bars());
            data.put("seed", played.seed());
            data.put("key", played.key());
            data.put("bpm", played.bpm());
            data.put("meter", played.meter());
            data.put("character", played.character());
            data.put("notes", played.notes());
            String text = KeyValue.line(fields);
            ExitCode code = played.notes() > 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
            if (analyze) {
                try {
                    PhraseAnalysis.Report report = PhraseAnalysis.of(Presets.play(id, bars, seed).score());
                    text = text + " " + report.line();
                    if (!report.passes()) {
                        code = ExitCode.CHECK_FAILED;
                    }
                } catch (io.github.sosgames.athenacl.adaptive.ThemeException e) {
                    return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "preset", e.getMessage(),
                            "The preset could not be played.");
                }
            }
            return parent.finish(code, text, "athenacl.result.preset-audition/1",
                    data, List.of(), List.of());
        }

        private Integer every(AgentCli parent) {
            StringBuilder text = new StringBuilder();
            int fail = 0;
            List<String> ids = Presets.ids();
            for (String presetId : ids) {
                Director.Performance performance;
                try {
                    performance = Presets.play(presetId, bars, seed);
                } catch (IllegalArgumentException e) {
                    return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--bars", e.getMessage(),
                            "Use --bars 8 or another positive count.");
                } catch (io.github.sosgames.athenacl.adaptive.ThemeException e) {
                    return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "preset", e.getMessage(),
                            "The preset could not be played.");
                }
                PhraseAnalysis.Report report = PhraseAnalysis.of(performance.score());
                if (!report.passes() || performance.notes().isEmpty()) {
                    fail++;
                }
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("preset", presetId);
                text.append(KeyValue.line(fields)).append(' ').append(report.line()).append('\n');
            }
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("presets", Integer.toString(ids.size()));
            summary.put("fail", Integer.toString(fail));
            text.append(KeyValue.line(summary));
            ExitCode code = fail == 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("presets", ids.size());
            data.put("fail", fail);
            return parent.finish(code, text.toString(), "athenacl.result.preset-audition/1",
                    data, List.of(), List.of());
        }
    }
}
