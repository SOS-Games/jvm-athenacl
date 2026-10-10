// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Offline checks on a render directory. {@code --loudness} measures the WAV.
 * A failed loudness reading exits 5. Later layers add stuck-note findings that exit 6.
 */
@CommandLine.Command(name = "check", description = "Check a render directory.")
public class CheckCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<wav-or-dir>")
    Path input;
    @CommandLine.Option(names = "--loudness")
    boolean loudness;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug check";
        if (!loudness) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug check <wav-or-dir> --loudness",
                    "Pass --loudness to measure the WAV.");
        }
        if (input == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug check <wav-or-dir> --loudness",
                    "Pass the directory render wrote.");
        }
        Path wav = AnalyzeCommand.wav(input);
        if (wav == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "wav not found",
                    "Render with --formats wav, then pass that directory or score.wav.");
        }
        return LoudnessReports.finish(parent, wav, "athenacl.result.check/1");
    }
}
