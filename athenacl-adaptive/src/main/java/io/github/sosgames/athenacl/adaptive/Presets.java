// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.theory.Key;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Built-in genre presets, loaded from {@code presets/genres}. A genre has no states.
 * A theme names one with {@code preset:} or {@code genre:} and may declare states of its own.
 * The director only sees the merged theme. World packs use a 12-tone scale that contains
 * the named notes. They are not a performance of that tradition.
 */
public final class Presets {
    private static final List<String> IDS = List.of(
            "fantasy", "scifi-space",
            "orchestral", "ambient", "folk", "jazz", "blues", "rock", "funk", "latin",
            "reggae", "house", "hiphop", "lofi", "chiptune", "metal",
            "hijaz", "raga", "hirajoshi", "yo", "pentatonic", "klezmer", "flamenco", "pelog", "slendro");

    private Presets() {
    }

    public record Preset(String id, String limits, LoudnessContour loudness, String instruments,
                         String character, String meter, Composition genre) {
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
     * Plays the genre for {@code bars} at {@code seed} and reports its key, tempo, and character.
     * {@code bpm} is the tempo that seed played. {@code notes} is how many notes that pass wrote.
     * No state is applied.
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
        Composition genre = preset.genre();
        Key key = Key.parse(genre.text("scale.tonic", "C") + " " + genre.text("scale.mode", "aeolian"));
        return new Audition(id, bars, seed, key.name(), performance.score().bpm(), preset.meter(),
                preset.character(), preset.instruments(), performance.notes().size());
    }

    /** Plays the genre for {@code bars}, with no state layered on. The same seed plays the same notes. */
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

    /** Genre defaults, then the library state {@code presets/states/<state>.yaml} when one is named. */
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
            LoudnessContour loudness = new LoudnessContour(genre.decimal("loudness.reference", -23), Map.of(),
                    genre.decimal("loudness.maxSpread", 6));
            return new Preset(id, genre.text("limits", ""), loudness, genre.text("palette", ""),
                    genre.text("character", ""), genre.text("meter", "4/4"), genre);
        } catch (IOException e) {
            throw new IllegalStateException(id, e);
        }
    }
}
