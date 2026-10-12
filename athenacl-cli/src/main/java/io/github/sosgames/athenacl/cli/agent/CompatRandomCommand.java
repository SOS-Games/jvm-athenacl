// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.compat.CompatRandom;
import io.github.sosgames.athenacl.compat.Py;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints {@code random.random()} the way CPython does for this seed.
 * The digits are the check that the ported generator matches.
 */
@CommandLine.Command(name = "random", description = "Print CPython random() draws.")
public class CompatRandomCommand implements Callable<Integer> {
    @CommandLine.ParentCommand CompatCommand compat;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "7")
    long seed;
    @CommandLine.Option(names = "--n", paramLabel = "<count>", defaultValue = "5")
    int count;

    @Override
    public Integer call() {
        AgentCli parent = compat.cli();
        parent.commandName = "compat random";
        if (count < 1) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--n",
                    "n must be 1 or more", "Use --n 5.");
        }
        CompatRandom random = CompatRandom.seed(seed);
        List<String> draws = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            draws.add(Py.repr(random.random()));
        }
        String values = String.join(",", draws);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("seed", Long.toString(seed));
        fields.put("n", Integer.toString(count));
        fields.put("values", values);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seed", seed);
        data.put("n", count);
        data.put("values", draws);
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.compat/1",
                data, List.of(), List.of());
    }
}
