// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.compat.DemoCorpus;
import io.github.sosgames.athenacl.core.PathScript;
import io.github.sosgames.athenacl.core.ValueGenerators;
import io.github.sosgames.athenacl.core.ScriptException;
import io.github.sosgames.athenacl.core.ScriptRunner;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The pregui MIDI demo scripts. {@code list} counts them. {@code run} checks that
 * each file has commands. {@code run --subset slice} plays the LineGroove beats and
 * compares each MIDI file to the golden beside the script. {@code run --subset po.value}
 * samples the value generators.
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

    @CommandLine.Command(name = "run", description = "Check the demo scripts.")
    public static class RunScripts implements Callable<Integer> {
        static final List<String> SLICE = List.of("script02a.ath", "script02b.ath");

        @CommandLine.ParentCommand Root conformance;
        @CommandLine.Option(names = "--subset", paramLabel = "<name>")
        String subset;

        @Override
        public Integer call() {
            AgentCli parent = conformance.cli();
            parent.commandName = "conformance run";
            if (subset != null) {
                return slice(parent);
            }
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

        private Integer slice(AgentCli parent) {
            if ("po.value".equals(subset)) {
                return values(parent);
            }
            if (!"slice".equals(subset)) {
                return parent.fail(ExitCode.USAGE, "USAGE", "--subset",
                        "unknown subset: " + subset,
                        "Try conformance run --subset slice or conformance run --subset po.value.");
            }
            int pass = 0;
            int fail = 0;
            try {
                for (String name : SLICE) {
                    Path script = DEMOS.resolve(name);
                    if (!Files.isRegularFile(script)) {
                        return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", name, "script not found",
                                "Run from the repo root, where conformance/demos lives.");
                    }
                    String stem = name.substring(0, name.length() - 4);
                    Path golden = script.resolveSibling(stem + ".mid");
                    if (!Files.isRegularFile(golden)) {
                        return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", stem + ".mid",
                                "golden MIDI not found",
                                "Expect the golden beside the script.");
                    }
                    String text = Files.readString(script, StandardCharsets.UTF_8);
                    PathScript.Result score = ScriptRunner.run(text, stem);
                    byte[] expected = Files.readAllBytes(golden);
                    if (Arrays.equals(score.midi(), expected)) {
                        pass++;
                    } else {
                        fail++;
                    }
                }
            } catch (ScriptException e) {
                return parent.fail(ExitCode.INVALID_INPUT, e.code(), e.path(), e.getMessage(), e.fix());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "conformance/demos", e.getMessage(),
                        "Check that the slice scripts and goldens are readable.");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("subset", "slice");
            fields.put("pass", Integer.toString(pass));
            fields.put("fail", Integer.toString(fail));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("subset", "slice");
            data.put("pass", pass);
            data.put("fail", fail);
            ExitCode code = fail == 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
            return parent.finish(code, KeyValue.line(fields), "athenacl.result.conformance-run/1",
                    data, List.of(), List.of());
        }

        private Integer values(AgentCli parent) {
            Path golden = Path.of("conformance/po/value.txt");
            if (!Files.isRegularFile(golden)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "conformance/po/value.txt",
                        "value golden not found",
                        "Run from the repo root, where conformance/po lives.");
            }
            ValueGenerators.Check check;
            try {
                check = ValueGenerators.check(golden);
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "conformance/po/value.txt", e.getMessage(),
                        "Check that the value golden is readable.");
            } catch (RuntimeException e) {
                return parent.fail(ExitCode.CHECK_FAILED, "CHECK_FAILED", "po.value", e.getMessage(),
                        "The value sample did not match the golden.");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("subset", "po.value");
            fields.put("pass", Integer.toString(check.pass()));
            fields.put("fail", Integer.toString(check.fail()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("subset", "po.value");
            data.put("pass", check.pass());
            data.put("fail", check.fail());
            ExitCode code = check.fail() == 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
            return parent.finish(code, KeyValue.line(fields), "athenacl.result.conformance-run/1",
                    data, List.of(), List.of());
        }
    }
}
