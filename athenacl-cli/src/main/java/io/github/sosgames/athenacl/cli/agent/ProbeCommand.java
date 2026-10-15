// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.analysis.Descriptors;
import io.github.sosgames.athenacl.instruments.analysis.InstrumentProfile;
import picocli.CommandLine;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Renders one dry note and prints the measurements for that take.
 * {@code kit analyze} is the profile; this is the single probe underneath it.
 */
@CommandLine.Command(name = "probe", description = "Measure one dry note from a kit instrument.")
public class ProbeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<instrument>")
    String name;
    @CommandLine.Option(names = "--kit", paramLabel = "<id>", defaultValue = "gm-lite")
    String kitId;
    @CommandLine.Option(names = "--pitch", paramLabel = "<name>", defaultValue = "C4")
    String pitch;
    @CommandLine.Option(names = "--velocity", paramLabel = "<n>", defaultValue = "80")
    int velocity;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug probe";
        if (name == null || name.isBlank()) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug probe <instrument> --kit <id>",
                    "Try debug probe flute --kit gm-lite --pitch C4.");
        }
        int midi;
        try {
            midi = PitchName.midi(pitch);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--pitch",
                    e.getMessage(), "Use a name such as C4.");
        }
        if (velocity < 1 || velocity > 127) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--velocity",
                    "velocity must be from 1 to 127", "Try --velocity 80.");
        }
        try {
            Descriptors.Measurement measurement = InstrumentProfile.probe(
                    Kits.find(kitId), name, midi, velocity);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("instrument", name);
            fields.put("pitch", pitch);
            fields.put("velocity", Integer.toString(velocity));
            fields.put("pitch.errorCents", String.format(Locale.US, "%.1f", measurement.pitchErrorCents()));
            fields.put("inharmonic.energy", String.format(Locale.US, "%.2f", measurement.inharmonicEnergy()));
            fields.put("spectral.centroidRatio", String.format(Locale.US, "%.2f", measurement.centroidRatio()));
            fields.put("spectral.flatness", String.format(Locale.US, "%.2f", measurement.flatness()));
            fields.put("attack.ms", String.format(Locale.US, "%.1f", measurement.attackMs()));
            fields.put("decay.ms", String.format(Locale.US, "%.1f", measurement.decayMs()));
            fields.put("peak.dbfs", String.format(Locale.US, "%.1f", measurement.peakDbfs()));
            fields.put("clipping", Integer.toString(measurement.clipCount()));
            fields.put("loopClicks", Integer.toString(measurement.loopClicks()));
            fields.put("loudness.integrated", String.format(Locale.US, "%.1f", measurement.loudnessLu()));
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.kit/1",
                    new LinkedHashMap<>(fields), List.of(), List.of());
        } catch (KitException e) {
            KitException.Issue issue = e.issues().get(0);
            ExitCode code = "NOT_FOUND".equals(issue.code()) || "KIT_NOT_FOUND".equals(issue.code())
                    ? ExitCode.NOT_FOUND : ExitCode.INVALID_INPUT;
            return parent.fail(code, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit",
                    e.getMessage() == null ? "probe failed" : e.getMessage(),
                    "Check that the soundfont can be read.");
        }
    }
}
