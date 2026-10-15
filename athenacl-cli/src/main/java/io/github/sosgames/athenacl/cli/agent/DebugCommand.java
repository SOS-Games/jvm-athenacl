// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Checks that do not write music. {@code determinism} hashes the event log twice.
 * {@code inspect} prints the notes that match {@code --where}.
 */
@CommandLine.Command(name = "debug", description = "Determinism, bisect, inspect, and overhead.",
        subcommands = {DeterminismCommand.class, BisectCommand.class, InspectCommand.class, CheckCommand.class,
                BarCommand.class, ExplainNoteCommand.class, TraceCommands.Trace.class, TraceCommands.Replay.class,
                FuzzCommand.class, OverheadCommand.class, ProbeCommand.class})
public class DebugCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "debug";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: debug determinism <notes.yaml> | debug bisect --good <dir> --bad <dir> | debug inspect <events.jsonl>",
                "Run athenacl help debug.");
    }

    AgentCli cli() {
        return parent;
    }
}
