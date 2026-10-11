// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.KitSession;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.Playable;
import io.github.sosgames.athenacl.instruments.ProbeNormalizer;
import io.github.sosgames.athenacl.render.BankMemory;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.render.loudness.LoudnessMeter;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import picocli.CommandLine;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Lists, shows, auditions, and compares named instruments from a kit.
 * The default kit is {@code gm-lite}. Audition and compare write WAVs under {@code --out}.
 */
@CommandLine.Command(name = "instruments", description = "List or audition instruments from a kit.",
        subcommands = {InstrumentsCommand.ListInstruments.class, InstrumentsCommand.Show.class,
                InstrumentsCommand.Audition.class, InstrumentsCommand.Compare.class})
public class InstrumentsCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "instruments";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: instruments list | instruments show <name> | instruments audition <name> | instruments compare <name>",
                "Try instruments list --kit gm-lite.");
    }

    AgentCli cli() {
        return parent;
    }

    private static int openCode;

    static KitSession open(AgentCli parent, String kit, Path cache) {
        String id = kit == null || kit.isBlank() ? "gm-lite" : kit;
        try {
            openCode = 0;
            return KitSession.open(Kits.find(id), SoundbankPaths.cache(cache), false);
        } catch (KitException e) {
            KitException.Issue issue = e.issues().get(0);
            openCode = parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
            return null;
        } catch (IOException e) {
            openCode = parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit", e.getMessage(),
                    "Check that the kit file and the soundfont cache are readable.");
            return null;
        }
    }

    @CommandLine.Command(name = "list", description = "List the instruments in a kit and the slot each one uses.")
    public static class ListInstruments implements Callable<Integer> {
        @CommandLine.ParentCommand InstrumentsCommand instruments;
        @CommandLine.Option(names = "--kit", paramLabel = "<id|file>", defaultValue = "gm-lite")
        String kit;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = instruments.cli();
            parent.commandName = "instruments list";
            KitSession session = open(parent, kit, cache);
            if (session == null) {
                return openCode;
            }
            List<Playable> rows = new ArrayList<>(session.instruments());
            rows.sort(Comparator.comparing(Playable::name));
            StringBuilder text = new StringBuilder();
            for (Playable playable : rows) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(line(playable));
            }
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.instrument/1",
                    Map.of("kit", session.kit().id(), "instruments", rows.size()), List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "show", description = "Show one instrument: source, license, slot, and optional sample size.")
    public static class Show implements Callable<Integer> {
        @CommandLine.ParentCommand InstrumentsCommand instruments;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<name>")
        String name;
        @CommandLine.Option(names = "--kit", paramLabel = "<id|file>", defaultValue = "gm-lite")
        String kit;
        @CommandLine.Option(names = "--memory")
        boolean memory;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = instruments.cli();
            parent.commandName = "instruments show";
            if (name == null || name.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: instruments show <name> --kit <id>",
                        "Try instruments show harp --kit gm-lite.");
            }
            KitSession session = open(parent, kit, cache);
            if (session == null) {
                return openCode;
            }
            Playable playable = session.get(name);
            if (playable == null) {
                return parent.fail(ExitCode.INVALID_INPUT, "KIT_UNKNOWN_INSTRUMENT", "argv[2]",
                        "unknown instrument " + name, "Use instruments list --kit " + session.kit().id() + ".");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("instrument", playable.name());
            fields.put("kit", session.kit().id());
            fields.put("source", playable.sourceKey());
            if (playable.percussion()) {
                fields.put("drumkit", Integer.toString(playable.drumkit()));
            } else {
                fields.put("bank", Integer.toString(playable.sourceBank()));
                fields.put("program", Integer.toString(playable.sourceProgram()));
            }
            fields.put("slot", playable.slotText());
            fields.put("license", playable.license());
            fields.put("gainDb", String.format(Locale.ROOT, "%.1f", playable.gainDb()));
            fields.put("transpose", Integer.toString(playable.transpose()));
            if (playable.keyLow() != null) {
                fields.put("keyRange", playable.keyLow() + ".." + playable.keyHigh());
            }
            if (!playable.fallbacksTaken().isEmpty()) {
                fields.put("fallbacks", String.join(",", playable.fallbacksTaken()));
            }
            if (memory) {
                try {
                    double mb = BankMemory.sampleBytes(ProbeNormalizer.spec(playable)) / (1024.0 * 1024.0);
                    fields.put("sampleMB", String.format(Locale.ROOT, "%.1f", mb));
                } catch (Exception e) {
                    return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--memory", e.getMessage(),
                            "Check that the soundfont is installed.");
                }
            }
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.instrument/1",
                    new LinkedHashMap<>(fields), List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "audition", description = "Render a few notes of one instrument and print the probe loudness.")
    public static class Audition implements Callable<Integer> {
        @CommandLine.ParentCommand InstrumentsCommand instruments;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<name>")
        String name;
        @CommandLine.Option(names = "--kit", paramLabel = "<id|file>", defaultValue = "gm-lite")
        String kit;
        @CommandLine.Option(names = "--notes", paramLabel = "<C4,E4,G4>", defaultValue = "C4,E4,G4")
        String notes;
        @CommandLine.Option(names = "--out", paramLabel = "<dir>")
        Path out;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = instruments.cli();
            parent.commandName = "instruments audition";
            if (name == null || out == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: instruments audition <name> --kit <id> --notes C4,E4,G4 --out <dir>",
                        "Pass --out as a directory this command may create.");
            }
            KitSession session = open(parent, kit, cache);
            if (session == null) {
                return openCode;
            }
            Playable playable = session.get(name);
            if (playable == null) {
                return parent.fail(ExitCode.INVALID_INPUT, "KIT_UNKNOWN_INSTRUMENT", "argv[2]",
                        "unknown instrument " + name, "Use instruments list --kit " + session.kit().id() + ".");
            }
            try {
                ProbeNormalizer.Level level = ProbeNormalizer.measure(playable);
                byte[] wav = WavRenderer.write(phrase(playable, notes, level.cc7()), null, List.of(ProbeNormalizer.spec(playable)));
                AtomicFiles.write(out.resolve("score.wav"), wav, parent.dryRun);
                Path temp = Files.createTempFile("athenacl-audition", ".wav");
                double probe;
                try {
                    Files.write(temp, wav);
                    probe = LoudnessMeter.measure(temp).integrated();
                } finally {
                    Files.deleteIfExists(temp);
                }
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("instrument", playable.name());
                fields.put("kit", session.kit().id());
                fields.put("loudness.probe", String.format(Locale.ROOT, "%.1f", probe));
                fields.put("slot", playable.slotText());
                return parent.finish(ExitCode.OK, KeyValue.line(fields) + " LUFS", "athenacl.result.instrument/1",
                        new LinkedHashMap<>(fields), List.of(), List.of());
            } catch (Exception e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                        "Check that the soundfont is installed and --out is writable.");
            }
        }
    }

    @CommandLine.Command(name = "compare", description = "Probe the same instrument in two kits and report the raw loudness gap.")
    public static class Compare implements Callable<Integer> {
        @CommandLine.ParentCommand InstrumentsCommand instruments;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<name>")
        String name;
        @CommandLine.Option(names = "--kits", paramLabel = "<gm-lite,gm-full>", required = true)
        String kits;
        @CommandLine.Option(names = "--out", paramLabel = "<dir>")
        Path out;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = instruments.cli();
            parent.commandName = "instruments compare";
            if (name == null || out == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: instruments compare <name> --kits gm-lite,gm-full --out <dir>",
                        "Pass two kit ids and an output directory.");
            }
            String[] ids = kits.split(",");
            StringBuilder text = new StringBuilder();
            List<Double> raw = new ArrayList<>();
            List<Double> probes = new ArrayList<>();
            try {
                for (String id : ids) {
                    KitSession session = open(parent, id.trim(), cache);
                    if (session == null) {
                        return openCode;
                    }
                    Playable playable = session.get(name);
                    if (playable == null) {
                        return parent.fail(ExitCode.INVALID_INPUT, "KIT_UNKNOWN_INSTRUMENT", "--kits",
                                "unknown instrument " + name, "Use a name that kit defines, such as piano.");
                    }
                    ProbeNormalizer.Level level = ProbeNormalizer.measure(playable);
                    byte[] wav = WavRenderer.write(phrase(playable, "C3,C4,C5", level.cc7()), null,
                            List.of(ProbeNormalizer.spec(playable)));
                    AtomicFiles.write(out.resolve(session.kit().id() + ".wav"), wav, parent.dryRun);
                    raw.add(level.rawLufs());
                    probes.add(level.probeLufs());
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append("instrument=").append(playable.name())
                            .append(" kit=").append(session.kit().id())
                            .append(" loudness.probe=").append(String.format(Locale.ROOT, "%.1f", level.probeLufs()))
                            .append(" raw.lufs=").append(String.format(Locale.ROOT, "%.1f", level.rawLufs()));
                }
            } catch (Exception e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                        "Check that both soundfonts are installed and --out is writable.");
            }
            if (raw.size() >= 2) {
                double delta = Math.abs(raw.get(0) - raw.get(1));
                text.append("\nraw.deltaLu=").append(String.format(Locale.ROOT, "%.1f", delta));
                boolean ok = probes.stream().allMatch(value -> Math.abs(value - ProbeNormalizer.TARGET) <= 0.5);
                if (ok) {
                    text.append("\nnormalized=ok");
                }
            }
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.instrument/1",
                    Map.of("instrument", name), List.of(), List.of());
        }
    }

    private static String line(Playable playable) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("instrument", playable.name());
        fields.put("kit", playable.kitId());
        fields.put("source", playable.sourceKey());
        if (playable.percussion()) {
            fields.put("drumkit", Integer.toString(playable.drumkit()));
        } else {
            fields.put("bank", Integer.toString(playable.sourceBank()));
            fields.put("program", Integer.toString(Math.max(0, playable.sourceProgram())));
        }
        fields.put("slot", playable.slotText());
        return KeyValue.line(fields);
    }

    private static Score phrase(Playable playable, String notes, int cc7) {
        int channel = playable.percussion() ? 9 : 0;
        List<ControlEvent> controls = new ArrayList<>(ProbeNormalizer.controls(playable, channel, cc7));
        List<NoteEvent> built = new ArrayList<>();
        String[] names = notes.split(",");
        for (int i = 0; i < names.length; i++) {
            int pitch = PitchName.midi(names[i].trim());
            built.add(new NoteEvent(i + 1L, i * 1920L, 1920, pitch, names[i].trim(), 100, channel,
                    playable.name(), 1, "1", "1/2", "audition", null));
        }
        return new Score(controls, built, 60, 4, 4, Math.max(1, built.size()));
    }
}
