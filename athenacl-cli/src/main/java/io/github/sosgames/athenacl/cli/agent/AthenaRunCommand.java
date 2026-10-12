// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import io.github.sosgames.athenacl.core.PathScript;
import io.github.sosgames.athenacl.core.ScriptException;
import io.github.sosgames.athenacl.events.EventLog;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Runs a path-only script and writes {@code <stem>.mid}. The golden file sits beside the
 * script, same stem, {@code .mid}. {@code bytesEqualGolden} is true when those bytes match.
 */
@CommandLine.Command(name = "run", description = "Run a path-only script and compare its MIDI to the golden.")
public class AthenaRunCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AthenaCommand athena;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<script.ath>")
    Path script;
    @CommandLine.Option(names = "--out", paramLabel = "<dir>")
    Path out;

    @Override
    public Integer call() {
        AgentCli parent = athena.cli();
        parent.commandName = "athena run";
        if (script == null || out == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                    "usage: athena run <script.ath> --out <dir>",
                    "Try athena run conformance/paths/c-major.ath --out build/l13.");
        }
        if (!Files.isRegularFile(script)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "script not found",
                    "Pass a path-only .ath file.");
        }
        String fileName = script.getFileName().toString();
        String stem = fileName.endsWith(".ath") ? fileName.substring(0, fileName.length() - 4) : fileName;
        Path golden = script.resolveSibling(stem + ".mid");
        try {
            String text = Files.readString(script, StandardCharsets.UTF_8);
            PathScript.Result score = PathScript.run(text, stem);
            if (!Files.isRegularFile(golden)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "golden", "golden MIDI not found",
                        "Expect " + TextPaths.portable(golden) + " beside the script.");
            }
            byte[] expected = Files.readAllBytes(golden);
            boolean equal = Arrays.equals(score.midi(), expected);
            AtomicFiles.WritePlan plan = AtomicFiles.write(out.resolve(stem + ".mid"), score.midi(), parent.dryRun);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("script", TextPaths.portable(script));
            fields.put("notes", Integer.toString(score.notes()));
            fields.put("tracks", Integer.toString(score.tracks()));
            fields.put("bytes", Integer.toString(score.midi().length));
            fields.put("hash", EventLog.sha256(score.midi()));
            fields.put("bytesEqualGolden", Boolean.toString(equal));
            fields.put("path", TextPaths.portable(plan.path()));
            fields.put("changed", Boolean.toString(plan.changed()));
            fields.put("dryRun", Boolean.toString(parent.dryRun));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("script", TextPaths.portable(script));
            data.put("notes", score.notes());
            data.put("tracks", score.tracks());
            data.put("bytes", score.midi().length);
            data.put("hash", EventLog.sha256(score.midi()));
            data.put("bytesEqualGolden", equal);
            data.put("dryRun", parent.dryRun);
            ExitCode code = equal ? ExitCode.OK : ExitCode.CHECK_FAILED;
            return parent.finish(code, KeyValue.line(fields), "athenacl.result.athena/1",
                    data, List.of(), List.of());
        } catch (ScriptException e) {
            return parent.fail(ExitCode.INVALID_INPUT, e.code(), e.path(), e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that the script is readable and --out is writable.");
        }
    }
}
