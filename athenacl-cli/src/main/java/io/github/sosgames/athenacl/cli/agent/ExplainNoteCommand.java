// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.debug.Inspect;
import io.github.sosgames.athenacl.events.NoteEvent;
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
 * Same notes as {@code debug bar}, without the bar header. Each line says why the note exists.
 * An event log is read in place: {@code --where layer=lead --first} prints the first matching
 * note, including {@code hook.cand=} and {@code score=} when the lead is a chosen hook.
 */
@CommandLine.Command(name = "explain", description = "Print why each note in one bar was chosen.")
public class ExplainNoteCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml|events.jsonl>")
    Path theme;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;
    @CommandLine.Option(names = "--bar", paramLabel = "<n>")
    Integer bar;
    @CommandLine.Option(names = "--where", paramLabel = "<field=value>")
    String where;
    @CommandLine.Option(names = "--note", paramLabel = "<id>")
    Long note;
    @CommandLine.Option(names = "--first")
    boolean first;

    @Override
    public Integer call() {
        if (theme != null && theme.getFileName() != null
                && theme.getFileName().toString().endsWith(".jsonl")) {
            return events(debug.cli(), theme, where, note, first);
        }
        return BarCommand.report(debug.cli(), "debug explain", theme, seed, bar, false);
    }

    private static int events(AgentCli parent, Path path, String where, Long note, boolean first) {
        parent.commandName = "debug explain";
        if (!Files.isRegularFile(path)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "event log not found",
                    "Render with --formats events first, then pass that events.jsonl.");
        }
        Score score;
        try {
            score = NoteFiles.events(path);
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the event log is readable.");
        }
        String filter = where;
        if (note != null) {
            filter = "id=" + note;
        }
        List<NoteEvent> matches;
        try {
            matches = matched(score, filter);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--where", e.getMessage(),
                    "Use a filter such as layer=lead.");
        }
        if (first && !matches.isEmpty()) {
            matches = List.of(matches.get(0));
        }
        StringBuilder text = new StringBuilder();
        for (NoteEvent item : matches) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", Long.toString(item.id()));
            fields.put("layer", item.layer());
            fields.put("pitch", item.pitchName());
            HookLabel.put(fields, item.motif());
            text.append(KeyValue.record("note", fields)).append('\n');
        }
        String body = text.isEmpty() ? "" : text.substring(0, text.length() - 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matches", matches.size());
        return parent.finish(ExitCode.OK, body, "athenacl.result.explain/1", data, List.of(), List.of());
    }

    private static List<NoteEvent> matched(Score score, String where) {
        List<String> lines = Inspect.lines(score, where);
        List<NoteEvent> notes = new java.util.ArrayList<>();
        for (NoteEvent note : score.notes()) {
            if (lines.contains(Inspect.line(note))) {
                notes.add(note);
            }
        }
        return notes;
    }
}
