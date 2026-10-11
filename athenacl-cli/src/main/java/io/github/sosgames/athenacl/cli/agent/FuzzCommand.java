// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.Quantize;
import io.github.sosgames.athenacl.adaptive.Scheduler;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.debug.StuckNotes;
import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.RandomSource;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Asks for state changes at seeded bar and beat positions and counts stuck notes and late landings.
 * The draws come from {@link RandomSource}. The wall clock is not used.
 */
@CommandLine.Command(name = "fuzz-transitions", description = "Try many seeded state changes and count stuck notes.")
public class FuzzCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;
    @CommandLine.Option(names = "--runs", defaultValue = "1000")
    int runs;
    @CommandLine.Option(names = "--seed", defaultValue = "1")
    long seed;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug fuzz-transitions";
        if (theme == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug fuzz-transitions <theme.yaml> --runs 1000 --seed 1",
                    "Pass a theme such as themes/fantasy-dungeon.yaml.");
        }
        if (!Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "theme not found",
                    "Pass a theme YAML file.");
        }
        if (runs < 1) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--runs", "runs must be 1 or more",
                    "Use --runs 1000.");
        }
        Theme loaded;
        try {
            loaded = Themes.load(theme);
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                    "Check that the theme file is readable.");
        }
        RandomSource rng = DefaultRandom.of(seed).fork("fuzz".hashCode());
        List<String> names = new ArrayList<>(loaded.states().keySet());
        int stuck = 0;
        int late = 0;
        for (int i = 0; i < runs; i++) {
            int bar = 1 + rng.nextInt(Math.max(1, loaded.bars()));
            int beat = 1 + rng.nextInt(loaded.numerator());
            String to = names.get(rng.nextInt(names.size()));
            double level = Themes.intensity(0.3 + rng.nextInt(8) / 10.0);
            List<Theme.Control> controls = new ArrayList<>();
            controls.add(new Theme.Control(1, 1, loaded.firstState(), 0.35));
            if (!(bar == 1 && beat == 1 && to.equals(loaded.firstState()))) {
                controls.add(new Theme.Control(bar, beat, to, level));
            }
            Director.Performance performance = Director.perform(loaded, controls, seed);
            stuck += StuckNotes.count(performance.score());
            for (Director.Transition transition : performance.transitions()) {
                if (Scheduler.late(transition.requestTick(), transition.appliedTick(),
                        Quantize.parse(transition.quantize()), loaded.barTicks(), loaded.bpm())) {
                    late++;
                }
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("runs", Integer.toString(runs));
        fields.put("stuck", Integer.toString(stuck));
        fields.put("lateTransitions", Integer.toString(late));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runs", runs);
        data.put("stuck", stuck);
        data.put("lateTransitions", late);
        ExitCode code = stuck == 0 && late == 0 ? ExitCode.OK : ExitCode.CHECK_FAILED;
        return parent.finish(code, KeyValue.line(fields), "athenacl.result.fuzz/1", data, List.of(), List.of());
    }
}
