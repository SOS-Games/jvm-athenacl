// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.Config;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.render.loudness.StateLoudness;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a theme offline. {@code --timeline} replaces the theme's demo script.
 * {@code --at} adds one control, such as {@code 9 state combat 0.6}. {@code --bars 9-10}
 * prints only that window. The same seed and the same controls always hash the same.
 */
final class ThemeCli {
    private static int parentCode;

    private ThemeCli() {
    }

    static int render(AgentCli parent, Path themePath, Path timeline, String barWindow, List<String> at,
                      long seed, Path outDir, String formats, String soundbank, Path cache) {
        parent.commandName = "render";
        Loaded loaded = load(parent, themePath, timeline, at);
        if (loaded == null) {
            return parentCode;
        }
        BarRange range = null;
        if (barWindow != null) {
            try {
                range = BarRange.parse(barWindow);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--bars", e.getMessage(),
                        "Use a window like 9-10.");
            }
        }
        Director.Performance full = Director.perform(loaded.theme, loaded.controls, seed);
        Director.Performance shown = range == null ? full : Director.slice(full, range.from, range.to);
        if (outDir == null) {
            String text = new String(EventLog.encode(shown.score()), StandardCharsets.UTF_8);
            if (!text.isEmpty() && !text.endsWith("\n")) {
                text = text + "\n";
            }
            String summary = windowLine(range, shown);
            return parent.finish(ExitCode.OK, text + summary, "athenacl.result.render/1",
                    data(shown, EventLog.hash(shown.score()), false, false, seed), List.of(), List.of());
        }
        List<String> requested;
        try {
            requested = RenderCommand.parseFormats(formats);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--formats", e.getMessage(),
                    "Use a comma list of mid, wav, events, txt.");
        }
        SoundbankResolver.Selection selection;
        try {
            String request = soundbank != null ? soundbank : Config.get("athenacl.soundbank");
            selection = SoundbankResolver.select(request, SoundbankPaths.cache(cache));
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--soundbank", e.getMessage(),
                    "Check that the soundbank file is readable.");
        }
        Score score = full.score();
        List<String> stateByBar = new ArrayList<>();
        for (Director.BarInfo bar : full.bars()) {
            stateByBar.add(bar.state());
        }
        Map<String, byte[]> bodies = new LinkedHashMap<>();
        try {
            for (String format : requested) {
                byte[] body = RenderCommand.body(format, score, selection.file());
                if ("wav".equals(format) && loaded.theme.loudness() != null) {
                    body = StateLoudness.fit(body, score.bpm(), score.barTicks(), stateByBar,
                            loaded.theme.loudness().reference(), loaded.theme.loudness().offsets());
                }
                bodies.put(format, body);
            }
        } catch (MidiUnavailableException e) {
            return parent.fail(ExitCode.ENVIRONMENT, "GERVILL_ABSENT", "gervill",
                    "this JDK cannot open an offline Gervill stream",
                    "Use a JDK that includes com.sun.media.sound and keep --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED.");
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--formats", e.getMessage(),
                    "Rendering the WAV or MIDI file failed. Try again, or drop wav from --formats.");
        }
        Double reference = loaded.theme.loudness() == null ? null : loaded.theme.loudness().reference();
        bodies.put("control", ControlLog.encode(TextPaths.portable(themePath), seed, loaded.theme.bars(),
                loaded.controls, reference));
        boolean changed = false;
        try {
            for (Map.Entry<String, byte[]> entry : bodies.entrySet()) {
                String name = entry.getKey().equals("control") ? "control.jsonl" : RenderCommand.fileName(entry.getKey());
                AtomicFiles.WritePlan plan = AtomicFiles.write(outDir.resolve(name), entry.getValue(), parent.dryRun);
                changed = changed || plan.changed();
            }
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that --out is a writable directory and try again.");
        }
        String hash = EventLog.hash(score);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("bars", Integer.toString(loaded.theme.bars()));
        fields.put("states", full.stateList());
        fields.put("transitions", Integer.toString(full.transitions().size()));
        fields.put("hash", hash);
        fields.put("dryRun", Boolean.toString(parent.dryRun));
        fields.put("changed", Boolean.toString(changed));
        Map<String, Object> payload = data(full, hash, changed, parent.dryRun, seed);
        payload.put("files", requested.size() + 1);
        payload.put("states", full.stateList());
        payload.put("transitions", full.transitions().size());
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.render/1",
                payload, RenderCommand.warnings(selection), List.of());
    }

    static Director.Performance perform(AgentCli parent, Path themePath, Path timeline, List<String> at, long seed) {
        Loaded loaded = load(parent, themePath, timeline, at);
        if (loaded == null) {
            return null;
        }
        return Director.perform(loaded.theme, loaded.controls, seed);
    }

    static int remember(int code) {
        parentCode = code;
        return code;
    }

    static int failureCode() {
        return parentCode;
    }

    private static Loaded load(AgentCli parent, Path themePath, Path timeline, List<String> at) {
        Theme theme;
        try {
            theme = Themes.load(themePath);
        } catch (ThemeException e) {
            ThemeIssue issue = e.issues().get(0);
            parentCode = parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
            return null;
        } catch (IOException e) {
            parentCode = parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the theme file is readable.");
            return null;
        }
        List<Theme.Control> base;
        try {
            base = timeline == null ? theme.timeline() : ControlLog.readTimeline(timeline);
        } catch (IOException e) {
            parentCode = parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--timeline", e.getMessage(),
                    "Pass a JSON array of bar, state, and intensity.");
            return null;
        }
        List<Theme.Control> extra = new ArrayList<>();
        if (at != null) {
            for (String text : at) {
                try {
                    extra.add(parseAt(text));
                } catch (IllegalArgumentException e) {
                    parentCode = parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--at", e.getMessage(),
                            "Use a control like \"9 state combat 0.6\".");
                    return null;
                }
            }
        }
        List<Theme.Control> controls = Director.merge(base, extra);
        ThemeIssue unknown = Director.unknown(theme, controls);
        if (unknown != null) {
            parentCode = parent.fail(ExitCode.INVALID_INPUT, unknown.code(), unknown.path(), unknown.message(), unknown.fix());
            return null;
        }
        return new Loaded(theme, controls);
    }

    static Theme.Control parseAt(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("at is empty");
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 3 || !parts[1].equals("state")) {
            throw new IllegalArgumentException("at " + text);
        }
        try {
            int bar = Integer.parseInt(parts[0]);
            double intensity = parts.length >= 4 ? Themes.intensity(Double.parseDouble(parts[3])) : 0.3;
            return new Theme.Control(bar, 1, parts[2], intensity);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("at " + text);
        }
    }

    static String barText(Director.Performance performance, int bar, boolean header) {
        Director.BarInfo info = null;
        for (Director.BarInfo candidate : performance.bars()) {
            if (candidate.bar() == bar) {
                info = candidate;
                break;
            }
        }
        if (info == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (header) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("bar", Integer.toString(info.bar()));
            fields.put("state", info.state());
            fields.put("intensity", String.format(java.util.Locale.ROOT, "%.2f", info.intensity()));
            fields.put("key", info.key());
            fields.put("bpm", Integer.toString(info.bpm()));
            text.append(KeyValue.line(fields)).append('\n');
        }
        for (Director.Played played : performance.notes()) {
            if (played.note().bar() != bar) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("id", Long.toString(played.note().id()));
            fields.put("layer", played.note().layer());
            fields.put("seed", played.note().seedPath());
            fields.put("why", played.why());
            text.append(KeyValue.record("note", fields)).append('\n');
        }
        if (text.isEmpty()) {
            return null;
        }
        return text.substring(0, text.length() - 1);
    }

    private static String windowLine(BarRange range, Director.Performance shown) {
        String bars = range == null
                ? Integer.toString(shown.score().bars())
                : range.from + "-" + range.to;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("bars", bars);
        fields.put("notes", Integer.toString(shown.notes().size()));
        fields.put("hash", EventLog.hash(shown.score()));
        return KeyValue.line(fields);
    }

    private static Map<String, Object> data(Director.Performance shown, String hash, boolean changed,
                                            boolean dryRun, long seed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("files", 0);
        data.put("notes", shown.notes().size());
        data.put("bars", shown.score().bars());
        data.put("changed", changed);
        data.put("hash", hash);
        data.put("dryRun", dryRun);
        data.put("seed", Long.toString(seed));
        return data;
    }

    private record Loaded(Theme theme, List<Theme.Control> controls) {
    }

    record BarRange(int from, int to) {
        static BarRange parse(String text) {
            String[] parts = text.trim().split("-", -1);
            if (parts.length == 1) {
                int bar = Integer.parseInt(parts[0].trim());
                if (bar < 1) {
                    throw new IllegalArgumentException("bars " + text);
                }
                return new BarRange(bar, bar);
            }
            if (parts.length != 2) {
                throw new IllegalArgumentException("bars " + text);
            }
            int from = Integer.parseInt(parts[0].trim());
            int to = Integer.parseInt(parts[1].trim());
            if (from < 1 || to < from) {
                throw new IllegalArgumentException("bars " + text);
            }
            return new BarRange(from, to);
        }
    }
}
