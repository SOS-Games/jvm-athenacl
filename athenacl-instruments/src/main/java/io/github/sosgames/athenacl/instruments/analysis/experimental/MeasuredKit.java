// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis.experimental;

import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.analysis.InstrumentProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The measured kit commands. Loaded only when {@code analysis.enabled} is true, so a normal
 * command never opens the probe synth.
 */
public final class MeasuredKit {
    private MeasuredKit() {
    }

    public static String analyze(String file, String out, String instruments, String probeVersion)
            throws IOException, KitException {
        InstrumentProfile.Report report = InstrumentProfile.analyze(
                Path.of(file),
                blank(out) ? null : Path.of(out),
                names(instruments),
                blank(probeVersion) ? null : Integer.valueOf(probeVersion));
        StringBuilder text = new StringBuilder();
        for (InstrumentProfile.Row row : report.rows()) {
            text.append(InstrumentProfile.rowLine(row, report.cached())).append('\n');
        }
        text.append("analyzed=").append(report.analyzed())
                .append(" bad=").append(report.bad())
                .append(" cached=").append(report.cached())
                .append(" hash=").append(report.hash());
        return text.toString();
    }

    public static String describe(String file, String name, String out, String textFlag, String where)
            throws IOException, KitException {
        Path kit = Path.of(file);
        io.github.sosgames.athenacl.instruments.Kit loaded = Kits.load(kit);
        if (!blank(name) && loaded.instrument(name) == null) {
            List<String> names = new ArrayList<>();
            for (var instrument : loaded.instruments()) {
                names.add(instrument.name());
            }
            String near = nearest(name, names);
            String fix = near == null ? "Pass a name from the kit." : "Did you mean " + near + "?";
            throw new KitException(new KitException.Issue("NOT_FOUND", "argv[2]", "instrument not found", fix));
        }
        InstrumentProfile.Report report = InstrumentProfile.analyze(kit, blank(out) ? null : Path.of(out));
        boolean prose = "true".equals(textFlag);
        StringBuilder printed = new StringBuilder();
        for (InstrumentProfile.Row row : report.rows()) {
            if (!blank(name) && !row.name().equals(name)) {
                continue;
            }
            if (!blank(where) && !matches(row, where)) {
                continue;
            }
            if (!printed.isEmpty()) {
                printed.append('\n');
            }
            printed.append(prose ? InstrumentProfile.text(row) : InstrumentProfile.rowLine(row, null));
        }
        if (!blank(name) && printed.isEmpty()) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "argv[2]", "instrument not found", "Pass a name from the kit."));
        }
        return printed.toString();
    }

    public static String compat(String kitId, String left, String right, String registers)
            throws IOException, KitException {
        InstrumentProfile.Report profile = profile(kitId);
        require(profile, left);
        require(profile, right);
        String leftRange = "C4..C5";
        String rightRange = "C4..C5";
        if (!blank(registers)) {
            String[] parts = registers.split(",", 2);
            leftRange = parts[0];
            rightRange = parts.length > 1 ? parts[1] : parts[0];
        }
        Compatibility.Pair pair = Compatibility.pair(row(profile, left), row(profile, right), leftRange, rightRange);
        return "instruments=" + left + "," + right
                + " roughness=" + Compatibility.num(pair.roughness())
                + " mask=" + Compatibility.num(pair.mask())
                + " verdict=" + pair.verdict();
    }

    public static String fit(String kitId, String name, String role) throws IOException, KitException {
        InstrumentProfile.Report profile = profile(kitId);
        require(profile, name);
        double fitness = Compatibility.fitness(role, row(profile, name));
        return "role=" + role + " instrument=" + name + " fitness=" + Compatibility.num(fitness);
    }

    public static String lintTheme(String file, String state) throws IOException, KitException {
        Compatibility.Lint lint = Compatibility.lintTheme(Path.of(file), blank(state) ? null : state);
        return lint.errors() + "\n" + lint.text();
    }

    public static String hash(String file, String out) throws IOException, KitException {
        return InstrumentProfile.analyze(Path.of(file), blank(out) ? null : Path.of(out)).hash();
    }

    public static String probe(String kitId, String name, String pitch, String midi, String velocity)
            throws IOException, KitException {
        Descriptors.Measurement measurement = InstrumentProfile.probe(
                Kits.find(kitId), name, Integer.parseInt(midi), Integer.parseInt(velocity));
        return "instrument=" + name
                + " pitch=" + pitch
                + " velocity=" + velocity
                + " pitch.errorCents=" + String.format(Locale.US, "%.1f", measurement.pitchErrorCents())
                + " octave_offset=" + measurement.octaveOffset()
                + " inharmonic.energy=" + String.format(Locale.US, "%.2f", measurement.inharmonicEnergy())
                + " spectral.centroidRatio=" + String.format(Locale.US, "%.2f", measurement.centroidRatio())
                + " spectral.flatness=" + String.format(Locale.US, "%.2f", measurement.flatness())
                + " attack.ms=" + String.format(Locale.US, "%.1f", measurement.attackMs())
                + " decay.ms=" + String.format(Locale.US, "%.1f", measurement.decayMs())
                + " peak.dbfs=" + String.format(Locale.US, "%.1f", measurement.peakDbfs())
                + " clipping=" + measurement.clipCount()
                + " loopClicks=" + measurement.loopClicks()
                + " loudness.integrated=" + String.format(Locale.US, "%.1f", measurement.loudnessLu());
    }

    private static InstrumentProfile.Report profile(String kitId) throws IOException, KitException {
        Path file = Kits.find(kitId);
        if (!Files.isRegularFile(file)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "--kit", "kit not found", "Pass a kit such as gm-lite or analysis."));
        }
        return InstrumentProfile.analyze(file, null);
    }

    private static void require(InstrumentProfile.Report profile, String name) throws KitException {
        if (row(profile, name) == null) {
            List<String> names = new ArrayList<>();
            for (InstrumentProfile.Row row : profile.rows()) {
                names.add(row.name());
            }
            String near = nearest(name, names);
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "argv[2]", "instrument not found",
                    near == null ? "Pass a name from the kit." : "Did you mean " + near + "?"));
        }
    }

    private static InstrumentProfile.Row row(InstrumentProfile.Report profile, String name) {
        for (InstrumentProfile.Row row : profile.rows()) {
            if (row.name().equals(name)) {
                return row;
            }
        }
        return null;
    }

    private static boolean matches(InstrumentProfile.Row row, String where) {
        int eq = where.indexOf('=');
        if (eq < 0) {
            return false;
        }
        String key = where.substring(0, eq);
        String value = where.substring(eq + 1);
        return switch (key) {
            case "verdict" -> row.verdict().equals(value);
            case "tag", "tags" -> row.tags().contains(value);
            case "reason", "reasons" -> row.reasons().stream()
                    .anyMatch(reason -> reason.equals(value) || reason.startsWith(value + "@"));
            default -> false;
        };
    }

    private static List<String> names(String instruments) {
        if (blank(instruments)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String name : instruments.split(",")) {
            if (!name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    private static String nearest(String token, List<String> names) {
        String best = null;
        int bestDistance = 5;
        for (String name : names) {
            int distance = distance(token, name);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = name;
            }
        }
        return best;
    }

    private static int distance(String left, String right) {
        int[] prev = new int[right.length() + 1];
        int[] curr = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[left.length() == 0 ? 0 : right.length()];
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
