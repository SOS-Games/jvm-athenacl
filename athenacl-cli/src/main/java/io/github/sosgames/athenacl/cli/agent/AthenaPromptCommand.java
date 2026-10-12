// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.core.CommandPrompt;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Resolves one athenaCL command line. {@code tpsd 7} is {@code TPsd}, {@code ?}
 * is help, and {@code pi} opens the path menu. The command is not run.
 */
@CommandLine.Command(name = "prompt", description = "Resolve one athenaCL command line.")
public class AthenaPromptCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AthenaCommand athena;
    @CommandLine.Option(names = "--line", required = true, paramLabel = "<text>")
    String line;

    @Override
    public Integer call() {
        AgentCli parent = athena.cli();
        parent.commandName = "athena prompt";
        try {
            CommandPrompt.Line parsed = CommandPrompt.parse(line);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("command", parsed.command());
            if (!parsed.prefix().isEmpty()) {
                fields.put("prefix", parsed.prefix());
            }
            fields.put("args", parsed.args());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", parsed.command());
            if (!parsed.prefix().isEmpty()) {
                data.put("prefix", parsed.prefix());
            }
            data.put("args", parsed.args());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.prompt/1",
                    data, List.of(), List.of());
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--line", e.getMessage(),
                    "Use a command such as tpsd 7.");
        }
    }
}
