// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.render.loudness.LoudnessMeter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Measures a rendered WAV. A directory uses {@code score.wav}. The line is
 * {@code loudness.integrated=} LUFS, {@code truePeak=} dBTP, and {@code shortTerm=}.
 */
@CommandLine.Command(name = "analyze", description = "Measure loudness of a rendered WAV.")
public class AnalyzeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<wav-or-dir>")
    Path input;

    @Override
    public Integer call() {
        parent.commandName = "analyze";
        if (input == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]", "usage: analyze <wav-or-dir>",
                    "Pass score.wav or the directory render wrote.");
        }
        Path wav = wav(input);
        if (wav == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "wav not found",
                    "Render with --formats wav, then pass that directory or score.wav.");
        }
        return LoudnessReports.finish(parent, wav, "athenacl.result.analyze/1");
    }

    static Path wav(Path input) {
        if (Files.isRegularFile(input)) {
            return input;
        }
        if (Files.isDirectory(input)) {
            Path score = input.resolve("score.wav");
            if (Files.isRegularFile(score)) {
                return score;
            }
        }
        return null;
    }
}
