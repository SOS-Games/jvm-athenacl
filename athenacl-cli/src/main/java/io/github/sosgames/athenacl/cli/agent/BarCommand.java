// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * One bar of a theme, regenerated from the seed. The header is the state the director
 * was in. Each note line carries the seed path and why that note was chosen.
 */
@CommandLine.Command(name = "bar", description = "Print one bar of a theme, with a reason on every note.")
public class BarCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;
    @CommandLine.Option(names = "--bar", paramLabel = "<n>")
    Integer bar;

    @Override
    public Integer call() {
        return report(debug.cli(), "debug bar", theme, seed, bar, true);
    }

    static int report(AgentCli parent, String command, Path theme, long seed, Integer bar, boolean header) {
        parent.commandName = command;
        if (theme == null || bar == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: " + command + " <theme.yaml> --seed <n> --bar <n>",
                    "Pass the theme and the bar you want to read.");
        }
        if (!Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "theme not found",
                    "Pass a theme YAML file.");
        }
        Director.Performance performance = ThemeCli.perform(parent, theme, null, List.of(), seed);
        if (performance == null) {
            return ThemeCli.failureCode();
        }
        String text = ThemeCli.barText(performance, bar, header);
        if (text == null) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--bar",
                    "bar " + bar + " is outside the theme",
                    "Use a bar from 1 to " + performance.score().bars() + ".");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bar", bar);
        data.put("notes", performance.notes().stream().filter(note -> note.note().bar() == bar).count());
        return parent.finish(ExitCode.OK, text, "athenacl.result.bar/1", data, List.of(), List.of());
    }
}
