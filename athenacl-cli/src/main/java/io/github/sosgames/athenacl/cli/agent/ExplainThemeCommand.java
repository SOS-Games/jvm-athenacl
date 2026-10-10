// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.LoudnessContour;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Prints the states a theme will play: key, tempo, layers, and the default quantize.
 * A preset is already merged, so {@code preset=fantasy} and {@code loudness.combat=+4.0}
 * describe the theme the director will actually use.
 */
@CommandLine.Command(name = "explain", description = "Print the states and layers in a theme.")
public class ExplainThemeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand ThemeCommand themeCommand;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;

    @Override
    public Integer call() {
        AgentCli parent = themeCommand.cli();
        parent.commandName = "theme explain";
        if (theme == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: theme explain <theme.yaml>",
                    "Pass a theme such as samples/themes/poc.yaml.");
        }
        if (!Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "theme not found",
                    "Pass a theme YAML file.");
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
        StringBuilder text = new StringBuilder();
        Map<String, String> head = new LinkedHashMap<>();
        head.put("id", loaded.id());
        if (loaded.preset() != null) {
            head.put("preset", loaded.preset());
        }
        head.put("bars", Integer.toString(loaded.bars()));
        head.put("states", String.join(",", loaded.states().keySet()));
        head.put("quantize", loaded.defaultQuantize().id());
        head.put("motif.every", Integer.toString(loaded.motifEvery()));
        if (loaded.hold() != null) {
            head.put("hold", loaded.hold().state());
            head.put("intensity", Themes.number(loaded.hold().intensity()));
        }
        text.append(KeyValue.line(head));
        for (Theme.StateDef state : loaded.states().values()) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("state", state.id());
            fields.put("key", state.key().name());
            fields.put("layers", String.join(",", state.layers()));
            fields.put("tempo", Integer.toString(state.tempo()));
            if (state.stinger() != null) {
                fields.put("stinger", state.stinger());
            }
            text.append('\n').append(KeyValue.line(fields));
        }
        if (loaded.loudness() != null) {
            Map<String, String> contour = new LinkedHashMap<>();
            for (String state : loaded.states().keySet()) {
                contour.put("loudness." + state, LoudnessContour.tenths(loaded.loudness().offset(state)));
            }
            text.append('\n').append(KeyValue.line(contour));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", loaded.id());
        data.put("states", List.copyOf(loaded.states().keySet()));
        data.put("quantize", loaded.defaultQuantize().id());
        return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.explain/1",
                data, List.of(), List.of());
    }
}
