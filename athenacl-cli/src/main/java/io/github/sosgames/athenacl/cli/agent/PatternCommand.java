// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.rhythm.DrumPattern;
import io.github.sosgames.athenacl.rhythm.Meter;
import picocli.CommandLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints a drum pattern. {@code x} is a hit and {@code .} is a rest. {@code --drum-grid}
 * adds a {@code kick |} row, one cell per step, repeated for each bar.
 */
@CommandLine.Command(name = "pattern", description = "Print a drum pattern, with an optional grid.")
public class PatternCommand implements Callable<Integer> {
    @CommandLine.ParentCommand RhythmCommand rhythm;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<x..x..>")
    String pattern;
    @CommandLine.Option(names = "--meter", paramLabel = "<n/d>", defaultValue = "4/4")
    String meterText;
    @CommandLine.Option(names = "--bars", paramLabel = "<n>", defaultValue = "1")
    int bars;
    @CommandLine.Option(names = "--drum-grid", description = "Print a kick row under the summary.")
    boolean drumGrid;

    @Override
    public Integer call() {
        AgentCli parent = rhythm.cli();
        parent.commandName = "rhythm pattern";
        if (pattern == null || pattern.isBlank()) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: rhythm pattern <x..x..> --meter 6/8 --bars 2 --drum-grid",
                    "Use x for a hit and . for a rest, as in x..x..x.");
        }
        try {
            Meter meter = Meter.parse(meterText);
            DrumPattern.Grid grid = DrumPattern.grid(pattern, meter, bars);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("bars", Integer.toString(grid.bars()));
            fields.put("onsets", Integer.toString(grid.onsets()));
            fields.put("meter", meter.text());
            fields.put("grouping", meter.grouping());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bars", grid.bars());
            data.put("onsets", grid.onsets());
            data.put("meter", meter.text());
            data.put("grouping", meter.grouping());
            String text = KeyValue.line(fields);
            if (drumGrid) {
                text = text + "\n" + grid.row();
                data.put("grid", grid.row());
            }
            return parent.finish(ExitCode.OK, text, "athenacl.result.rhythm/1", data, List.of(), List.of());
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(),
                    "Use a meter like 6/8 and a pattern of x and . such as x..x..x.");
        }
    }
}
