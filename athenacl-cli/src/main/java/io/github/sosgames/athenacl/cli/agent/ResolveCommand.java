// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.Resolver;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints one resolved state. Genre defaults, then the state theme, then the game file.
 * {@code --explain} names every layer in the merge, not only the one that won.
 */
@CommandLine.Command(name = "resolve", description = "Print the modules for one genre and state.")
public class ResolveCommand implements Callable<Integer> {
    @CommandLine.ParentCommand ThemeCommand themeCommand;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;
    @CommandLine.Option(names = "--genre", paramLabel = "<id>")
    String genre;
    @CommandLine.Option(names = "--state", paramLabel = "<id>")
    String state;
    @CommandLine.Option(names = "--explain")
    boolean explain;

    @Override
    public Integer call() {
        AgentCli parent = themeCommand.cli();
        parent.commandName = "theme resolve";
        if (state == null || state.isBlank()) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: theme resolve --genre <id> --state <id>",
                    "Try theme resolve --genre fantasy --state sneaking.");
        }
        if (theme != null && !Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "theme not found",
                    "Pass a theme YAML file.");
        }
        if ((genre == null || genre.isBlank()) && theme == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "--genre",
                    "usage: theme resolve --genre <id> --state <id>",
                    "Try theme resolve --genre fantasy --state sneaking.");
        }
        Composition resolved;
        try {
            resolved = Resolver.resolveTheme(genre, state, theme);
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--genre", e.getMessage(),
                    "Check presets/genres and presets/states.");
        }
        if (resolved == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--genre",
                    "unknown genre " + genre,
                    "Use a genre such as fantasy.");
        }
        Map<String, Object> data = new LinkedHashMap<>(resolved.tree());
        data.put("from", resolved.chainText());
        return parent.finish(ExitCode.OK, resolved.lines(explain), "athenacl.result.resolve/1",
                data, java.util.List.of(), java.util.List.of());
    }
}
