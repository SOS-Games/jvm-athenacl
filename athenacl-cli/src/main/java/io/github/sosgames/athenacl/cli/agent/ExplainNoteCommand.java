// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Same notes as {@code debug bar}, without the bar header. Each line says why the note exists. */
@CommandLine.Command(name = "explain", description = "Print why each note in one bar was chosen.")
public class ExplainNoteCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;
    @CommandLine.Option(names = "--bar", paramLabel = "<n>")
    Integer bar;

    @Override
    public Integer call() {
        return BarCommand.report(debug.cli(), "debug explain", theme, seed, bar, false);
    }
}
