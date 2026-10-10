// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.analysis.DescribeMusic;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Prints one summary line per bar from an event log. {@code --roll} adds a piano roll
 * under each bar. A quarter note on the downbeat starts {@code C4 |●}.
 */
@CommandLine.Command(name = "describe-music", description = "Summarize an event log, with an optional piano roll.")
public class DescribeMusicCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<events.jsonl>")
    Path events;
    @CommandLine.Option(names = "--roll", description = "Print a piano roll under each bar.")
    boolean roll;
    @CommandLine.Option(names = "--bars", paramLabel = "<from..to>")
    String bars;

    @Override
    public Integer call() {
        parent.commandName = "describe-music";
        if (events == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]", "usage: describe-music <events.jsonl>",
                    "Pass the events.jsonl written by render.");
        }
        if (!Files.isRegularFile(events)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]",
                    "event log not found",
                    "Render with --formats events first, then pass that events.jsonl.");
        }
        Score score;
        try {
            score = NoteFiles.events(events);
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]", e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the event log is readable.");
        }
        int from = 1;
        int to = Math.max(1, score.bars());
        if (bars != null && !bars.isBlank()) {
            try {
                int[] range = parseBars(bars);
                from = range[0];
                to = range[1];
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--bars", e.getMessage(),
                        "Use a bar such as 1 or a range such as 1..2.");
            }
        }
        String text = DescribeMusic.text(score, roll, from, to);
        return parent.finish(ExitCode.OK, text, "athenacl.result.describe-music/1",
                DescribeMusic.data(score, roll, from, to), List.of(), List.of());
    }

    static int[] parseBars(String text) {
        String value = text.trim();
        int dots = value.indexOf("..");
        int dash = value.indexOf('-');
        int split = dots >= 0 ? dots : dash;
        try {
            if (split < 0) {
                int bar = Integer.parseInt(value);
                if (bar < 1) {
                    throw new IllegalArgumentException("--bars must start at 1");
                }
                return new int[] {bar, bar};
            }
            int width = dots >= 0 ? 2 : 1;
            int from = Integer.parseInt(value.substring(0, split).trim());
            int to = Integer.parseInt(value.substring(split + width).trim());
            if (from < 1 || to < from) {
                throw new IllegalArgumentException("--bars range is empty");
            }
            return new int[] {from, to};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--bars must look like 1 or 1..2");
        }
    }
}
