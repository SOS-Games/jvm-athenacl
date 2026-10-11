// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.theory.Key;
import io.github.sosgames.athenacl.theory.Modulation;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Plans a modulation and prints it. Nothing is written.
 * {@code --method pivot} names a shared chord and a two-bar bridge.
 */
@CommandLine.Command(name = "modulate", description = "Plan a pivot, a direct jump, or a borrowed chord.")
public class ModulateCommand implements Callable<Integer> {
    @CommandLine.ParentCommand TheoryCommand theory;
    @CommandLine.Parameters(index = "0", paramLabel = "<from>")
    String fromName;
    @CommandLine.Parameters(index = "1", paramLabel = "<to>")
    String toName;
    @CommandLine.Option(names = "--method", defaultValue = "pivot", paramLabel = "<pivot|direct|mixture>")
    String method;

    @Override
    public Integer call() {
        AgentCli parent = theory.cli();
        parent.commandName = "theory modulate";
        Modulation.Plan plan;
        try {
            plan = Modulation.plan(Key.parse(fromName), Key.parse(toName), method);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--method", e.getMessage(),
                    "Use two keys, such as \"D dorian\" and \"F lydian\", and method pivot, direct, or mixture.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("method", plan.method());
        fields.put("from", plan.from().name());
        fields.put("to", plan.to().name());
        if (!plan.pivot().isBlank()) {
            fields.put("pivot", plan.pivot());
        }
        if (!plan.mixture().isBlank()) {
            fields.put("mixture", plan.mixture());
        }
        fields.put("bars", Integer.toString(plan.bars()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", plan.method());
        data.put("from", plan.from().name());
        data.put("to", plan.to().name());
        if (!plan.pivot().isBlank()) {
            data.put("pivot", plan.pivot());
        }
        if (!plan.mixture().isBlank()) {
            data.put("mixture", plan.mixture());
        }
        data.put("bars", plan.bars());
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.modulate/1",
                data, List.of(), List.of());
    }
}
