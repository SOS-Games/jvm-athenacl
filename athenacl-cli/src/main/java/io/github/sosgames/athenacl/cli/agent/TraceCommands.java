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
import io.github.sosgames.athenacl.events.EventLog;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Reads a render directory's {@code control.jsonl} and plays it again.
 * {@code --transitions} prints each state change and the tick it landed on.
 * {@code debug replay} prints the event-log hash, which matches the render when the log is unchanged.
 */
public final class TraceCommands {
    private TraceCommands() {
    }

    @CommandLine.Command(name = "trace", description = "Print state changes from a render directory.")
    public static class Trace implements Callable<Integer> {
        @CommandLine.ParentCommand DebugCommand debug;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<dir-or-control.jsonl>")
        Path input;
        @CommandLine.Option(names = "--transitions")
        boolean transitions;

        @Override
        public Integer call() {
            AgentCli parent = debug.cli();
            parent.commandName = "debug trace";
            if (!transitions) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: debug trace <dir> --transitions",
                        "Pass --transitions to list state changes.");
            }
            Played played = play(parent, input);
            if (played == null) {
                return ThemeCli.failureCode();
            }
            StringBuilder text = new StringBuilder();
            for (Director.Transition transition : played.performance.transitions()) {
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("from", transition.from());
                fields.put("to", transition.to());
                fields.put("quantize", transition.quantize());
                fields.put("requestTick", Long.toString(transition.requestTick()));
                fields.put("appliedTick", Long.toString(transition.appliedTick()));
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(KeyValue.record("transition", fields));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("transitions", played.performance.transitions().size());
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.trace/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "replay", description = "Replay a control log and print the event-log hash.")
    public static class Replay implements Callable<Integer> {
        @CommandLine.ParentCommand DebugCommand debug;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<control.jsonl>")
        Path input;

        @Override
        public Integer call() {
            AgentCli parent = debug.cli();
            parent.commandName = "debug replay";
            Played played = play(parent, input);
            if (played == null) {
                return ThemeCli.failureCode();
            }
            String hash = EventLog.hash(played.performance.score());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("hash", hash);
            fields.put("bars", Integer.toString(played.performance.score().bars()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("hash", hash);
            data.put("bars", played.performance.score().bars());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.replay/1",
                    data, List.of(), List.of());
        }
    }

    private record Played(Director.Performance performance) {
    }

    private static Played play(AgentCli parent, Path input) {
        if (input == null) {
            ThemeCli.remember(parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug trace <dir> --transitions | debug replay <control.jsonl>",
                    "Pass the directory render wrote, or its control.jsonl."));
            return null;
        }
        Path log = control(input);
        if (log == null) {
            ThemeCli.remember(parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                    "control log not found",
                    "Render a theme with --out, then pass that directory or control.jsonl."));
            return null;
        }
        ControlLog.Log recorded;
        try {
            recorded = ControlLog.read(log);
        } catch (IOException e) {
            ThemeCli.remember(parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the control log is readable."));
            return null;
        }
        Path themePath = Path.of(recorded.theme());
        if (!Files.isRegularFile(themePath)) {
            ThemeCli.remember(parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "theme",
                    "theme not found",
                    "Run replay from the directory that contains " + recorded.theme() + "."));
            return null;
        }
        Theme theme;
        try {
            theme = Themes.load(themePath);
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            ThemeCli.remember(parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(),
                    issue.message(), issue.fix()));
            return null;
        } catch (IOException e) {
            ThemeCli.remember(parent.fail(ExitCode.RUNTIME, "IO_ERROR", "theme", e.getMessage(),
                    "Check that the theme file is readable."));
            return null;
        }
        Theme limited = new Theme(theme.id(), recorded.bars(), theme.bpm(), theme.numerator(), theme.denominator(),
                theme.states(), theme.stingers(), theme.defaultQuantize(), theme.rules(), List.of());
        return new Played(Director.perform(limited, recorded.controls(), recorded.seed()));
    }

    private static Path control(Path input) {
        if (Files.isRegularFile(input)) {
            return input;
        }
        if (Files.isDirectory(input)) {
            Path log = input.resolve("control.jsonl");
            if (Files.isRegularFile(log)) {
                return log;
            }
        }
        return null;
    }
}
