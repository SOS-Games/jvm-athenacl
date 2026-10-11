// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

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
 * and the GM limit: choir and taiko, no sung lyrics. The director never sees the preset
 * itself; {@code theme explain} shows the theme after the merge.
 */
@CommandLine.Command(name = "preset", description = "Show a built-in genre preset.",
        subcommands = {PresetCommand.Show.class})
public class PresetCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "preset";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: preset show <id>",
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
            fields.put("instruments", preset.instruments());
            fields.put("limits", preset.limits());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("preset", preset.id());
            data.put("limits", preset.limits());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.preset/1",
                    data, List.of(), List.of());
        }
    }
}
