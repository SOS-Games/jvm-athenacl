// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import io.github.sosgames.athenacl.rhythm.Swing;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Measures a rendered WAV. A directory uses {@code score.wav}. The line is
 * {@code loudness.integrated=} LUFS, {@code truePeak=} dBTP, and {@code shortTerm=}.
 * When the directory also has {@code events.jsonl} with swung eighths, the line adds
 * {@code swing.measured=}.
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
        return LoudnessReports.finish(parent, wav, "athenacl.result.analyze/1", swingSuffix(input));
    }

    private static String swingSuffix(Path input) {
        Path dir = Files.isDirectory(input) ? input : input.getParent();
        if (dir == null) {
            return null;
        }
        Path events = dir.resolve("events.jsonl");
        if (!Files.isRegularFile(events)) {
            return null;
        }
        try {
            Score score = NoteFiles.events(events);
            return Swing.measure(score).stream()
                    .mapToObj(measured -> "swing.measured=" + Swing.format(measured))
                    .findFirst()
                    .orElse(null);
        } catch (ScoreException | IOException e) {
            return null;
        }
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
