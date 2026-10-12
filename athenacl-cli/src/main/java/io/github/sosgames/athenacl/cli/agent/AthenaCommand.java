// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Runs an athenaCL command script. {@code athena run} is the path-only slice:
 * pitch paths in, a MIDI file out. {@code athena prompt --line} resolves one
 * command the way the prompt does, and does not run it.
 */
@CommandLine.Command(name = "athena", description = "Run an athenaCL command script.",
        subcommands = {AthenaRunCommand.class, AthenaPromptCommand.class})
public class AthenaCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "athena";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: athena run <script.ath> --out <dir> | athena prompt --line <text>",
                "Try athena run conformance/paths/c-major.ath --out build/l13.");
    }

    AgentCli cli() {
        return parent;
    }
}
