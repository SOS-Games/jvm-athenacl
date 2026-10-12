// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.DebugSink;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Times one event-log encode with debug off and one with a provenance observer.
 * The two encodes must match. The times are wall-clock, so they are not part of a hash.
 */
@CommandLine.Command(name = "overhead", description = "Compare event-log time with debug off and provenance on.")
public class OverheadCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<notes.yaml>")
    Path notes;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug overhead";
        if (notes == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug overhead <notes.yaml>",
                    "Pass a note list or a theme.");
        }
        if (!Files.isRegularFile(notes)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                    "file not found",
                    "Pass a note list or a theme.");
        }
        Score score;
        try {
            score = load();
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(), e.fix());
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the file is readable.");
        }
        byte[] plain = EventLog.encode(score);
        int[] seen = {0};
        long offNs = time(() -> EventLog.encode(score));
        long provenanceNs = time(() -> EventLog.encode(score, new DebugSink() {
            @Override
            public void note(NoteEvent note) {
                seen[0]++;
            }
        }));
        byte[] traced = EventLog.encode(score, new DebugSink() {
            @Override
            public void note(NoteEvent note) {
                seen[0]++;
            }
        });
        boolean identical = seen[0] >= score.notes().size() && Arrays.equals(plain, traced);
        double ratio = offNs == 0 ? 1.0 : (double) provenanceNs / (double) offNs;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("identical", Boolean.toString(identical));
        fields.put("off.ms", millis(offNs));
        fields.put("provenance.ms", millis(provenanceNs));
        fields.put("ratio", String.format(Locale.ROOT, "%.2f", ratio));
        fields.put("notes", Integer.toString(score.notes().size()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("identical", identical);
        data.put("offMs", offNs / 1_000_000.0);
        data.put("provenanceMs", provenanceNs / 1_000_000.0);
        data.put("ratio", ratio);
        data.put("notes", score.notes().size());
        ExitCode code = identical ? ExitCode.OK : ExitCode.CHECK_FAILED;
        return parent.finish(code, KeyValue.line(fields), "athenacl.result.overhead/1",
                data, List.of(), List.of());
    }

    private Score load() throws IOException, ScoreException, ThemeException {
        if (Themes.looksLike(notes)) {
            Theme theme = Themes.load(notes);
            return Director.perform(theme, theme.timeline(), seed).score();
        }
        return NoteFiles.yaml(notes, seed);
    }

    private static long time(Runnable work) {
        long started = System.nanoTime();
        work.run();
        return System.nanoTime() - started;
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
