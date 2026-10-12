// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.compat.DemoCorpus;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The pregui MIDI demo scripts. {@code list} counts them. {@code run} checks that
 * each file has commands. Byte-for-byte MIDI comparison starts when the engine can
 * play a script.
 */
public final class ConformanceCommand {
    static final Path DEMOS = Path.of("conformance/demos");

    private ConformanceCommand() {
    }

    @CommandLine.Command(name = "conformance", description = "List or check the demo script corpus.",
            subcommands = {ListScripts.class, RunScripts.class})
    public static class Root implements Callable<Integer> {
        @CommandLine.ParentCommand AgentCli parent;

        @Override
        public Integer call() {
            parent.commandName = "conformance";
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                    "usage: conformance list | conformance run",
                    "Try conformance list.");
        }

        AgentCli cli() {
            return parent;
        }
    }

    @CommandLine.Command(name = "list", description = "Count the demo scripts.")
    public static class ListScripts implements Callable<Integer> {
        @CommandLine.ParentCommand Root conformance;

        @Override
        public Integer call() {
            AgentCli parent = conformance.cli();
            parent.commandName = "conformance list";
            List<Path> scripts;
            try {
                scripts = DemoCorpus.scripts(DEMOS);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "conformance/demos", e.getMessage(),
                        "Check that conformance/demos is readable.");
            }
            if (scripts.isEmpty() && !Files.isDirectory(DEMOS)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "conformance/demos",
                        "demo corpus not found",
                        "Run from the repo root, where conformance/demos lives.");
            }
            StringBuilder text = new StringBuilder();
            text.append("scripts=").append(scripts.size());
            List<String> names = new ArrayList<>();
            for (Path script : scripts) {
                String name = script.getFileName().toString();
                names.add(name);
                text.append('\n').append("script=").append(name);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scripts", scripts.size());
            data.put("names", names);
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.conformance/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "run", description = "Check that every demo script has commands.")
    public static class RunScripts implements Callable<Integer> {
        @CommandLine.ParentCommand Root conformance;

        @Override
        public Integer call() {
            AgentCli parent = conformance.cli();
            parent.commandName = "conformance run";
            List<Path> scripts;
            try {
                scripts = DemoCorpus.scripts(DEMOS);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "conformance/demos", e.getMessage(),
                        "Check that conformance/demos is readable.");
            }
            if (scripts.isEmpty() && !Files.isDirectory(DEMOS)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "conformance/demos",
                        "demo corpus not found",
                        "Run from the repo root, where conformance/demos lives.");
            }
            int missing = 0;
            try {
                for (Path script : scripts) {
                    if (DemoCorpus.commands(script) == 0) {
                        missing++;
                    }
                }
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "conformance/demos", e.getMessage(),
                        "Check that each demo script is readable.");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("scripts", Integer.toString(scripts.size()));
            fields.put("missing", Integer.toString(missing));
            fields.put("fail", Integer.toString(missing));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scripts", scripts.size());
            data.put("missing", missing);
            data.put("fail", missing);
            ExitCode code = missing == 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
            return parent.finish(code, KeyValue.line(fields), "athenacl.result.conformance-run/1",
                    data, List.of(), List.of());
        }
    }
}
