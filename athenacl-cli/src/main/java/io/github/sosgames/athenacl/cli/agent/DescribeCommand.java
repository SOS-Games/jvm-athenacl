// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.theory.Chord;
import io.github.sosgames.athenacl.theory.Key;
import io.github.sosgames.athenacl.render.soundbank.InstrumentIndex;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCatalog;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankRejected;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;
import picocli.CommandLine;

import javax.sound.midi.InvalidMidiDataException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code describe scale} lists a mode, {@code describe chord} names a roman numeral,
 * and {@code describe instruments} counts presets in a soundbank. GeneralUser GS is
 * {@code instruments=287 drumkits=13}. The bank has to be installed first.
 */
@CommandLine.Command(name = "describe", description = "Describe a scale, a chord, or a soundbank.",
        subcommands = {
                DescribeCommand.ScaleName.class,
                DescribeCommand.ChordName.class,
                DescribeCommand.Instruments.class
        })
public class DescribeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "describe";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: describe scale <key> | describe chord <symbol> --key <key> | describe instruments --soundbank <id>",
                "Try describe scale D-dorian.");
    }

    @CommandLine.Command(name = "scale", description = "List the pitch classes of a scale.")
    public static class ScaleName implements Callable<Integer> {
        @CommandLine.ParentCommand DescribeCommand describe;
        @CommandLine.Parameters(index = "0..1", arity = "1..2", paramLabel = "<tonic mode>")
        List<String> name;

        @Override
        public Integer call() {
            AgentCli parent = describe.cli();
            parent.commandName = "describe scale";
            try {
                Key key = Key.parse(String.join(" ", name));
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("scale", key.name());
                fields.put("pcs", key.pcs());
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("scale", key.name());
                data.put("pcs", key.pcs());
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.scale/1",
                        data, List.of(), List.of());
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]", e.getMessage(),
                        "Use a tonic and a mode, such as \"D dorian\" or D-dorian.");
            }
        }
    }

    @CommandLine.Command(name = "chord", description = "Spell a chord and its roman numeral in a key.")
    public static class ChordName implements Callable<Integer> {
        @CommandLine.ParentCommand DescribeCommand describe;
        @CommandLine.Parameters(index = "0", paramLabel = "<symbol>")
        String symbol;
        @CommandLine.Option(names = "--key", required = true, paramLabel = "<tonic mode>")
        String keyName;

        @Override
        public Integer call() {
            AgentCli parent = describe.cli();
            parent.commandName = "describe chord";
            try {
                Key key = Key.parse(keyName);
                Chord chord = Chord.parse(symbol);
                String roman = chord.roman(key).format();
                String pcs = chord.pcs(key);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("chord", symbol.trim());
                fields.put("roman", roman);
                fields.put("pcs", pcs);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("chord", symbol.trim());
                data.put("roman", roman);
                data.put("pcs", pcs);
                data.put("key", key.name());
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.chord/1",
                        data, List.of(), List.of());
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]", e.getMessage(),
                        "Use a symbol such as Dm7 and a key such as \"D dorian\".");
            }
        }
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "instruments", description = "Count instruments and drum kits in a soundbank.")
    public static class Instruments implements Callable<Integer> {
        @CommandLine.ParentCommand DescribeCommand describe;
        @CommandLine.Option(names = "--soundbank", paramLabel = "<id|file>", required = true)
        String soundbank;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = describe.cli();
            parent.commandName = "describe instruments";
            SoundbankResolver.Selection selection;
            try {
                selection = SoundbankResolver.select(soundbank, SoundbankPaths.cache(cache));
            } catch (SoundbankRejected e) {
                return parent.fail(ExitCode.INVALID_INPUT, e.code(), e.path(), e.getMessage(), e.fix());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--soundbank", e.getMessage(),
                        "Check that the soundbank file is readable.");
            }
            if (selection.file() == null || selection.fallback()) {
                String fix = SoundbankCatalog.byId(soundbank) != null
                        ? "Run soundbank install " + soundbank + " first."
                        : "Pass an installed id or a path to an .sf2 file.";
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--soundbank",
                        "soundbank not found", fix);
            }
            try {
                InstrumentIndex.Counts counts = InstrumentIndex.count(selection.file());
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("instruments", Integer.toString(counts.instruments()));
                fields.put("drumkits", Integer.toString(counts.drumkits()));
                fields.put("soundbank", selection.name());
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("instruments", counts.instruments());
                data.put("drumkits", counts.drumkits());
                data.put("soundbank", selection.name());
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.instruments/1",
                        data, List.of(), List.of());
            } catch (InvalidMidiDataException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--soundbank",
                        "not a soundbank", "Pass an .sf2 or .dls file Gervill can read.");
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--soundbank", e.getMessage(),
                        "Check that the soundbank file is readable.");
            }
        }
    }
}
