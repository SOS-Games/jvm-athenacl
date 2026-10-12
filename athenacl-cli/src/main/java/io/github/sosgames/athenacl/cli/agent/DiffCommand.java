// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeDiff;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.debug.Bisect;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code diff} of two theme files prints the resolved leaves that differ.
 * {@code diff} of two render directories prints the first bar whose notes differ.
 * {@code --fail-on-diff} exits 6 when anything differs.
 */
@CommandLine.Command(name = "diff", description = "Diff two themes or two render directories.")
public class DiffCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<left>")
    Path left;
    @CommandLine.Parameters(index = "1", arity = "0..1", paramLabel = "<right>")
    Path right;
    @CommandLine.Option(names = "--fail-on-diff")
    boolean failOnDiff;

    @Override
    public Integer call() {
        parent.commandName = "diff";
        if (left == null || right == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                    "usage: diff <theme.yaml> <theme.yaml> | diff <dir> <dir>",
                    "Pass two themes, or two render directories.");
        }
        if (!Files.exists(left) || !Files.exists(right)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]",
                    "path not found",
                    "Pass two theme files or two render directories.");
        }
        if (Files.isDirectory(left) && Files.isDirectory(right)) {
            return renders();
        }
        if (Files.isRegularFile(left) && Files.isRegularFile(right)) {
            return themes();
        }
        return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]",
                "diff needs two themes or two directories",
                "Do not mix a theme file with a render directory.");
    }

    private int themes() {
        Theme a;
        Theme b;
        try {
            a = Themes.load(left);
            b = Themes.load(right);
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that both theme files are readable.");
        }
        List<ThemeDiff.Change> changes = ThemeDiff.compare(a, b);
        List<Map<String, String>> rows = new ArrayList<>();
        for (ThemeDiff.Change change : changes) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("kind", change.kind());
            row.put("path", change.path());
            row.put("from", change.from());
            row.put("to", change.to());
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("changes", rows);
        data.put("identical", changes.isEmpty());
        ExitCode code = failOnDiff && !changes.isEmpty() ? ExitCode.CHECK_FAILED : ExitCode.OK;
        return parent.finish(code, ThemeDiff.text(changes), "athenacl.result.diff/1",
                data, List.of(), List.of());
    }

    private int renders() {
        Path leftEvents = left.resolve("events.jsonl");
        Path rightEvents = right.resolve("events.jsonl");
        if (!Files.isRegularFile(leftEvents) || !Files.isRegularFile(rightEvents)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]",
                    "events.jsonl not found",
                    "Pass two render directories, each with events.jsonl.");
        }
        String leftText;
        String rightText;
        try {
            leftText = Files.readString(leftEvents, StandardCharsets.UTF_8);
            rightText = Files.readString(rightEvents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that both event logs are readable.");
        }
        Bisect.Hit hit = Bisect.events(leftText, rightText);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("firstDivergentBar", Integer.toString(hit.bar()));
        if (hit.bar() > 0) {
            fields.put("added", Integer.toString(hit.added()));
            fields.put("removed", Integer.toString(hit.removed()));
            fields.put("changed", Integer.toString(hit.changed()));
        } else {
            fields.put("identical", "true");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstDivergentBar", hit.bar());
        data.put("added", hit.added());
        data.put("removed", hit.removed());
        data.put("changed", hit.changed());
        data.put("identical", hit.identical());
        ExitCode code = failOnDiff && !hit.identical() ? ExitCode.CHECK_FAILED : ExitCode.OK;
        return parent.finish(code, KeyValue.line(fields), "athenacl.result.diff/1",
                data, List.of(), List.of());
    }
}
