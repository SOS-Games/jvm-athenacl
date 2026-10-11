// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.LiveClock;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints the scheduler snapshot a game would show: state, intensity, bar, lookahead, and build time.
 * {@code --out} writes that one line to a file. {@code --dry-run} prints it and does not write.
 */
@CommandLine.Command(name = "adapt", description = "Print the live scheduler snapshot for a theme.")
public class AdaptCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;
    @CommandLine.Option(names = "--timeline", paramLabel = "<file.json>")
    Path timeline;
    @CommandLine.Option(names = "--seed", defaultValue = "1")
    long seed;
    @CommandLine.Option(names = "--out", paramLabel = "<file>")
    Path out;

    @Override
    public Integer call() {
        parent.commandName = "adapt";
        if (theme == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                    "usage: adapt <theme.yaml>",
                    "Pass a theme such as themes/fantasy-dungeon.yaml.");
        }
        if (!Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "theme not found",
                    "Pass a theme YAML file.");
        }
        Theme loaded;
        try {
            loaded = Themes.load(theme);
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the theme file is readable.");
        }
        List<io.github.sosgames.athenacl.adaptive.Theme.Control> controls;
        try {
            controls = timeline == null ? loaded.timeline() : ControlLog.readTimeline(timeline);
        } catch (IOException e) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--timeline", e.getMessage(),
                    "Pass a JSON array of bar, state, and intensity.");
        }
        LiveClock clock = LiveClock.start(loaded, controls, seed);
        clock.poll(0);
        String line = clock.snapshot();
        if (out != null) {
            try {
                AtomicFiles.write(out, (line + "\n").getBytes(StandardCharsets.UTF_8), parent.dryRun);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                        "Check that the snapshot file can be written.");
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("snapshot", line);
        return parent.finish(ExitCode.OK, line, "athenacl.result.adapt/1", data, List.of(), List.of());
    }
}
