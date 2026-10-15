// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The numbers in {@code instruments/analysis.yaml}. They are starting points:
 * a fixture that should fail is the thing that moves a threshold, not a hand edit
 * of one instrument's verdict.
 */
public record Thresholds(
        int probeVersion,
        double pitchSoftCents,
        double pitchHardCents,
        double inharmonicCents,
        double inharmonicEnergy,
        double flatness,
        double clipDbfs,
        double loopClickSigma,
        int loopClickCount,
        double brightRatio,
        double warmRatio,
        double padAttackMs,
        double pluckAttackMs,
        double pluckDecayMs,
        double velocityMinLu) {
    public static Thresholds load(Path file) throws IOException {
        Thresholds defaults = defaults();
        if (file == null || !Files.isRegularFile(file)) {
            return defaults;
        }
        JsonNode root = new ObjectMapper(new YAMLFactory())
                .readTree(Files.readString(file, StandardCharsets.UTF_8));
        return new Thresholds(
                root.path("probeVersion").asInt(defaults.probeVersion),
                root.path("pitchSoftCents").asDouble(defaults.pitchSoftCents),
                root.path("pitchHardCents").asDouble(defaults.pitchHardCents),
                root.path("inharmonicCents").asDouble(defaults.inharmonicCents),
                root.path("inharmonicEnergy").asDouble(defaults.inharmonicEnergy),
                root.path("flatness").asDouble(defaults.flatness),
                root.path("clipDbfs").asDouble(defaults.clipDbfs),
                root.path("loopClickSigma").asDouble(defaults.loopClickSigma),
                root.path("loopClickCount").asInt(defaults.loopClickCount),
                root.path("brightRatio").asDouble(defaults.brightRatio),
                root.path("warmRatio").asDouble(defaults.warmRatio),
                root.path("padAttackMs").asDouble(defaults.padAttackMs),
                root.path("pluckAttackMs").asDouble(defaults.pluckAttackMs),
                root.path("pluckDecayMs").asDouble(defaults.pluckDecayMs),
                root.path("velocityMinLu").asDouble(defaults.velocityMinLu));
    }

    public static Thresholds defaults() {
        return new Thresholds(4, 15, 30, 50, 0.25, 0.35, -6.0, 8, 8,
                2.0, 1.4, 80, 40, 400, 3);
    }
}
