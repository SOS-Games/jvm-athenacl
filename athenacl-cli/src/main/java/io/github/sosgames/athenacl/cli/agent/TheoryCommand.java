// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Scale and harmony commands. {@code theory progression} writes a voice-led event log.
 */
@CommandLine.Command(name = "theory", description = "Scales, chords, progressions, and modulation.",
        subcommands = {ProgressionCommand.class, ModulateCommand.class})
public class TheoryCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "theory";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: theory progression <roman numerals> --key <key> --out <dir>",
                "Try theory progression i bVII bVI bVII --key D-aeolian --voice-lead --out build/l2.");
    }

    AgentCli cli() {
        return parent;
    }
}
