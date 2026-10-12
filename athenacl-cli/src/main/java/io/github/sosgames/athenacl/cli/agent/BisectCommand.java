// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.debug.Bisect;
import io.github.sosgames.athenacl.events.EventLog;
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
 * Compares a good render directory with a bad one and prints the first bar that split.
 * When both directories have a control log, it also searches prefixes of the bad log
 * until that bar matches the bad render. That control's bar is {@code firstControl}.
 */
@CommandLine.Command(name = "bisect", description = "Find the first bar where two renders diverge.")
public class BisectCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Option(names = "--good", paramLabel = "<dir>", required = true)
    Path good;
    @CommandLine.Option(names = "--bad", paramLabel = "<dir>", required = true)
    Path bad;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug bisect";
        Path goodEvents = good.resolve("events.jsonl");
        Path badEvents = bad.resolve("events.jsonl");
        if (!Files.isRegularFile(goodEvents) || !Files.isRegularFile(badEvents)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--good",
                    "events.jsonl not found",
                    "Pass two render directories, each with events.jsonl.");
        }
        String goodText;
        String badText;
        try {
            goodText = Files.readString(goodEvents, StandardCharsets.UTF_8);
            badText = Files.readString(badEvents, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--good", e.getMessage(),
                    "Check that both event logs are readable.");
        }
        Bisect.Hit hit = Bisect.events(goodText, badText);
        int controlBar = 0;
        Path goodControl = good.resolve("control.jsonl");
        Path badControl = bad.resolve("control.jsonl");
        if (Files.isRegularFile(goodControl) && Files.isRegularFile(badControl)) {
            try {
                String goodLog = Files.readString(goodControl, StandardCharsets.UTF_8);
                String badLog = Files.readString(badControl, StandardCharsets.UTF_8);
                controlBar = Bisect.firstControl(goodLog, badLog);
                if (hit.bar() > 0) {
                    int caused = causedBy(badControl, badText, hit.bar());
                    if (caused > 0) {
                        controlBar = caused;
                    }
                }
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "control.jsonl", e.getMessage(),
                        "Check that both control logs are readable.");
            } catch (ThemeException e) {
                ThemeIssue issue = e.issues().get(0);
                return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("firstDivergentBar", Integer.toString(hit.bar()));
        if (hit.eventId() > 0) {
            fields.put("event", Long.toString(hit.eventId()));
        }
        if (hit.bar() > 0) {
            fields.put("added", Integer.toString(hit.added()));
            fields.put("removed", Integer.toString(hit.removed()));
            fields.put("changed", Integer.toString(hit.changed()));
        }
        if (controlBar > 0) {
            fields.put("firstControl", Integer.toString(controlBar));
        }
        if (hit.identical()) {
            fields.put("identical", "true");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstDivergentBar", hit.bar());
        data.put("event", hit.eventId());
        data.put("added", hit.added());
        data.put("removed", hit.removed());
        data.put("changed", hit.changed());
        data.put("firstControl", controlBar);
        data.put("identical", hit.identical());
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.bisect/1",
                data, List.of(), List.of());
    }

    /**
     * Shortest prefix of the bad control log whose notes at {@code bar} match the bad render.
     * The last call in that prefix is the one that caused the split.
     */
    private static int causedBy(Path badControl, String badEvents, int bar) throws IOException, ThemeException {
        ControlLog.Log recorded = ControlLog.read(badControl);
        if (recorded.theme() == null || recorded.theme().isBlank()) {
            return 0;
        }
        Path themePath = Path.of(recorded.theme());
        if (!Files.isRegularFile(themePath)) {
            return 0;
        }
        Theme theme = Themes.load(themePath).withBars(recorded.bars());
        List<Theme.Control> controls = recorded.controls();
        int k = Bisect.firstTrue(controls.size(), prefix -> {
            List<Theme.Control> slice = new ArrayList<>(controls.subList(0, prefix));
            String jsonl = new String(EventLog.encode(
                    Director.perform(theme, slice, recorded.seed()).score()), StandardCharsets.UTF_8);
            return Bisect.sameBar(jsonl, badEvents, bar);
        });
        if (k < 1) {
            return 0;
        }
        return controls.get(k - 1).bar();
    }
}
