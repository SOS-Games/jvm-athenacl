// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.Issue;
import io.github.sosgames.athenacl.cli.model.Severity;
import io.github.sosgames.athenacl.instruments.EngineSettings;
import io.github.sosgames.athenacl.instruments.Experimental;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.descriptors.DescriptorLint;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Lint checks declared descriptors. Pair and role-fit stay on the experimental probe
 * and refuse to run while analysis is disabled.
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
            if (file == null && (genre == null || genre.isBlank())) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: kit lint <theme.yaml> [--state <id>|--all-states]",
                        "Try kit lint samples/themes/lint-clean.yaml --all-states.");
            }
            try {
                if (EngineSettings.analysisEnabled() && file != null) {
                    String payload = Experimental.call("lintTheme",
                            new Class<?>[] {String.class, String.class},
                            new Object[] {file.toString(), allStates || state == null ? "" : state});
                    int split = payload.indexOf('\n');
                    int errors = Integer.parseInt(payload.substring(0, split));
                    String text = payload.substring(split + 1);
                    ExitCode code = errors > 0 ? ExitCode.INVALID_INPUT : ExitCode.OK;
                    return parent.finish(code, text, "athenacl.result.kit/1",
                            new LinkedHashMap<>(), List.of(), List.of());
                }
                DescriptorLint.Report report = file == null
                        ? DescriptorLint.genre(genre, seed == null ? 1 : seed, kitId)
                        : DescriptorLint.theme(file, allStates ? null : state, kitId, seed == null ? 1 : seed);
                return finish(parent, report);
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]",
                        e.getMessage() == null ? "lint failed" : e.getMessage(),
                        "Check that the theme can be read.");
            } catch (Exception e) {
                return parent.fail(ExitCode.INTERNAL, "INTERNAL", "argv[2]",
                        e.getMessage() == null ? "lint failed" : e.getMessage(),
                        "Set analysis.enabled=true only for the experimental probe.");
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
        @CommandLine.Option(names = "--kit", paramLabel = "<id>")
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
            if (!EngineSettings.analysisEnabled()) {
                return disabled(parent);
            }
            try {
                String text = Experimental.call("compat",
                        new Class<?>[] {String.class, String.class, String.class, String.class},
                        new Object[] {io.github.sosgames.athenacl.instruments.KitDefaults.id(kitId), left, right,
                                registers == null ? "" : registers});
                return parent.finish(ExitCode.OK, text, "athenacl.result.kit/1",
                        new LinkedHashMap<>(), List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit",
                        e.getMessage() == null ? "compat failed" : e.getMessage(),
                        "Check that the kit profile can be read.");
            } catch (Exception e) {
                return parent.fail(ExitCode.INTERNAL, "INTERNAL", "--kit",
                        e.getMessage() == null ? "compat failed" : e.getMessage(),
                        "Set analysis.enabled=true only for the experimental probe.");
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
        @CommandLine.Option(names = "--kit", paramLabel = "<id>")
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
            if (!EngineSettings.analysisEnabled()) {
                return disabled(parent);
            }
            try {
                String text = Experimental.call("fit",
                        new Class<?>[] {String.class, String.class, String.class},
                        new Object[] {io.github.sosgames.athenacl.instruments.KitDefaults.id(kitId), name, role});
                return parent.finish(ExitCode.OK, text, "athenacl.result.kit/1",
                        new LinkedHashMap<>(), List.of(), List.of());
            } catch (KitException e) {
                return fail(parent, e);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit",
                        e.getMessage() == null ? "fit failed" : e.getMessage(),
                        "Check that the kit profile can be read.");
            } catch (Exception e) {
                return parent.fail(ExitCode.INTERNAL, "INTERNAL", "--kit",
                        e.getMessage() == null ? "fit failed" : e.getMessage(),
                        "Set analysis.enabled=true only for the experimental probe.");
            }
        }
    }

    private static int finish(AgentCli parent, DescriptorLint.Report lint) {
        List<Issue> warnings = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        for (DescriptorLint.Finding finding : lint.findings()) {
            Issue issue = new Issue(finding.code(),
                    "error".equals(finding.severity()) ? Severity.ERROR : Severity.WARNING,
                    finding.path(), finding.message(), finding.fix(), "docs/instrument-descriptors.md");
            if ("error".equals(finding.severity())) {
                errors.add(issue);
            } else {
                warnings.add(issue);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", lint.errors());
        data.put("warnings", lint.warnings());
        data.put("findings", lint.findings().stream().map(DescriptorLint.Finding::line).toList());
        ExitCode code = lint.errors() > 0 ? ExitCode.INVALID_INPUT : ExitCode.OK;
        return parent.finish(code, lint.text(), "athenacl.result.kit/1", data, warnings, errors);
    }

    private static int disabled(AgentCli parent) {
        KitException.Issue issue = EngineSettings.disabled().issues().get(0);
        return parent.fail(ExitCode.USAGE, issue.code(), issue.path(), issue.message(), issue.fix());
    }

    private static int fail(AgentCli parent, KitException e) {
        KitException.Issue issue = e.issues().get(0);
        if ("ANALYSIS_DISABLED".equals(issue.code())) {
            return parent.fail(ExitCode.USAGE, issue.code(), issue.path(), issue.message(), issue.fix());
        }
        ExitCode code = "NOT_FOUND".equals(issue.code()) || "KIT_NOT_FOUND".equals(issue.code())
                ? ExitCode.NOT_FOUND
                : ExitCode.INVALID_INPUT;
        return parent.fail(code, issue.code(), issue.path(), issue.message(), issue.fix());
    }
}
