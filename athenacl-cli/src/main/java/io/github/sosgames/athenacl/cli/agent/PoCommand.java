// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.core.ParameterObjects;
import io.github.sosgames.athenacl.core.ScriptException;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Draws from a parameter object. {@code po sample basketGen "oc,(1,2,3)" --n 6}
 * walks that basket in order and prints the six values.
 */
@CommandLine.Command(name = "po", description = "Sample a parameter object.",
        subcommands = {PoCommand.Sample.class})
public class PoCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "po";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: po sample <generator> <args> --n <count>",
                "Try po sample basketGen \"oc,(1,2,3)\" --n 6.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "sample", description = "Draw the next values from a generator.")
    public static class Sample implements Callable<Integer> {
        @CommandLine.ParentCommand PoCommand po;
        @CommandLine.Parameters(index = "0", arity = "1", paramLabel = "<generator>")
        String generator;
        @CommandLine.Parameters(index = "1", arity = "0..1", paramLabel = "<args>")
        String args;
        @CommandLine.Option(names = "--n", paramLabel = "<count>", defaultValue = "1")
        int count;
        @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
        long seed;

        @Override
        public Integer call() {
            AgentCli parent = po.cli();
            parent.commandName = "po sample";
            try {
                List<String> draws = ParameterObjects.sample(generator, args == null ? "" : args, count, seed);
                String values = String.join(",", draws);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("generator", generator);
                fields.put("n", Integer.toString(count));
                fields.put("values", values);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("generator", generator);
                data.put("n", count);
                data.put("values", draws);
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.po/1",
                        data, List.of(), List.of());
            } catch (ScriptException e) {
                return parent.fail(ExitCode.INVALID_INPUT, e.code(), e.path(), e.getMessage(), e.fix());
            }
        }
    }
}
