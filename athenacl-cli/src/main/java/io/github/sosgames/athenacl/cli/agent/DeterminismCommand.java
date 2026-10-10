// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.debug.Determinism;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.EventLog;
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
 * Loads the same note list more than once and compares event-log hashes.
 * It does not render audio. A mismatch exits 6.
 */
@CommandLine.Command(name = "determinism", description = "Render the event log more than once and compare hashes.")
public class DeterminismCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<notes.yaml>")
    Path notes;
    @CommandLine.Option(names = "--runs", paramLabel = "<n>", defaultValue = "2")
    int runs;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug determinism";
        if (notes == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug determinism <notes.yaml> --runs <n>",
                    "Pass a YAML note list such as samples/notes/c-major-scale.yaml.");
        }
        if (runs < 1) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--runs",
                    "runs must be 1 or more", "Use --runs 2.");
        }
        if (!Files.isRegularFile(notes)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                    "note list not found",
                    "Pass a YAML note list such as samples/notes/c-major-scale.yaml.");
        }
        Determinism.Report report;
        try {
            report = Determinism.run(runs, () -> {
                try {
                    return EventLog.hash(NoteFiles.yaml(notes, seed));
                } catch (IOException | ScoreException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof ScoreException score) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]",
                        score.getMessage(), score.fix());
            }
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", cause.getMessage(),
                    "Check that the note list is readable.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("runs", Integer.toString(report.runs()));
        fields.put("identical", Boolean.toString(report.identical()));
        fields.put("hash", report.hash());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runs", report.runs());
        data.put("identical", report.identical());
        data.put("hash", report.hash());
        ExitCode code = report.identical() ? ExitCode.OK : ExitCode.CHECK_FAILED;
        return parent.finish(code, KeyValue.line(fields), "athenacl.result.determinism/1",
                data, List.of(), List.of());
    }
}
