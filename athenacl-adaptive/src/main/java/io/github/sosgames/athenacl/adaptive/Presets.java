// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.theory.Key;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in genre presets. A theme names one with {@code preset: fantasy} and may override any state.
 * The director only sees the merged theme. World packs use a 12-tone scale that contains the
 * named notes. They are not a performance of that tradition.
 */
public final class Presets {
    private static final List<String> STATE_IDS = List.of(
            "peaceful", "sneaking", "combat", "danger", "trading", "partying");

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
        String yaml = "id: " + id + "\npreset: " + id + "\nbars: " + bars + "\nmeter: \"" + preset.meter() + "\"\n";
        Theme theme = Themes.parse(id + ".yaml", yaml);
        Director.Performance performance = Director.perform(theme, List.of(), seed);
        Theme.StateDef peaceful = preset.states().get("peaceful");
        return new Audition(id, bars, seed, peaceful.key().name(), peaceful.tempo(), preset.meter(),
                preset.character(), preset.instruments(), performance.notes().size());
    }

    private static List<Preset> all() {
        List<Preset> presets = new java.util.ArrayList<>();
        presets.add(fantasy());
        presets.add(scifi());
        presets.addAll(genres());
        presets.addAll(world());
        return List.copyOf(presets);
    }

    /**
     * Dungeon music: modal exploration, a low drone while sneaking, harp and flute in town,
     * taiko, horn, and trumpet in a fight. Each state keeps several of those sounding together.
     * Loudness follows the shared contour.
     */
    public static Preset fantasy() {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        states.put("peaceful", state("peaceful", "A aeolian", 100, List.of("harp", "pad", "bass", "lead"),
                Map.of("pad", 48, "harp", 46, "bass", 43, "lead", 73),
                Map.of("harp", "harp", "pad", "strings", "bass", "cello", "lead", "flute")));
        states.put("sneaking", state("sneaking", "D dorian", 100, List.of("drone", "pad", "bass", "harp"),
                Map.of("drone", 49, "pad", 52, "bass", 43, "harp", 46),
                Map.of("drone", "strings.slow", "pad", "choir", "bass", "cello", "harp", "harp")));
        states.put("combat", state("combat", "D aeolian", 100,
                List.of("pad", "brass", "bass", "lead", "taiko", "drums"),
                Map.of("pad", 48, "brass", 60, "bass", 43, "lead", 56, "taiko", 116),
                Map.of("pad", "strings", "brass", "horn", "bass", "cello", "lead", "trumpet",
                        "taiko", "taiko", "drums", "drums.orchestral")));
        states.put("danger", state("danger", "E phrygian", 100, List.of("drone", "pad", "bass", "brass", "taiko"),
                Map.of("drone", 49, "pad", 52, "bass", 43, "brass", 60, "taiko", 116),
                Map.of("drone", "strings.slow", "pad", "choir", "bass", "cello", "brass", "horn", "taiko", "taiko")));
        states.put("trading", state("trading", "D dorian", 100, List.of("harp", "pad", "bass", "lead"),
                Map.of("harp", 46, "pad", 52, "bass", 43, "lead", 73),
                Map.of("harp", "harp", "pad", "choir", "bass", "cello", "lead", "flute")));
        states.put("partying", state("partying", "D dorian", 100, List.of("harp", "pad", "bass", "lead", "drums"),
                Map.of("harp", 46, "pad", 48, "bass", 43, "lead", 110),
                Map.of("harp", "harp", "pad", "strings", "bass", "cello", "lead", "fiddle",
                        "drums", "drums.orchestral")));
        Map<String, Double> offsets = new LinkedHashMap<>();
        offsets.put("sneaking", -1.5);
        offsets.put("peaceful", -1.0);
        offsets.put("trading", -1.0);
        offsets.put("partying", 1.5);
        offsets.put("danger", 2.0);
        offsets.put("combat", 4.0);
        return new Preset("fantasy",
                "GM choir, harp, horn, flute, fiddle, and taiko. No sung lyrics.",
                states,
                new LoudnessContour(-23, offsets, 6),
                "harp,choir,strings,horn,flute,fiddle,taiko",
                "modal,taiko",
                "4/4");
    }

    /**
     * Space music: Lydian pads, an arpeggio on the eighth-note grid, and a synth bass.
     * Combat doubles the arp into sixteenths and adds an 808 kit. Trading uses celesta.
     * There is no whole-tone scale in the theory layer, so the raised fourth stands in for it.
     * Loudness uses the same contour as the dungeon.
     */
    public static Preset scifi() {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        states.put("peaceful", state("peaceful", "C lydian", 100, List.of("pad", "arp", "bass"),
                Map.of("pad", 89, "arp", 80, "bass", 38),
                Map.of("pad", "pad.warm", "arp", "lead.square", "bass", "bass.synth")));
        states.put("sneaking", state("sneaking", "A lydian", 100, List.of("pad", "arp", "bass"),
                Map.of("pad", 95, "arp", 80, "bass", 38),
                Map.of("pad", "pad.sweep", "arp", "lead.square", "bass", "bass.synth")));
        states.put("combat", state("combat", "F lydian", 100, List.of("pad", "arp", "bass", "lead", "drums"),
                Map.of("pad", 95, "arp", 80, "bass", 38, "lead", 81, "drums", 25),
                Map.of("pad", "pad.sweep", "arp", "lead.square", "bass", "bass.synth",
                        "lead", "lead.saw", "drums", "drums.808")));
        states.put("danger", state("danger", "E lydian", 100, List.of("pad", "arp", "bass"),
                Map.of("pad", 99, "arp", 80, "bass", 38),
                Map.of("pad", "fx.atmosphere", "arp", "lead.square", "bass", "bass.synth")));
        states.put("trading", state("trading", "G lydian", 100, List.of("bell", "pad", "arp", "bass"),
                Map.of("bell", 8, "pad", 88, "arp", 80, "bass", 38),
                Map.of("bell", "celesta", "pad", "pad.newage", "arp", "lead.square", "bass", "bass.synth")));
        states.put("partying", state("partying", "D lydian", 100, List.of("pad", "arp", "bass", "lead", "drums"),
                Map.of("pad", 89, "arp", 80, "bass", 38, "lead", 81, "drums", 25),
                Map.of("pad", "pad.warm", "arp", "lead.square", "bass", "bass.synth",
                        "lead", "lead.saw", "drums", "drums.808")));
        Map<String, Double> offsets = new LinkedHashMap<>();
        offsets.put("sneaking", -1.5);
        offsets.put("peaceful", -1.0);
        offsets.put("trading", -1.0);
        offsets.put("partying", 1.5);
        offsets.put("danger", 2.0);
        offsets.put("combat", 4.0);
        return new Preset("scifi-space",
                "GM synth pads, square and saw leads, synth bass, celesta, and an 808 kit. "
                        + "No filter sweeps until the effects stage. No sung lyrics.",
                states,
                new LoudnessContour(-23, offsets, 6),
                "pad,square,saw,bass,celesta,808",
                "lydian,arp",
                "4/4");
    }

    private static List<Preset> genres() {
        return List.of(
                pack("orchestral", "D aeolian", 72, "4/4", "cinematic,strings",
                        "strings,horn,timpani,flute,harp,cello",
                        "GM strings, horn, timpani, flute, harp, and cello. No sung lyrics.",
                        List.of("pad", "brass", "taiko", "lead", "harp", "bass"),
                        Map.of("pad", 48, "brass", 60, "taiko", 47, "lead", 73, "harp", 46, "bass", 43),
                        Map.of("pad", "strings", "brass", "horn", "taiko", "timpani", "lead", "flute",
                                "harp", "harp", "bass", "cello")),
                pack("ambient", "C lydian", 60, "4/4", "sparse,pad",
                        "pad,celesta,choir,harp,flute,cello",
                        "Slow pads, choir, harp, and flute. No filter sweeps until the effects stage.",
                        List.of("pad", "bell", "drone", "chords", "lead", "bass"),
                        Map.of("pad", 89, "bell", 8, "drone", 52, "chords", 46, "lead", 73, "bass", 43),
                        Map.of("pad", "pad.warm", "bell", "celesta", "drone", "choir", "chords", "harp",
                                "lead", "flute", "bass", "cello")),
                pack("folk", "D dorian", 108, "6/8", "jig",
                        "flute,fiddle,harp,accordion,bass,drums",
                        "A jig on flute, fiddle, harp, and accordion. No sung lyrics.",
                        List.of("lead", "pad", "harp", "chords", "bass", "drums"),
                        Map.of("lead", 73, "pad", 110, "harp", 46, "chords", 21, "bass", 32, "drums", 0),
                        Map.of("lead", "flute", "pad", "fiddle", "harp", "harp", "chords", "accordion",
                                "bass", "bass.acoustic", "drums", "drums.standard")),
                pack("jazz", "F dorian", 120, "4/4", "swing",
                        "piano,bass,ride,trumpet,vibes,trombone",
                        "Piano, bass, trumpet, vibes, trombone, and a kit. The swing is the character, not a copied tune.",
                        List.of("chords", "bass", "drums", "lead", "bell", "brass"),
                        Map.of("chords", 0, "bass", 32, "drums", 0, "lead", 56, "bell", 11, "brass", 57),
                        Map.of("chords", "piano", "bass", "bass.acoustic", "drums", "drums.standard",
                                "lead", "trumpet", "bell", "vibes", "brass", "trombone")),
                pack("blues", "A mixolydian", 80, "12/8", "shuffle",
                        "guitar,organ,bass,rhodes,drums",
                        "A 12/8 shuffle on guitar, organ, and Rhodes. GM guitar is not a real amp.",
                        List.of("lead", "chords", "bass", "pad", "harp", "drums"),
                        Map.of("lead", 29, "chords", 16, "bass", 33, "pad", 4, "harp", 27, "drums", 0),
                        Map.of("lead", "guitar.overdrive", "chords", "organ", "bass", "bass.finger",
                                "pad", "rhodes", "harp", "guitar.clean", "drums", "drums.standard")),
                pack("rock", "E mixolydian", 128, "4/4", "backbeat",
                        "guitar,bass,drums,organ,piano",
                        "Backbeat, two guitars, and organ. GM guitar is not a real amp.",
                        List.of("lead", "bass", "drums", "chords", "harp", "pad"),
                        Map.of("lead", 29, "bass", 33, "drums", 0, "chords", 16, "harp", 27, "pad", 0),
                        Map.of("lead", "guitar.overdrive", "bass", "bass.finger", "drums", "drums.standard",
                                "chords", "organ", "harp", "guitar.clean", "pad", "piano")),
                pack("funk", "E dorian", 100, "4/4", "clav",
                        "clav,bass,guitar,rhodes,drums",
                        "Clav, guitar, Rhodes, and a fingered bass on a straight grid.",
                        List.of("chords", "bass", "lead", "pad", "arp", "drums"),
                        Map.of("chords", 7, "bass", 33, "lead", 27, "pad", 4, "arp", 80, "drums", 0),
                        Map.of("chords", "clav", "bass", "bass.finger", "lead", "guitar.clean",
                                "pad", "rhodes", "arp", "lead.square", "drums", "drums.standard")),
                pack("latin", "C dorian", 120, "4/4", "clave",
                        "nylon,piano,drums,trumpet,bass,strings",
                        "Nylon guitar, piano, trumpet, and bass. The clave is the character.",
                        List.of("harp", "chords", "drums", "lead", "bass", "pad"),
                        Map.of("harp", 24, "chords", 0, "drums", 0, "lead", 56, "bass", 32, "pad", 48),
                        Map.of("harp", "nylon", "chords", "piano", "drums", "drums.standard",
                                "lead", "trumpet", "bass", "bass.acoustic", "pad", "strings")),
                pack("reggae", "A dorian", 76, "4/4", "skank",
                        "organ,bass,guitar,rhodes,drums",
                        "Offbeat organ, guitar, and a slow bass. No sung lyrics.",
                        List.of("chords", "bass", "lead", "pad", "harp", "drums"),
                        Map.of("chords", 16, "bass", 33, "lead", 27, "pad", 4, "harp", 24, "drums", 0),
                        Map.of("chords", "organ", "bass", "bass.finger", "lead", "guitar.clean",
                                "pad", "rhodes", "harp", "nylon", "drums", "drums.standard")),
                pack("house", "A aeolian", 124, "4/4", "kick-four",
                        "pad,bass,808,square,saw,celesta",
                        "A four-on-the-floor grid with two leads. No sidechain until the effects stage.",
                        List.of("pad", "bass", "drums", "arp", "lead", "bell"),
                        Map.of("pad", 88, "bass", 38, "drums", 25, "arp", 80, "lead", 81, "bell", 8),
                        Map.of("pad", "pad.newage", "bass", "bass.synth", "drums", "drums.808",
                                "arp", "lead.square", "lead", "lead.saw", "bell", "celesta")),
                pack("hiphop", "D aeolian", 84, "4/4", "trap",
                        "808,pad,drums,saw,rhodes",
                        "Instrumental only. GM has no rap vocals.",
                        List.of("bass", "pad", "drums", "lead", "chords", "arp"),
                        Map.of("bass", 38, "pad", 88, "drums", 25, "lead", 81, "chords", 4, "arp", 80),
                        Map.of("bass", "bass.synth", "pad", "pad.newage", "drums", "drums.808",
                                "lead", "lead.saw", "chords", "rhodes", "arp", "lead.square")),
                pack("lofi", "F dorian", 72, "4/4", "rhodes",
                        "rhodes,bass,drums,piano,guitar,vibes",
                        "Slow Rhodes, piano, guitar, and soft drums. No vinyl crackle until sampled layers.",
                        List.of("pad", "bass", "drums", "chords", "harp", "drone"),
                        Map.of("pad", 4, "bass", 33, "drums", 0, "chords", 0, "harp", 27, "drone", 11),
                        Map.of("pad", "rhodes", "bass", "bass.finger", "drums", "drums.standard",
                                "chords", "piano", "harp", "guitar.clean", "drone", "vibes")),
                pack("chiptune", "C ionian", 140, "4/4", "square",
                        "square,saw,bass,808,pad,celesta",
                        "Square and saw leads. No bitcrush until the effects stage.",
                        List.of("arp", "bass", "lead", "drums", "pad", "bell"),
                        Map.of("arp", 80, "bass", 38, "lead", 81, "drums", 25, "pad", 88, "bell", 8),
                        Map.of("arp", "lead.square", "bass", "bass.synth", "lead", "lead.saw",
                                "drums", "drums.808", "pad", "pad.newage", "bell", "celesta")),
                pack("metal", "E phrygian", 160, "4/4", "double-kick",
                        "guitar,bass,drums,organ,strings",
                        "Two distortion guitars and a short bass. GM guitar is not a real amp.",
                        List.of("lead", "bass", "drums", "harp", "chords", "pad"),
                        Map.of("lead", 30, "bass", 33, "drums", 0, "harp", 29, "chords", 16, "pad", 48),
                        Map.of("lead", "guitar.distortion", "bass", "bass.finger", "drums", "drums.standard",
                                "harp", "guitar.overdrive", "chords", "organ", "pad", "strings")));
    }

    private static List<Preset> world() {
        return List.of(
                pack("hijaz", "E phrygian-dominant", 90, "4/4", "maqam",
                        "oboe,strings,cello,nylon,choir",
                        "Phrygian dominant stands in for Hijaz. A 12-tone sketch, not a maqam performance.",
                        List.of("lead", "pad", "bass", "harp", "drone"),
                        Map.of("lead", 68, "pad", 48, "bass", 43, "harp", 24, "drone", 52),
                        Map.of("lead", "oboe", "pad", "strings", "bass", "cello", "harp", "nylon", "drone", "choir")),
                pack("raga", "C phrygian-dominant", 80, "4/4", "sitar",
                        "sitar,strings,tambura,bansuri,bass",
                        "A scale only. This is not raga grammar, and the sitar is the GM program.",
                        List.of("lead", "drone", "pad", "harp", "bass"),
                        Map.of("lead", 104, "drone", 49, "pad", 48, "harp", 75, "bass", 43),
                        Map.of("lead", "sitar", "drone", "strings.slow", "pad", "strings", "harp", "panflute",
                                "bass", "cello")),
                pack("hirajoshi", "C aeolian", 66, "4/4", "koto",
                        "koto,strings,flute,bass,celesta",
                        "The five hirajoshi notes sit inside this aeolian scale. Not a koto school piece.",
                        List.of("harp", "pad", "lead", "bass", "chords"),
                        Map.of("harp", 107, "pad", 48, "lead", 73, "bass", 43, "chords", 8),
                        Map.of("harp", "koto", "pad", "strings", "lead", "flute", "bass", "cello", "chords", "celesta")),
                pack("yo", "C ionian", 72, "4/4", "shamisen",
                        "shamisen,flute,koto,bass,drums",
                        "Yo's five notes sit inside ionian. The shamisen is the GM program.",
                        List.of("harp", "lead", "chords", "bass", "drums"),
                        Map.of("harp", 106, "lead", 73, "chords", 107, "bass", 32, "drums", 0),
                        Map.of("harp", "shamisen", "lead", "flute", "chords", "koto", "bass", "bass.acoustic",
                                "drums", "drums.standard")),
                pack("pentatonic", "C ionian", 96, "4/4", "five-note",
                        "flute,harp,strings,bass,marimba,drums",
                        "Major pentatonic sits inside ionian. These instruments play the seven-note parent.",
                        List.of("lead", "harp", "pad", "bass", "chords", "drums"),
                        Map.of("lead", 73, "harp", 46, "pad", 48, "bass", 32, "chords", 12, "drums", 0),
                        Map.of("lead", "flute", "harp", "harp", "pad", "strings", "bass", "bass.acoustic",
                                "chords", "marimba", "drums", "drums.standard")),
                pack("klezmer", "D phrygian-dominant", 120, "4/4", "freygish",
                        "clarinet,accordion,violin,bass,drums",
                        "Phrygian dominant stands in for freygish. Not a copied dance tune.",
                        List.of("lead", "chords", "pad", "bass", "drums"),
                        Map.of("lead", 71, "chords", 21, "pad", 40, "bass", 32, "drums", 0),
                        Map.of("lead", "clarinet", "chords", "accordion", "pad", "violin",
                                "bass", "bass.acoustic", "drums", "drums.standard")),
                pack("flamenco", "E phrygian-dominant", 100, "4/4", "palmas",
                        "guitar,bass,strings,drums",
                        "Phrygian dominant, nylon and steel guitar. Not a copied palo.",
                        List.of("harp", "lead", "chords", "bass", "pad", "drums"),
                        Map.of("harp", 24, "lead", 27, "chords", 29, "bass", 32, "pad", 48, "drums", 0),
                        Map.of("harp", "nylon", "lead", "guitar.clean", "chords", "guitar.overdrive",
                                "bass", "bass.acoustic", "pad", "strings", "drums", "drums.standard")),
                pack("pelog", "C phrygian", 70, "4/4", "bell-row",
                        "kalimba,strings,flute,marimba,bass",
                        "A 12-tone sketch of pelog. Not gamelan tuning.",
                        List.of("bell", "pad", "lead", "chords", "bass"),
                        Map.of("bell", 108, "pad", 48, "lead", 73, "chords", 12, "bass", 43),
                        Map.of("bell", "kalimba", "pad", "strings", "lead", "flute", "chords", "marimba",
                                "bass", "cello")),
                pack("slendro", "C mixolydian", 76, "4/4", "even-row",
                        "kalimba,koto,strings,flute,bass,drums",
                        "A roughly even five-note set sits inside mixolydian. Not slendro tuning.",
                        List.of("bell", "chords", "pad", "lead", "bass", "drums"),
                        Map.of("bell", 108, "chords", 107, "pad", 48, "lead", 73, "bass", 43, "drums", 0),
                        Map.of("bell", "kalimba", "chords", "koto", "pad", "strings", "lead", "flute",
                                "bass", "cello", "drums", "drums.standard")));
    }

    private static Theme.StateDef state(String id, String key, int tempo, List<String> layers,
                                        Map<String, Integer> programs, Map<String, String> instruments) {
        return new Theme.StateDef(id, Key.parse(key), tempo, layers, null, programs, instruments);
    }

    private static Preset pack(String id, String key, int bpm, String meter, String character, String instruments,
                               String limits, List<String> layers, Map<String, Integer> programs,
                               Map<String, String> kits) {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        for (String stateId : STATE_IDS) {
            states.put(stateId, new Theme.StateDef(stateId, Key.parse(key), bpm, layers, null,
                    programs, kits, meter));
        }
        return new Preset(id, limits, states, contour(), instruments, character, meter);
    }

    private static LoudnessContour contour() {
        Map<String, Double> offsets = new LinkedHashMap<>();
        offsets.put("sneaking", -1.5);
        offsets.put("peaceful", -1.0);
        offsets.put("trading", -1.0);
        offsets.put("partying", 1.5);
        offsets.put("danger", 2.0);
        offsets.put("combat", 4.0);
        return new LoudnessContour(-23, offsets, 6);
    }
}
