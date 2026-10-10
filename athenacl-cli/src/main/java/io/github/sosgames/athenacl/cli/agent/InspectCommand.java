// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.debug.Inspect;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints note lines from an event log. {@code --where pitch=E4} keeps the notes
 * with that pitch name, so a scale has exactly one {@code note id=} line for E4.
 */
@CommandLine.Command(name = "inspect", description = "Print notes from an event log that match --where.")
public class InspectCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<events.jsonl>")
    Path events;
    @CommandLine.Option(names = "--where", paramLabel = "<field=value>")
    String where;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug inspect";
        if (events == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug inspect <events.jsonl> --where pitch=E4",
                    "Pass the events.jsonl written by render.");
        }
        if (!Files.isRegularFile(events)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                    "event log not found",
                    "Render with --formats events first, then pass that events.jsonl.");
        }
        Score score;
        try {
            score = NoteFiles.events(events);
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the event log is readable.");
        }
        List<String> lines;
        try {
            lines = Inspect.lines(score, where);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--where", e.getMessage(),
                    "Use a filter such as pitch=E4.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matches", lines.size());
        data.put("lines", lines);
        String text = String.join("\n", lines);
        return parent.finish(ExitCode.OK, text, "athenacl.result.inspect/1", data, List.of(), List.of());
    }
}
