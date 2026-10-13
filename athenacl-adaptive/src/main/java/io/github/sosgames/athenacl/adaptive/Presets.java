// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in genre presets, loaded from {@code presets/genres} and {@code presets/states}.
 * A theme names one with {@code preset:} or {@code genre:} and may override any state.
 * The director only sees the merged theme. World packs use a 12-tone scale that contains
 * the named notes. They are not a performance of that tradition.
 */
public final class Presets {
    private static final List<String> STATE_IDS = List.of(
            "peaceful", "sneaking", "combat", "danger", "trading", "partying");
    private static final List<String> IDS = List.of(
            "fantasy", "scifi-space",
            "orchestral", "ambient", "folk", "jazz", "blues", "rock", "funk", "latin",
            "reggae", "house", "hiphop", "lofi", "chiptune", "metal",
            "hijaz", "raga", "hirajoshi", "yo", "pentatonic", "klezmer", "flamenco", "pelog", "slendro");

    private Presets() {
    }

    public record Preset(String id, String limits, Map<String, Theme.StateDef> states, LoudnessContour loudness,
                         String instruments, String character, String meter) {
    }

    public record Audition(String id, int bars, long seed, String key, int bpm, String meter, String character,
                           String instruments, int notes) {
    }

    public static Preset get(String id) {
        for (Preset preset : all()) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    public static List<String> ids() {
        return all().stream().map(Preset::id).toList();
    }

    public static Preset fantasy() {
        return get("fantasy");
    }

    public static Preset scifi() {
        return get("scifi-space");
    }

    /**
     * Plays the preset for {@code bars} at {@code seed} and reports the peaceful state's
     * key, tempo, and character. {@code notes} is how many notes that pass wrote.
     */
    public static Audition audition(String id, int bars, long seed) throws ThemeException {
        Preset preset = get(id);
        if (preset == null) {
            throw new IllegalArgumentException("unknown preset " + id);
        }
        if (bars < 1) {
            throw new IllegalArgumentException("bars must be 1 or more");
        }
        Director.Performance performance = play(id, bars, seed);
        Theme.StateDef peaceful = preset.states().get("peaceful");
        return new Audition(id, bars, seed, peaceful.key().name(), peaceful.tempo(), preset.meter(),
                preset.character(), preset.instruments(), performance.notes().size());
    }

    /** Plays the preset's first state for {@code bars}. The same seed plays the same notes. */
    public static Director.Performance play(String id, int bars, long seed) throws ThemeException {
        Preset preset = get(id);
        if (preset == null) {
            throw new IllegalArgumentException("unknown preset " + id);
        }
        if (bars < 1) {
            throw new IllegalArgumentException("bars must be 1 or more");
        }
        String yaml = "id: " + id + "\npreset: " + id + "\nbars: " + bars + "\nmeter: \"" + preset.meter() + "\"\n";
        return Director.perform(Themes.parse(id + ".yaml", yaml), List.of(), seed);
    }

    /** Genre defaults, then the generic state, then {@code presets/states/<genre>/<state>.yaml}. */
    public static Composition resolve(String genreId, String stateId) throws IOException {
        return Resolver.resolve(genreId, stateId, "", null);
    }

    private static List<Preset> all() {
        List<Preset> presets = new java.util.ArrayList<>();
        for (String id : IDS) {
            presets.add(load(id));
        }
        return List.copyOf(presets);
    }

    private static Preset load(String id) {
        try {
            Composition genre = Resolver.genre(id);
            if (genre == null) {
                throw new IllegalStateException("missing preset " + id);
            }
            Map<String, Theme.StateDef> states = new LinkedHashMap<>();
            for (String stateId : STATE_IDS) {
                Composition resolved = Resolver.resolve(id, stateId, "", null);
                states.put(stateId, Resolver.state(stateId, resolved, genre.text("meter", "4/4")));
            }
            return new Preset(id, genre.text("limits", ""), states, loudness(genre),
                    genre.text("palette", ""), genre.text("character", ""), genre.text("meter", "4/4"));
        } catch (IOException e) {
            throw new IllegalStateException(id, e);
        }
    }

    private static LoudnessContour loudness(Composition genre) {
        Map<String, Double> offsets = new LinkedHashMap<>();
        for (String stateId : STATE_IDS) {
            offsets.put(stateId, genre.decimal("loudness.offsets." + stateId, 0));
        }
        return new LoudnessContour(genre.decimal("loudness.reference", -23), offsets,
                genre.decimal("loudness.maxSpread", 6));
    }
}
