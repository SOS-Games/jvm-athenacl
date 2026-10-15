// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code ensemble select} fills each role from a kit profile.
 * The same seed picks the same instruments. A missing profile keeps the fixed names.
 */
@CommandLine.Command(name = "ensemble", description = "Choose instruments for a genre state.",
        subcommands = {EnsembleCommand.Select.class})
public class EnsembleCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "ensemble";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: ensemble select --genre <id> --state <id> --seed <n>",
                "Try ensemble select --genre fantasy --state sneaking --seed 1 --kit analysis.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "select", description = "Pick one instrument per role.")
    public static class Select implements Callable<Integer> {
        @CommandLine.ParentCommand EnsembleCommand ensemble;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
        Path theme;
        @CommandLine.Option(names = "--genre", paramLabel = "<id>")
        String genre;
        @CommandLine.Option(names = "--state", paramLabel = "<id>")
        String state;
        @CommandLine.Option(names = "--seed", paramLabel = "<n>", defaultValue = "1")
        long seed;
        @CommandLine.Option(names = "--kit", paramLabel = "<id>")
        String kit;
        @CommandLine.Option(names = "--explain")
        boolean explain;

        @Override
        public Integer call() {
            AgentCli parent = ensemble.cli();
            parent.commandName = "ensemble select";
            if (state == null || state.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "--state",
                        "usage: ensemble select --genre <id> --state <id>",
                        "Try ensemble select --genre fantasy --state sneaking --seed 1 --kit analysis.");
            }
            if ((genre == null || genre.isBlank()) && theme == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "--genre",
                        "usage: ensemble select --genre <id> --state <id>",
                        "Try ensemble select --genre fantasy --state sneaking --seed 1 --kit analysis.");
            }
            try {
                Ensemble.Result result = Ensemble.select(genre, state, theme, kit, seed);
                String text = result.text();
                if (explain && result.roles().size() > 1) {
                    text = text + "\nrunner=" + result.roles().get(1).instrument();
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("seedPath", result.seedPath());
                data.put("fallback", result.fallback());
                data.put("roles", result.roles().stream().map(Ensemble.Role::instrument).toList());
                return parent.finish(ExitCode.OK, text, "athenacl.result.ensemble/1",
                        data, List.of(), List.of());
            } catch (KitException e) {
                KitException.Issue issue = e.issues().get(0);
                ExitCode code = "NOT_FOUND".equals(issue.code()) ? ExitCode.NOT_FOUND : ExitCode.INVALID_INPUT;
                return parent.fail(code, issue.code(), issue.path(), issue.message(), issue.fix());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--genre",
                        e.getMessage() == null ? "ensemble failed" : e.getMessage(),
                        "Check the genre and the kit profile.");
            }
        }
    }
}
