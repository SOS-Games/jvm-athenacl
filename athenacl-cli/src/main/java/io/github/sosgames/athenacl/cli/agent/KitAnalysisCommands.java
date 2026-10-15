// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.Issue;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.Severity;
import io.github.sosgames.athenacl.cli.model.Suggestions;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.analysis.Compatibility;
import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import io.github.sosgames.athenacl.instruments.analysis.InstrumentProfile;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Lint, pair, and role-fit commands. They read a kit profile.
 * The small analysis kit is rendered if its profile is missing. A full GM kit is not.
 */
public final class KitAnalysisCommands {
    private KitAnalysisCommands() {
    }

    @CommandLine.Command(name = "lint", description = "Lint the instruments in a resolved theme.")
    public static class Lint implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
        Path file;
        @CommandLine.Option(names = "--state", paramLabel = "<id>")
        String state;
        @CommandLine.Option(names = "--all-states")
        boolean allStates;
        @CommandLine.Option(names = "--genre", paramLabel = "<id>")
        String genre;
        @CommandLine.Option(names = "--seed", paramLabel = "<n>")
        Integer seed;
        @CommandLine.Option(names = "--kit", paramLabel = "<id>")
        String kitId;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit lint";
            if (file == null && genre != null && !genre.isBlank()) {
                try {
                    Compatibility.Lint lint = Ensemble.lintGenre(genre, seed == null ? 1 : seed, kitId);
                    return finish(parent, lint);
                } catch (KitException e) {
                    return fail(parent, e);
                } catch (IOException e) {
                    return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--genre",
                            e.getMessage() == null ? "lint failed" : e.getMessage(),
                            "Check that the kit profile can be read.");
                }
            }
            if (file == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: kit lint <theme.yaml> [--state <id>|--all-states]",
                        "Try kit lint samples/themes/lint-clean.yaml --all-states.");
            }
            try {
                Compatibility.Lint lint = Compatibility.lintTheme(file, allStates ? null : state);
                return finish(parent, lint);
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]",
                        e.getMessage() == null ? "lint failed" : e.getMessage(),
                        "Check that the theme and the kit profile can be read.");
            }
        }
    }

    @CommandLine.Command(name = "compat", description = "Compare two instruments at the registers they share.")
    public static class Compat implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<instrument>")
        String left;
        @CommandLine.Parameters(index = "1", arity = "0..1", paramLabel = "<instrument>")
        String right;
        @CommandLine.Option(names = "--kit", paramLabel = "<id>", defaultValue = "gm-lite")
        String kitId;
        @CommandLine.Option(names = "--registers", paramLabel = "<low..high,low..high>")
        String registers;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit compat";
            if (left == null || right == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: kit compat <instrument> <instrument> --kit <id>",
                        "Try kit compat saw saw --kit analysis --registers C4..C5,C4..C5.");
            }
            try {
                InstrumentProfile.Report profile = profile(kitId);
                String missing = missing(profile, left, right);
                if (missing != null) {
                    return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                            "instrument not found", hint(profile, missing));
                }
                InstrumentProfile.Row a = row(profile, left);
                InstrumentProfile.Row b = row(profile, right);
                String leftRange = "C4..C5";
                String rightRange = "C4..C5";
                if (registers != null && !registers.isBlank()) {
                    String[] parts = registers.split(",", 2);
                    leftRange = parts[0];
                    rightRange = parts.length > 1 ? parts[1] : parts[0];
                }
                Compatibility.Pair pair = Compatibility.pair(a, b, leftRange, rightRange);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("instruments", left + "," + right);
                fields.put("roughness", Compatibility.num(pair.roughness()));
                fields.put("mask", Compatibility.num(pair.mask()));
                fields.put("verdict", pair.verdict());
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.kit/1",
                        new LinkedHashMap<>(fields), List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit",
                        e.getMessage() == null ? "compat failed" : e.getMessage(),
                        "Check that the kit profile can be read.");
            }
        }
    }

    @CommandLine.Command(name = "fit", description = "Score one instrument for a role.")
    public static class Fit implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<instrument>")
        String name;
        @CommandLine.Option(names = "--role", paramLabel = "<role>", required = true)
        String role;
        @CommandLine.Option(names = "--kit", paramLabel = "<id>", defaultValue = "gm-lite")
        String kitId;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit fit";
            if (name == null || name.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: kit fit <instrument> --role <role> --kit <id>",
                        "Try kit fit pluck --role arp --kit analysis.");
            }
            try {
                InstrumentProfile.Report profile = profile(kitId);
                InstrumentProfile.Row row = row(profile, name);
                if (row == null) {
                    return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                            "instrument not found", hint(profile, name));
                }
                double fitness = Compatibility.fitness(role, row);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("role", role);
                fields.put("instrument", name);
                fields.put("fitness", Compatibility.num(fitness));
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.kit/1",
                        new LinkedHashMap<>(fields), List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit",
                        e.getMessage() == null ? "fit failed" : e.getMessage(),
                        "Check that the kit profile can be read.");
            }
        }
    }

    private static int finish(AgentCli parent, Compatibility.Lint lint) {
        List<Issue> warnings = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        for (Compatibility.Finding finding : lint.findings()) {
            Issue issue = new Issue(finding.code(),
                    "error".equals(finding.severity()) ? Severity.ERROR : Severity.WARNING,
                    finding.path(), finding.line(), finding.fix(), "docs/instrument-tags.md");
            if ("error".equals(finding.severity())) {
                errors.add(issue);
            } else {
                warnings.add(issue);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", lint.errors());
        data.put("warnings", lint.warnings());
        data.put("findings", lint.findings().stream().map(Compatibility.Finding::line).toList());
        ExitCode code = lint.errors() > 0 ? ExitCode.INVALID_INPUT : ExitCode.OK;
        return parent.finish(code, lint.text(), "athenacl.result.kit/1", data, warnings, errors);
    }

    private static InstrumentProfile.Report profile(String kitId) throws IOException, KitException {
        Path file = Kits.find(kitId);
        if (!java.nio.file.Files.isRegularFile(file)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "--kit", "kit not found",
                    "Pass a kit such as gm-lite or analysis."));
        }
        return InstrumentProfile.analyze(file, null);
    }

    private static InstrumentProfile.Row row(InstrumentProfile.Report profile, String name) {
        for (InstrumentProfile.Row row : profile.rows()) {
            if (row.name().equals(name)) {
                return row;
            }
        }
        return null;
    }

    private static String missing(InstrumentProfile.Report profile, String left, String right) {
        if (row(profile, left) == null) {
            return left;
        }
        if (row(profile, right) == null) {
            return right;
        }
        return null;
    }

    private static String hint(InstrumentProfile.Report profile, String name) {
        List<String> names = new ArrayList<>();
        for (InstrumentProfile.Row row : profile.rows()) {
            names.add(row.name());
        }
        String near = Suggestions.nearest(name, names, 4);
        return near == null ? "Pass a name from the kit." : "Did you mean " + near + "?";
    }

    private static int fail(AgentCli parent, KitException e) {
        KitException.Issue issue = e.issues().get(0);
        ExitCode code = "NOT_FOUND".equals(issue.code()) || "KIT_NOT_FOUND".equals(issue.code())
                ? ExitCode.NOT_FOUND
                : ExitCode.INVALID_INPUT;
        return parent.fail(code, issue.code(), issue.path(), issue.message(), issue.fix());
    }
}
