// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.debug.StuckNotes;
import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Offline checks on a render directory. {@code --loudness} measures the WAV.
 * A theme render also fails when a loudness jump bigger than {@code maxStep} has no CC11 ramp.
 * {@code --stuck-notes} reads {@code events.jsonl} and exits 6 when a note never turns off.
 */
@CommandLine.Command(name = "check", description = "Check a render directory for loudness or stuck notes.")
public class CheckCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<wav-or-dir>")
    Path input;
    @CommandLine.Option(names = "--loudness")
    boolean loudness;
    @CommandLine.Option(names = "--stuck-notes")
    boolean stuckNotes;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug check";
        if (!loudness && !stuckNotes) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug check <dir> --loudness | --stuck-notes",
                    "Pass --loudness, --stuck-notes, or both.");
        }
        if (input == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug check <dir> --loudness | --stuck-notes",
                    "Pass the directory render wrote.");
        }
        if (stuckNotes) {
            int code = reportStuck(parent);
            if (!loudness || code != ExitCode.OK.number()) {
                return code;
            }
        }
        Path wav = AnalyzeCommand.wav(input);
        if (wav == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "wav not found",
                    "Render with --formats wav, then pass that directory or score.wav.");
        }
        String step = loudnessStep(input);
        if (step != null) {
            return parent.fail(ExitCode.CHECK_FAILED, "LOUDNESS_STEP", "transitions", step,
                    "Ramp CC11 across a jump larger than maxStepPerTransition.");
        }
        return LoudnessReports.finish(parent, wav, "athenacl.result.check/1");
    }

    /** Null when every large jump has a ramp, or when this directory is not a theme render. */
    private static String loudnessStep(Path input) {
        if (input == null || !Files.isDirectory(input)) {
            return null;
        }
        Path log = input.resolve("control.jsonl");
        if (!Files.isRegularFile(log)) {
            return null;
        }
        try {
            ControlLog.Log recorded = ControlLog.read(log);
            Path themePath = Path.of(recorded.theme());
            if (!Files.isRegularFile(themePath)) {
                return null;
            }
            Theme theme = Themes.load(themePath).withBars(recorded.bars());
            if (theme.loudness() == null) {
                return null;
            }
            Director.Performance performance = Director.perform(theme, recorded.controls(), recorded.seed());
            for (Director.Transition transition : performance.transitions()) {
                double step = Math.abs(theme.loudness().offset(transition.to())
                        - theme.loudness().offset(transition.from()));
                if (step <= theme.loudness().maxStep()) {
                    continue;
                }
                if (!ramped(performance, transition.appliedTick(), theme.barTicks())) {
                    return "step=" + io.github.sosgames.athenacl.adaptive.LoudnessContour.tenths(step)
                            + " ramp=false";
                }
            }
            return null;
        } catch (ThemeException | IOException e) {
            return null;
        }
    }

    private static boolean ramped(Director.Performance performance, long start, long barTicks) {
        long end = start + barTicks;
        for (ControlEvent event : performance.score().controls()) {
            if (event instanceof ControlEvent.ControlChange change
                    && change.controller() == 11
                    && change.startTick() >= start
                    && change.startTick() < end) {
                return true;
            }
        }
        return false;
    }

    private int reportStuck(AgentCli parent) {
        Path events = events(input);
        if (events == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "event log not found",
                    "Render with --formats events, then pass that directory or events.jsonl.");
        }
        try {
            Score score = NoteFiles.events(events);
            return finishStuck(parent, StuckNotes.count(score));
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the event log is readable.");
        }
    }

    private static int finishStuck(AgentCli parent, int stuck) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("stuck", Integer.toString(stuck));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stuck", stuck);
        ExitCode code = stuck == 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
        return parent.finish(code, KeyValue.line(fields), "athenacl.result.check/1",
                data, List.of(), List.of());
    }

    static Path events(Path input) {
        if (Files.isRegularFile(input) && input.getFileName().toString().endsWith(".jsonl")) {
            return input;
        }
        if (Files.isDirectory(input)) {
            Path log = input.resolve("events.jsonl");
            if (Files.isRegularFile(log)) {
                return log;
            }
        }
        return null;
    }
}
