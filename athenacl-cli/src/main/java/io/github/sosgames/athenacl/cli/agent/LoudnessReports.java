// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.render.loudness.LoudnessMeter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared text for {@code analyze} and {@code debug check --loudness}. */
final class LoudnessReports {
    private LoudnessReports() {
    }

    static int finish(AgentCli parent, Path wav, String schema) {
        return finish(parent, wav, schema, null);
    }

    static int finish(AgentCli parent, Path wav, String schema, String extra) {
        try {
            LoudnessMeter.Reading reading = LoudnessMeter.measure(wav);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("integrated", reading.integrated());
            data.put("shortTerm", reading.shortTerm());
            data.put("truePeak", reading.truePeak());
            data.put("wav", wav.getFileName().toString());
            String line = extra == null || extra.isBlank() ? reading.line() : reading.line() + " " + extra;
            return parent.finish(ExitCode.OK, line, schema, data, List.of(), List.of());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the WAV is readable PCM.");
        }
    }
}
