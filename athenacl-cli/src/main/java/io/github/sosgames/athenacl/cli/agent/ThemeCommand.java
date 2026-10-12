// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Theme files. {@code validate} checks states and transitions. {@code explain} prints the
 * states the director will actually play.
 */
@CommandLine.Command(name = "theme", description = "Validate, explain, or resolve a theme.",
        subcommands = {ValidateCommand.class, ExplainThemeCommand.class, ResolveCommand.class})
public class ThemeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "theme";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: theme validate <theme.yaml> | theme explain <theme.yaml> | theme resolve --genre <id> --state <id>",
                "Try theme resolve --genre fantasy --state sneaking.");
    }

    AgentCli cli() {
        return parent;
    }
}
