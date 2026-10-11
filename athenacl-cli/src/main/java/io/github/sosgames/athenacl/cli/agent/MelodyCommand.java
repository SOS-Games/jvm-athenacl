// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.motif.critic.MelodyCritic;
import io.github.sosgames.athenacl.motif.critic.WeightFit;
import io.github.sosgames.athenacl.motif.hook.HookSettings;
import io.github.sosgames.athenacl.motif.hook.MelodyPitches;
import io.github.sosgames.athenacl.theory.Key;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Hook generator and melody critic. {@code melody generate --explain} prints one line
 * per candidate and the pick. {@code melody score} ranks a MIDI line. {@code melody rate}
 * writes one comparison when {@code --choose} is set, so a check never waits for a person.
 * {@code melody fit} turns those rows into weights. The same ratings file always yields
 * the same hash.
 */
@CommandLine.Command(name = "melody", description = "Generate, score, rate, or fit a melody.",
        subcommands = {MelodyCommand.Generate.class, MelodyCommand.Score.class,
                MelodyCommand.Rate.class, MelodyCommand.Fit.class})
public class MelodyCommand implements Callable<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "melody";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: melody generate | melody score <file.mid> | melody rate | melody fit <ratings.jsonl>",
                "Try melody generate --key \"E minor\" --candidates 64 --seed 3 --explain.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "generate", description = "Grow hook candidates and keep the highest score.")
    static class Generate implements Callable<Integer> {
        @CommandLine.ParentCommand MelodyCommand melody;
        @CommandLine.Option(names = "--key", paramLabel = "<key>")
        String key;
        @CommandLine.Option(names = "--chords", paramLabel = "<romans>")
        String chords;
        @CommandLine.Option(names = "--candidates", paramLabel = "<n>")
        Integer candidates;
        @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
        long seed;
        @CommandLine.Option(names = "--explain")
        boolean explain;
        @CommandLine.Option(names = "--references", paramLabel = "<file.mid>")
        Path references;
        @CommandLine.Option(names = "--theme", paramLabel = "<theme.yaml>")
        Path theme;
        @CommandLine.Option(names = "--state", paramLabel = "<id>")
        String state;

        @Override
        public Integer call() {
            AgentCli parent = melody.cli();
            parent.commandName = "melody generate";
            int[] failed = new int[1];
            Loaded loaded = load(parent, key, theme, state, failed);
            if (loaded == null) {
                return failed[0];
            }
            int count = candidates == null ? loaded.settings.candidates() : candidates;
            if (count < 1) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--candidates",
                        "candidates must be at least 1", "Use a positive count, such as 64.");
            }
            List<int[]> refs;
            try {
                refs = references == null ? List.of() : List.of(readMidis(references));
            } catch (IOException e) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--references", e.getMessage(),
                        "Pass a MIDI melody the critic must not copy.");
            }
            List<int[]> harmony;
            try {
                harmony = MelodyCritic.chords(loaded.key, chords);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--chords", e.getMessage(),
                        "Use roman numerals such as \"i VI VII i\".");
            }
            HookSettings settings = loaded.settings.withCandidates(count);
            MelodyCritic.Ranked ranked = MelodyCritic.rank(loaded.key, seed,
                    loaded.state == null ? "melody" : loaded.state, settings, harmony, refs);
            MelodyCritic.Scored chosen = ranked.chosen();
            StringBuilder text = new StringBuilder();
            if (explain) {
                for (MelodyCritic.Scored candidate : ranked.candidates()) {
                    text.append(candidateLine(candidate)).append('\n');
                }
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("chosen", Integer.toString(chosen.cand()));
            fields.put("hook", "h1");
            fields.put("notes", Integer.toString(chosen.motif().notes().size()));
            fields.put("contour", chosen.contour());
            fields.put("form", chosen.form());
            fields.put("score", MelodyCritic.format(chosen.score()));
            text.append(KeyValue.line(fields));
            Map<String, Object> data = new LinkedHashMap<>();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("cand", chosen.cand());
            body.put("hook", "h1");
            body.put("notes", chosen.motif().notes().size());
            body.put("contour", chosen.contour());
            body.put("form", chosen.form());
            body.put("score", round3(chosen.score()));
            body.put("features", roundFeatures(chosen.features()));
            data.put("chosen", body);
            data.put("candidates", count);
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.melody/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "score", description = "Score one MIDI melody.")
    static class Score implements Callable<Integer> {
        @CommandLine.ParentCommand MelodyCommand melody;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<file.mid>")
        Path file;
        @CommandLine.Option(names = "--key", paramLabel = "<key>")
        String key;

        @Override
        public Integer call() {
            AgentCli parent = melody.cli();
            parent.commandName = "melody score";
            if (file == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: melody score <file.mid> --key \"C major\"",
                        "Pass a MIDI melody.");
            }
            if (!Files.isRegularFile(file)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "melody not found",
                        "Pass a MIDI file such as samples/melodies/folk-pd-01.mid.");
            }
            Key parsed;
            try {
                parsed = Key.parse(key == null || key.isBlank() ? "C major" : key);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--key", e.getMessage(),
                        "Use a key such as \"G major\".");
            }
            MelodyCritic.Scored scored;
            try {
                List<MelodyPitches.Note> notes = MelodyPitches.read(file);
                scored = MelodyCritic.scorePitches(MelodyPitches.midis(notes), MelodyPitches.durations(notes),
                        parsed, HookSettings.defaults(), List.of(), List.of());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                        "Check that the MIDI file is readable.");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("score", MelodyCritic.format(scored.score()));
            fields.put("rejected", scored.rejected() == null ? "-" : scored.rejected());
            if (scored.rejected() == null) {
                for (String name : MelodyCritic.FEATURES) {
                    fields.put(name, MelodyCritic.format(scored.features().get(name)));
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("score", round3(scored.score()));
            data.put("rejected", scored.rejected() == null ? "-" : scored.rejected());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.melody/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "rate", description = "Write one A/B rating. --choose skips the prompt.")
    static class Rate implements Callable<Integer> {
        @CommandLine.ParentCommand MelodyCommand melody;
        @CommandLine.Option(names = "--key", paramLabel = "<key>")
        String key;
        @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
        long seed;
        @CommandLine.Option(names = "--choose", paramLabel = "<a|b>")
        String choose;
        @CommandLine.Option(names = "--out", paramLabel = "<ratings.jsonl>")
        Path out;

        @Override
        public Integer call() {
            AgentCli parent = melody.cli();
            parent.commandName = "melody rate";
            Key parsed;
            try {
                parsed = Key.parse(key == null || key.isBlank() ? "C major" : key);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--key", e.getMessage(),
                        "Use a key such as \"C major\".");
            }
            if (choose != null && !"a".equals(choose) && !"b".equals(choose)) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--choose",
                        "choose must be a or b", "Pass --choose a or --choose b.");
            }
            MelodyCritic.Ranked ranked = MelodyCritic.rank(parsed, seed, "rate",
                    HookSettings.defaults().withCandidates(2), List.of(), List.of());
            MelodyCritic.Scored first = ranked.candidates().get(0);
            MelodyCritic.Scored second = ranked.candidates().get(1);
            StringBuilder text = new StringBuilder();
            text.append(pairLine("a", first)).append('\n');
            text.append(pairLine("b", second)).append('\n');
            text.append("roll.a=").append(roll(first)).append('\n');
            text.append("roll.b=").append(roll(second)).append('\n');
            String winner = choose == null ? "-" : choose;
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("winner", winner);
            text.append(KeyValue.line(fields));
            if (choose != null) {
                if (out == null) {
                    return parent.fail(ExitCode.USAGE, "USAGE", "--out",
                            "usage: melody rate --choose a --out <ratings.jsonl>",
                            "Name the jsonl file that should receive this rating.");
                }
                String line = ratingJson(choose, first, second);
                try {
                    AtomicFiles.write(out, (line + "\n").getBytes(StandardCharsets.UTF_8), parent.dryRun);
                } catch (IOException e) {
                    return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                            "Check that the ratings file can be written.");
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("winner", winner);
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.melody/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "fit", description = "Fit critic weights from a ratings jsonl file.")
    static class Fit implements Callable<Integer> {
        @CommandLine.ParentCommand MelodyCommand melody;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<ratings.jsonl>")
        Path ratings;
        @CommandLine.Option(names = "--out", paramLabel = "<weights.yaml>")
        Path out;

        @Override
        public Integer call() {
            AgentCli parent = melody.cli();
            parent.commandName = "melody fit";
            if (ratings == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: melody fit <ratings.jsonl> --out <weights.yaml>",
                        "Pass a jsonl file of A/B ratings.");
            }
            if (!Files.isRegularFile(ratings)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "ratings not found",
                        "Pass samples/ratings/synthetic.jsonl or a file from melody rate.");
            }
            List<double[]> rows = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(ratings, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    rows.add(diff(JSON.readTree(line)));
                }
            } catch (IOException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(),
                        "Each line must be one rating object.");
            }
            if (rows.isEmpty()) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", "ratings file is empty",
                        "Add at least one winner line.");
            }
            WeightFit.Model model = WeightFit.fit(new WeightFit.ListView() {
                @Override
                public int n() {
                    return rows.size();
                }

                @Override
                public double diff(int row, int feature) {
                    return rows.get(row)[feature];
                }
            }, MelodyCritic.defaultWeights());
            if (out != null) {
                try {
                    AtomicFiles.write(out, model.yaml().getBytes(StandardCharsets.UTF_8), parent.dryRun);
                } catch (IOException e) {
                    return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                            "Check that the weights file can be written.");
                }
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("n", Integer.toString(model.n()));
            fields.put("accuracy.cv", String.format(Locale.ROOT, "%.2f", model.accuracy()));
            fields.put("weightsHash", model.hash());
            fields.put("order", String.join(",", model.order()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("n", model.n());
            data.put("accuracy.cv", String.format(Locale.ROOT, "%.2f", model.accuracy()));
            data.put("weightsHash", model.hash());
            data.put("order", String.join(",", model.order()));
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.melody/1",
                    data, List.of(), List.of());
        }
    }

    private static double[] diff(JsonNode node) {
        boolean aWins = !"b".equals(node.path("winner").asText("a"));
        double[] diff = new double[MelodyCritic.FEATURES.length];
        for (int i = 0; i < MelodyCritic.FEATURES.length; i++) {
            String name = MelodyCritic.FEATURES[i];
            double a = node.path("a").path(name).asDouble();
            double b = node.path("b").path(name).asDouble();
            diff[i] = (aWins ? a : b) - (aWins ? b : a);
        }
        return diff;
    }

    private static String ratingJson(String winner, MelodyCritic.Scored a, MelodyCritic.Scored b) {
        return "{\"winner\":\"" + winner + "\",\"a\":" + featureJson(a) + ",\"b\":" + featureJson(b) + "}";
    }

    private static String featureJson(MelodyCritic.Scored scored) {
        StringBuilder text = new StringBuilder("{");
        for (int i = 0; i < MelodyCritic.FEATURES.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            String name = MelodyCritic.FEATURES[i];
            double value = scored.features().getOrDefault(name, 0.0);
            text.append('"').append(name).append("\":").append(MelodyCritic.format(value));
        }
        return text.append('}').toString();
    }

    private static String pairLine(String name, MelodyCritic.Scored scored) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pair", name);
        fields.put("cand", Integer.toString(scored.cand()));
        fields.put("score", MelodyCritic.format(scored.score()));
        fields.put("contour", scored.contour());
        fields.put("notes", Integer.toString(scored.motif().notes().size()));
        return KeyValue.line(fields);
    }

    private static String roll(MelodyCritic.Scored scored) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < scored.motif().notes().size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(scored.motif().notes().get(i).degree());
        }
        return text.toString();
    }

    private static String candidateLine(MelodyCritic.Scored candidate) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("cand", Integer.toString(candidate.cand()));
        if (candidate.rejected() != null) {
            fields.put("rejected", candidate.rejected());
            return KeyValue.line(fields);
        }
        fields.put("score", MelodyCritic.format(candidate.score()));
        for (String name : MelodyCritic.FEATURES) {
            fields.put(name, MelodyCritic.format(candidate.features().get(name)));
        }
        fields.put("rejected", "-");
        return KeyValue.line(fields);
    }

    private static Map<String, Double> roundFeatures(Map<String, Double> features) {
        Map<String, Double> rounded = new LinkedHashMap<>();
        for (String name : MelodyCritic.FEATURES) {
            rounded.put(name, round3(features.getOrDefault(name, 0.0)));
        }
        return rounded;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static int[] readMidis(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("melody not found");
        }
        return MelodyPitches.midis(MelodyPitches.read(path));
    }

    private record Loaded(Key key, HookSettings settings, String state) {
    }

    private static Loaded load(AgentCli parent, String key, Path theme, String state, int[] failed) {
        HookSettings settings = HookSettings.defaults();
        Key parsed = null;
        if (theme != null) {
            if (!Files.isRegularFile(theme)) {
                failed[0] = parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--theme", "theme not found",
                        "Pass a theme YAML file.");
                return null;
            }
            try {
                Theme loaded = Themes.load(theme);
                String stateId = state == null || state.isBlank() ? loaded.firstState() : state;
                if (loaded.state(stateId) == null) {
                    failed[0] = parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--state",
                            "unknown state " + stateId, "Use a state named in the theme.");
                    return null;
                }
                if (loaded.hooks() != null) {
                    settings = loaded.hooks().forState(stateId);
                }
                parsed = loaded.state(stateId).key();
            } catch (ThemeException e) {
                ThemeIssue issue = e.issues().get(0);
                failed[0] = parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(),
                        issue.message(), issue.fix());
                return null;
            } catch (IOException e) {
                failed[0] = parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--theme", e.getMessage(),
                        "Check that the theme file is readable.");
                return null;
            }
        }
        if (key != null && !key.isBlank()) {
            try {
                parsed = Key.parse(key);
            } catch (IllegalArgumentException e) {
                failed[0] = parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--key", e.getMessage(),
                        "Use a key such as \"E minor\".");
                return null;
            }
        }
        if (parsed == null) {
            parsed = Key.parse("C major");
        }
        return new Loaded(parsed, settings, state);
    }
}
