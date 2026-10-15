// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.Resolver;
import io.github.sosgames.athenacl.adaptive.Sampler;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.analysis.Compatibility;
import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints one resolved state. Genre defaults, then the state theme, then the game file.
 * {@code --seed} draws each distribution and prints where the value came from.
 * {@code --explain} names every layer in the merge, and the distribution when there is one.
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
    @CommandLine.Option(names = "--with-lint")
    boolean withLint;
    @CommandLine.Option(names = "--seed")
    Integer seed;
    @CommandLine.Option(names = "--kit", paramLabel = "<id>")
    String kit;

    @Override
    public Integer call() {
        AgentCli parent = themeCommand.cli();
        parent.commandName = "theme resolve";
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
        if (seed != null) {
            resolved = Sampler.sample(resolved, seed, Sampler.SECTION);
        }
        String text = resolved.lines(explain);
        if (seed != null && state != null && !state.isBlank()) {
            try {
                Ensemble.Result picked = Ensemble.select(genre, state, theme, kit, seed);
                text = text + "\n" + picked.instrumentLines();
            } catch (KitException e) {
                KitException.Issue issue = e.issues().get(0);
                return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--seed",
                        e.getMessage() == null ? "ensemble failed" : e.getMessage(),
                        "Check that the kit profile can be read.");
            }
        }
        Map<String, Object> data = new LinkedHashMap<>(resolved.tree());
        data.put("from", resolved.chainText());
        if (!withLint || theme == null) {
            return parent.finish(ExitCode.OK, text, "athenacl.result.resolve/1",
                    data, java.util.List.of(), java.util.List.of());
        }
        try {
            Compatibility.Lint lint = Compatibility.lintTheme(theme, state);
            text = text + "\n" + lint.text();
            data.put("errors", lint.errors());
            data.put("warnings", lint.warnings());
            ExitCode code = lint.errors() > 0 ? ExitCode.INVALID_INPUT : ExitCode.OK;
            return parent.finish(code, text, "athenacl.result.resolve/1",
                    data, java.util.List.of(), java.util.List.of());
        } catch (KitException e) {
            KitException.Issue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--theme", e.getMessage(),
                    "Check that the theme and the kit profile can be read.");
        }
    }
}
