// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/** {@code help} and {@code help --json}. No arguments prints every command. */
@CommandLine.Command(name = "help", description = "List commands and copy-pasteable examples.")
public class HelpCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<command>")
    String topic;

    @Override
    public Integer call() {
        return run(parent, topic);
    }

    static int run(AgentCli cli, String topic) {
        cli.commandName = "help";
        if (topic == null || topic.isBlank()) {
            if (cli.json) {
                return cli.finish(io.github.sosgames.athenacl.cli.model.ExitCode.OK, null,
                        "athenacl.result.help/1", HelpText.data(CommandCatalog.all()),
                        List.of(), List.of());
            }
            cli.out.print(HelpText.full());
            return io.github.sosgames.athenacl.cli.model.ExitCode.OK.number();
        }
        CommandCatalog.CommandInfo info = CommandCatalog.byName(topic);
        if (info == null) {
            String fix = "Run athenacl help to list commands.";
            String near = io.github.sosgames.athenacl.cli.model.Suggestions.nearest(
                    topic, CommandCatalog.names());
            if (near != null) {
                fix = "Did you mean " + near + "? " + fix;
            }
            return cli.fail(io.github.sosgames.athenacl.cli.model.ExitCode.USAGE, "UNKNOWN_COMMAND",
                    "argv[1]", "unknown command: " + topic, fix);
        }
        if (cli.json) {
            return cli.finish(io.github.sosgames.athenacl.cli.model.ExitCode.OK, null,
                    "athenacl.result.help/1", HelpText.data(List.of(info)), List.of(), List.of());
        }
        cli.out.print(HelpText.one(info));
        return io.github.sosgames.athenacl.cli.model.ExitCode.OK.number();
    }
}
