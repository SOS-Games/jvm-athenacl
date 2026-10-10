// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Checks a theme file. Unknown transition targets exit 3 with a JSON pointer and a fix hint.
 * A theme that only has warnings still exits 0.
 */
@CommandLine.Command(name = "validate", description = "Check a theme file for unknown states and bad rules.")
public class ValidateCommand implements Callable<Integer> {
    @CommandLine.ParentCommand ThemeCommand themeCommand;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;

    @Override
    public Integer call() {
        AgentCli parent = themeCommand.cli();
        parent.commandName = "theme validate";
        if (theme == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: theme validate <theme.yaml>",
                    "Pass a theme such as samples/themes/poc.yaml.");
        }
        if (!Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "theme not found",
                    "Pass a theme YAML file.");
        }
        try {
            Theme loaded = Themes.load(theme);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("states", Integer.toString(loaded.states().size()));
            fields.put("errors", "0");
            fields.put("warnings", "0");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("states", loaded.states().size());
            data.put("errors", 0);
            data.put("warnings", 0);
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.validate/1",
                    data, List.of(), List.of());
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the theme file is readable.");
        }
    }
}
