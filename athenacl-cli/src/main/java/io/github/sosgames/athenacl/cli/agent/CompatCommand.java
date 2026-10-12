// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Python-compatible helpers for the athenaCL port. {@code compat random} prints
 * CPython {@code random()} draws for a seed.
 */
@CommandLine.Command(name = "compat", description = "CPython-compatible random draws.",
        subcommands = {CompatRandomCommand.class})
public class CompatCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "compat";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: compat random --seed <n> --n <count>",
                "Try compat random --seed 7 --n 5.");
    }

    AgentCli cli() {
        return parent;
    }
}
