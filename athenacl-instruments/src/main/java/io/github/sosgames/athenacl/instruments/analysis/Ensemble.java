// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.Resolver;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.descriptors.Selector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picks one instrument per role from declared descriptors. The same seed picks the same
 * names, and a missing kit file keeps the instruments written in the theme. Measured profiles
 * are not read.
 */
public final class Ensemble {
    private Ensemble() {
    }

    public record Role(String role, String instrument, double score, String verdict, String from) {
    }

    public record Result(List<Role> roles, boolean fallback, String seedPath, String detail) {
        public Result {
            roles = List.copyOf(roles);
            detail = detail == null ? "" : detail;
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            if (fallback) {
                sb.append("ensemble.fallback=fixed\n");
            }
            for (Role role : roles) {
                sb.append("role=").append(role.role);
                sb.append(" instrument=").append(role.instrument);
                sb.append(" score=").append(num(role.score));
                sb.append(" from=").append(role.from);
                if (role.verdict != null && !role.verdict.isBlank()) {
                    sb.append(" class=").append(role.verdict);
                }
                sb.append(" seed=").append(seedPath).append('/').append(role.role);
                sb.append('\n');
            }
            if (sb.isEmpty()) {
                sb.append("seedPath=").append(seedPath);
            } else {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        }

        public String explain() {
            if (detail.isBlank()) {
                return text();
            }
            return text() + "\n" + detail;
        }

        /** The instrument this layer plays. A role the search did not fill keeps the YAML name. */
        public String playing(String layer, String yaml) {
            if (fallback) {
                return yaml;
            }
            for (Role role : roles) {
                if (role.role.equals(layer)) {
                    return role.instrument;
                }
            }
            return yaml;
        }

        public String instrumentLines() {
            StringBuilder sb = new StringBuilder();
            for (Role role : roles) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("instruments.").append(role.role).append('=').append(role.instrument);
                sb.append(" source=ensemble:").append(fallback ? "fixed" : role.from);
            }
            return sb.toString();
        }
    }

    public static Result select(String genre, String state, Path theme, String kitOverride, long seed)
            throws IOException, KitException {
        Composition composition = theme == null
                ? Resolver.resolve(genre, state, "", null)
                : Resolver.resolveTheme(genre == null ? "" : genre, state, theme);
        if (composition == null) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "--genre", "unknown genre " + genre,
                    "Use a genre such as fantasy."));
        }
        if (!"auto".equals(composition.text("ensemble.select", "fixed"))) {
            return fallback(composition, state, seed);
        }
        String kitId = kitOverride == null || kitOverride.isBlank() ? firstKit(composition) : kitOverride;
        Path file = kitId == null || kitId.isBlank() ? null : Kits.find(kitId);
        if (file == null || !Files.isRegularFile(file)) {
            return fallback(composition, state, seed);
        }
        String genreId = genre == null || genre.isBlank() ? composition.text("genre", composition.text("preset", "")) : genre;
        Selector.Outcome outcome = Selector.choose(composition, genreId, file, seed);
        return from(outcome, seed);
    }

    /**
     * The ensemble a render should play. A genre that does not ask for auto returns null so the
     * YAML names stay. A missing kit does the same. This does not open a synth.
     */
    public static Result forPlayback(Composition composition, String state, long seed) {
        if (composition == null || !"auto".equals(composition.text("ensemble.select", "fixed"))) {
            return null;
        }
        try {
            String kitId = firstKit(composition);
            Path file = kitId.isBlank() ? null : Kits.find(kitId);
            if (file == null || !Files.isRegularFile(file)) {
                return null;
            }
            String genre = composition.text("genre", composition.text("preset", ""));
            Selector.Outcome outcome = Selector.choose(composition, genre, file, seed);
            Result result = from(outcome, seed);
            return result.roles.isEmpty() ? null : result;
        } catch (IOException | KitException e) {
            return null;
        }
    }

    public static Result fallback(Composition composition, String state, long seed) {
        List<Role> roles = new ArrayList<>();
        for (String role : Selector.roles(composition)) {
            String pin = composition.text("ensemble.roles." + role + ".pin", "");
            String name = pin.isBlank() ? composition.text("instruments." + role, "") : pin;
            if (!name.isBlank()) {
                roles.add(new Role(role, name, 0, "", pin.isBlank() ? "fixed" : "pin"));
            }
        }
        return new Result(roles, true, seed + "/ensemble/" + state, "");
    }

    private static Result from(Selector.Outcome outcome, long seed) {
        List<Role> roles = new ArrayList<>();
        for (Selector.Pick pick : outcome.picks()) {
            roles.add(new Role(pick.role(), pick.instrument(), pick.score(), pick.soundClass(), pick.from()));
        }
        return new Result(roles, outcome.fallback(), seed + "/ensemble", outcome.detail());
    }

    private static String firstKit(Composition composition) {
        String kits = composition.text("ensemble.kits", "");
        if (kits.isBlank()) {
            kits = composition.text("instruments.kits", "");
        }
        if (kits.isBlank()) {
            return "";
        }
        return kits.split(",")[0].trim();
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
