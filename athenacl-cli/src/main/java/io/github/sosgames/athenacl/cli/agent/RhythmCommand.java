// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Rhythm commands. {@code pattern} prints a drum grid. {@code poly} writes two pulse trains
 * that meet on each downbeat.
 */
@CommandLine.Command(name = "rhythm", description = "Drum patterns, meter, and polyrhythm.",
        subcommands = {PatternCommand.class, PolyCommand.class})
public class RhythmCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "rhythm";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: rhythm pattern <x..x..> | rhythm poly <3:2> --out <dir>",
                "Try rhythm pattern x..x..x. --meter 6/8 --bars 2 --drum-grid.");
    }

    AgentCli cli() {
        return parent;
    }
}
