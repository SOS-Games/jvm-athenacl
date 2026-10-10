// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.BuildVersion;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.Issue;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.Platform;
import io.github.sosgames.athenacl.cli.model.Severity;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * One text line: {@code version=} {@code java=} {@code gervill=}. Java is the feature
 * version (21, 25, …), not the full build. Gervill missing is a warning here, not a crash,
 * so this command can still report the environment. {@code --strict} turns that warning
 * into exit 3.
 */
@CommandLine.Command(name = "version", description = "Print version, Java feature level, and Gervill.")
public class VersionCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "version";
        String javaFeature = Integer.toString(Runtime.version().feature());
        String gervill = Platform.gervill();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("version", BuildVersion.PROJECT);
        fields.put("java", javaFeature);
        fields.put("gervill", gervill);
        List<Issue> warnings = new ArrayList<>();
        if (!"present".equals(gervill)) {
            warnings.add(new Issue(
                    "GERVILL_ABSENT",
                    Severity.WARNING,
                    "runtime",
                    "Gervill is not on this JDK",
                    "Install a full JDK 21 that includes java.desktop.",
                    "AGENTS.md#version"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", BuildVersion.PROJECT);
        data.put("java", javaFeature);
        data.put("gervill", gervill);
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.version/1",
                data, warnings, List.of());
    }
}
