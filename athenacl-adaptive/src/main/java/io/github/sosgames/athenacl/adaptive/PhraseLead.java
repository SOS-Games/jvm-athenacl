// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.motif.Motif;
import io.github.sosgames.athenacl.motif.PhrasePlan;
import io.github.sosgames.athenacl.theory.Key;
import io.github.sosgames.athenacl.theory.Progression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One bar of a monophonic lead phrase. A phrase is four bars, and a period is two of those.
 * The motif is the opening idea. Later bars turn that idea over. Strong beats sit on the
 * chord of this bar. Weak beats step. One leap in the phrase steps back. A cadence note is
 * long. {@code motif.every} counts phrases between statements of the unturned idea.
 * Rhythm cells and the form plan are read from {@code presets/rhythms.yaml}.
 * When the state names {@code source.lead.po}, those draws replace the named cells.
 */
public final class PhraseLead {
    private static final Library LIBRARY = Library.load();

    private PhraseLead() {
    }

    public record Tone(long offset, long ticks, int midi, String why, String motif) {
    }

    public static List<Tone> bar(Theme.StateDef state, Progression.Chord chord, int held, int every,
                                  long barTicks, long seed, Motif motif) {
        if (state.composition() != null && !state.composition().flag("lead.enabled", true)) {
            return List.of();
        }
        if (motif == null || motif.notes().isEmpty() || barTicks <= 0) {
            return List.of();
        }
        String formName = state.composition() == null ? "period" : state.composition().text("lead.phrase", "period");
        Library.Form form = LIBRARY.form(formName);
        List<String> cells = cells(state);
        int slot = Math.floorMod(held, form.bars);
        int period = held / form.bars;
        boolean hook = every <= 1 || Math.floorMod(period, every) == 0;
        String token = form.plan.get(Math.floorMod(slot, form.plan.size()));
        String chain = chain(token, hook, form.develop, period);
        Motif shaped = "idea".equals(chain) || "repeat".equals(chain)
                ? motif
                : PhrasePlan.develop(motif, motif.id() + ":" + chain);
        if (shaped.notes().isEmpty()) {
            shaped = motif;
        }
        boolean half = "half".equals(token) || (chord.cadence() && chord.numeral().startsWith("V"));
        boolean authentic = "authentic".equals(token)
                || (chord.cadence() && ("i".equals(chord.numeral()) || "I".equals(chord.numeral())));
        int cellIndex = "idea".equals(token) ? 0 : slot;
        List<String> sourced = LayerPo.rhythmTokens(state, seed, cellIndex);
        List<Slot> rhythm = sourced.isEmpty()
                ? fit(cells.get(Math.floorMod(cellIndex, cells.size())), barTicks)
                : fit(sourced, barTicks);
        int[] degrees = place(state.key(), chord, shaped, rhythm, seed, state.id(), held, form.phrase, half,
                authentic, density(state));
        int low = register(state, 0);
        int high = register(state, 1);
        List<Tone> tones = new ArrayList<>();
        String label = motif.id() + ":" + chain;
        List<Integer> sounding = new ArrayList<>();
        List<Slot> used = new ArrayList<>();
        for (int i = 0; i < rhythm.size(); i++) {
            Slot slotNote = rhythm.get(i);
            if (slotNote.rest || degrees[i] == Integer.MIN_VALUE || slotNote.ticks < 1) {
                continue;
            }
            int midi = state.key().degreeMidi(degrees[i], 5);
            if ("chord-tone".equals(slotNote.why) || "cadence".equals(slotNote.why)) {
                midi += nearestPcShift(chord, midi);
            }
            if ("leap".equals(slotNote.why) && !sounding.isEmpty()) {
                midi = sounding.get(sounding.size() - 1) + slotNote.leap;
            } else if ("passing".equals(slotNote.why) && !used.isEmpty()
                    && "leap".equals(used.get(used.size() - 1).why)) {
                midi = sounding.get(sounding.size() - 1) - Integer.signum(used.get(used.size() - 1).leap);
            }
            sounding.add(midi);
            used.add(slotNote);
        }
        int shift = registerShift(sounding, low, high);
        for (int i = 0; i < sounding.size(); i++) {
            Slot slotNote = used.get(i);
            tones.add(new Tone(slotNote.offset, slotNote.ticks, sounding.get(i) + shift, slotNote.why, label));
        }
        return List.copyOf(tones);
    }

    private static String chain(String token, boolean hook, List<String> develop, int period) {
        if ("half".equals(token) || "authentic".equals(token)) {
            token = "idea";
        }
        if (!hook && ("idea".equals(token) || "repeat".equals(token)) && !develop.isEmpty()) {
            return develop.get(Math.floorMod(period, develop.size()));
        }
        return token;
    }

    private static int[] place(Key key, Progression.Chord chord, Motif motif, List<Slot> rhythm,
                               long seed, String state, int held, int phraseBars,
                               boolean half, boolean authentic, double density) {
        int[] degrees = new int[rhythm.size()];
        int cursor = 0;
        for (int i = 0; i < rhythm.size(); i++) {
            if (rhythm.get(i).rest) {
                degrees[i] = Integer.MIN_VALUE;
                continue;
            }
            degrees[i] = motif.notes().get(cursor % motif.notes().size()).degree();
            cursor++;
        }
        int strong = firstStrong(rhythm);
        if (strong >= 0) {
            int shift = shiftOntoChord(key, chord, degrees[strong]);
            for (int i = 0; i < degrees.length; i++) {
                if (degrees[i] != Integer.MIN_VALUE) {
                    degrees[i] += shift;
                }
            }
        }
        int previous = Integer.MIN_VALUE;
        for (int i = 0; i < rhythm.size(); i++) {
            if (degrees[i] == Integer.MIN_VALUE) {
                continue;
            }
            if (rhythm.get(i).strong) {
                degrees[i] = nearestChord(key, chord, degrees[i]);
                rhythm.get(i).why = "chord-tone";
            } else if (previous != Integer.MIN_VALUE && Math.abs(degrees[i] - previous) > 1) {
                degrees[i] = previous + Integer.signum(degrees[i] - previous);
                rhythm.get(i).why = "passing";
            } else {
                rhythm.get(i).why = "passing";
            }
            previous = degrees[i];
        }
        if (Math.floorMod(held, phraseBars) == 1) {
            leap(key, degrees, rhythm, seed, state, held);
        }
        if (half || authentic) {
            cadence(key, chord, degrees, rhythm, half ? LIBRARY.half : LIBRARY.authentic);
        }
        thin(degrees, rhythm, seed, state, held, density);
        return degrees;
    }

    private static void leap(Key key, int[] degrees, List<Slot> rhythm, long seed, String state, int held) {
        int from = -1;
        int to = -1;
        for (int i = 0; i < rhythm.size(); i++) {
            if (degrees[i] == Integer.MIN_VALUE || rhythm.get(i).strong) {
                continue;
            }
            if (from < 0) {
                from = i;
                continue;
            }
            to = i;
            break;
        }
        if (from < 0 || to < 0) {
            return;
        }
        int previous = previousSounding(degrees, from);
        if (previous == Integer.MIN_VALUE) {
            return;
        }
        RandomSource rng = DefaultRandom.of(seed).fork(state.hashCode(), "leap".hashCode(), held);
        int size = LIBRARY.leapMin;
        int span = LIBRARY.leapMax - LIBRARY.leapMin + 1;
        if (span > 1) {
            size += rng.nextInt(span);
        }
        degrees[from] = previous + size;
        degrees[to] = degrees[from] - 1;
        int gap = key.degreeMidi(degrees[from], 5) - key.degreeMidi(previous, 5);
        if (Math.abs(gap) < 5) {
            gap = Integer.signum(gap == 0 ? 1 : gap) * 5;
        }
        rhythm.get(from).leap = gap;
        rhythm.get(from).why = "leap";
        rhythm.get(to).why = "passing";
    }

    private static void cadence(Key key, Progression.Chord chord, int[] degrees, List<Slot> rhythm, int[] targets) {
        int last = -1;
        for (int i = 0; i < degrees.length; i++) {
            if (degrees[i] != Integer.MIN_VALUE) {
                last = i;
            }
        }
        if (last < 0 || targets.length == 0) {
            return;
        }
        degrees[last] = nearestTarget(key, chord, degrees[last], targets);
        rhythm.get(last).why = "cadence";
        long bar = 0;
        for (Slot slot : rhythm) {
            bar = Math.max(bar, slot.offset + slot.ticks);
        }
        long min = Math.max(1, bar / 2);
        Slot note = rhythm.get(last);
        if (note.ticks < min) {
            long start = Math.max(0, bar - min);
            for (int i = 0; i < last; i++) {
                Slot before = rhythm.get(i);
                if (before.offset >= start) {
                    degrees[i] = Integer.MIN_VALUE;
                    before.rest = true;
                    before.ticks = 0;
                } else if (before.offset + before.ticks > start) {
                    before.ticks = start - before.offset;
                }
            }
            note.offset = start;
            note.ticks = bar - start;
        }
    }

    private static void thin(int[] degrees, List<Slot> rhythm, long seed, String state, int held, double density) {
        if (density >= 1) {
            return;
        }
        RandomSource rng = DefaultRandom.of(seed).fork(state.hashCode(), "density".hashCode(), held);
        for (int i = 0; i < degrees.length; i++) {
            if (degrees[i] == Integer.MIN_VALUE || rhythm.get(i).strong || "cadence".equals(rhythm.get(i).why)
                    || "leap".equals(rhythm.get(i).why)) {
                continue;
            }
            if (rng.nextDouble() >= density) {
                degrees[i] = Integer.MIN_VALUE;
                rhythm.get(i).rest = true;
            }
        }
    }

    private static int previousSounding(int[] degrees, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (degrees[i] != Integer.MIN_VALUE) {
                return degrees[i];
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int firstStrong(List<Slot> rhythm) {
        for (int i = 0; i < rhythm.size(); i++) {
            if (!rhythm.get(i).rest && rhythm.get(i).strong) {
                return i;
            }
        }
        return -1;
    }

    private static int shiftOntoChord(Key key, Progression.Chord chord, int degree) {
        int best = 0;
        int distance = Integer.MAX_VALUE;
        for (int shift = -6; shift <= 6; shift++) {
            if (!chordTone(key, chord, degree + shift)) {
                continue;
            }
            int abs = Math.abs(shift);
            if (abs < distance) {
                distance = abs;
                best = shift;
            }
        }
        return best;
    }

    private static int nearestChord(Key key, Progression.Chord chord, int degree) {
        if (chordTone(key, chord, degree)) {
            return degree;
        }
        for (int distance = 1; distance <= 7; distance++) {
            if (chordTone(key, chord, degree - distance)) {
                return degree - distance;
            }
            if (chordTone(key, chord, degree + distance)) {
                return degree + distance;
            }
        }
        return degree;
    }

    private static int nearestTarget(Key key, Progression.Chord chord, int degree, int[] targets) {
        int best = targets[0] - 1;
        int distance = Integer.MAX_VALUE;
        boolean matched = false;
        for (int target : targets) {
            int scale = target - 1;
            if (!chordTone(key, chord, scale)) {
                continue;
            }
            int abs = Math.abs(scale - degree);
            if (abs < distance) {
                distance = abs;
                best = scale;
                matched = true;
            }
        }
        if (matched) {
            return best;
        }
        for (int target : targets) {
            int scale = target - 1;
            int abs = Math.abs(scale - degree);
            if (abs < distance) {
                distance = abs;
                best = scale;
            }
        }
        return best;
    }

    /** Smallest semitone move that lands on a pitch class of this chord. */
    private static int nearestPcShift(Progression.Chord chord, int midi) {
        int pc = Math.floorMod(midi, 12);
        int best = 0;
        int distance = 12;
        for (int interval : chord.intervals()) {
            int target = Math.floorMod(chord.rootPc() + interval, 12);
            int delta = target - pc;
            if (delta > 6) {
                delta -= 12;
            } else if (delta < -6) {
                delta += 12;
            }
            if (Math.abs(delta) < distance) {
                distance = Math.abs(delta);
                best = delta;
            }
        }
        return best;
    }

    private static boolean chordTone(Key key, Progression.Chord chord, int degree) {
        int pc = Math.floorMod(key.degreeMidi(degree, 5), 12);
        for (int interval : chord.intervals()) {
            if (Math.floorMod(chord.rootPc() + interval, 12) == pc) {
                return true;
            }
        }
        return false;
    }

    private static List<Slot> fit(String cellName, long barTicks) {
        List<String> tokens = LIBRARY.cells.getOrDefault(cellName, LIBRARY.cells.get(LIBRARY.defaults.get(0)));
        return fit(tokens, barTicks);
    }

    private static List<Slot> fit(List<String> tokens, long barTicks) {
        List<Slot> slots = new ArrayList<>();
        long cursor = 0;
        int index = 0;
        while (cursor < barTicks && index < 32) {
            String token = tokens.get(index % tokens.size());
            boolean rest = token.startsWith("r");
            long ticks = TimeBase.durationTicks(rest ? token.substring(1) : token);
            if (ticks < 1) {
                ticks = TimeBase.PPQ / 2;
            }
            if (cursor + ticks > barTicks) {
                ticks = barTicks - cursor;
            }
            boolean strong = cursor == 0 || cursor == barTicks / 2;
            slots.add(new Slot(cursor, ticks, rest, strong, "passing"));
            cursor += ticks;
            index++;
        }
        return slots;
    }

    private static List<String> cells(Theme.StateDef state) {
        List<String> named = state.composition() == null ? List.of() : state.composition().list("lead.rhythmCells");
        List<String> known = new ArrayList<>();
        for (String name : named) {
            if (LIBRARY.cells.containsKey(name)) {
                known.add(name);
            }
        }
        return known.isEmpty() ? LIBRARY.defaults : known;
    }

    private static double density(Theme.StateDef state) {
        if (state.composition() == null) {
            return 1;
        }
        return state.composition().decimal("lead.density", 1);
    }

    private static int register(Theme.StateDef state, int index) {
        if (state.composition() == null) {
            return -1;
        }
        List<String> names = state.composition().list("lead.register");
        if (names.size() < 2) {
            return -1;
        }
        try {
            return PitchName.midi(names.get(index));
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /** Move the whole bar by octaves so its leaps stay intact inside the register. */
    private static int registerShift(List<Integer> midis, int low, int high) {
        if (midis.isEmpty() || low < 0 || high < low) {
            return 0;
        }
        int bottom = midis.get(0);
        int top = midis.get(0);
        for (int midi : midis) {
            bottom = Math.min(bottom, midi);
            top = Math.max(top, midi);
        }
        int shift = 0;
        while (top + shift > high && bottom + shift - 12 >= low) {
            shift -= 12;
        }
        while (bottom + shift < low && top + shift + 12 <= high) {
            shift += 12;
        }
        return shift;
    }

    private static final class Slot {
        private long offset;
        private long ticks;
        private boolean rest;
        private final boolean strong;
        private String why;
        private int leap;

        private Slot(long offset, long ticks, boolean rest, boolean strong, String why) {
            this.offset = offset;
            this.ticks = ticks;
            this.rest = rest;
            this.strong = strong;
            this.why = why;
        }
    }

    private static final class Library {
        private final Map<String, List<String>> cells;
        private final Map<String, Form> forms;
        private final List<String> defaults;
        private final int[] half;
        private final int[] authentic;
        private final int leapMin;
        private final int leapMax;

        private Library(Map<String, List<String>> cells, Map<String, Form> forms, List<String> defaults,
                        int[] half, int[] authentic, int leapMin, int leapMax) {
            this.cells = cells;
            this.forms = forms;
            this.defaults = defaults;
            this.half = half;
            this.authentic = authentic;
            this.leapMin = leapMin;
            this.leapMax = leapMax;
        }

        private Form form(String name) {
            Form found = forms.get(name);
            if (found != null) {
                return found;
            }
            found = forms.get("period");
            return found == null ? new Form(8, 4, List.of("idea"), List.of("sequence")) : found;
        }

        private static Library load() {
            JsonNode root;
            try {
                root = Composition.readResource("/presets/rhythms.yaml");
            } catch (IOException e) {
                root = null;
            }
            Map<String, List<String>> cells = new LinkedHashMap<>();
            Map<String, Form> forms = new LinkedHashMap<>();
            List<String> defaults = new ArrayList<>();
            int[] half = {2, 5, 7};
            int[] authentic = {1, 3};
            int leapMin = 3;
            int leapMax = 6;
            if (root != null && root.isObject()) {
                JsonNode cellNode = root.get("cells");
                if (cellNode != null && cellNode.isObject()) {
                    var fields = cellNode.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        cells.put(entry.getKey(), texts(entry.getValue()));
                    }
                }
                JsonNode formNode = root.get("forms");
                if (formNode != null && formNode.isObject()) {
                    var fields = formNode.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        JsonNode body = entry.getValue();
                        forms.put(entry.getKey(), new Form(
                                body.path("bars").asInt(8),
                                body.path("phrase").asInt(4),
                                texts(body.get("plan")),
                                texts(body.get("develop"))));
                    }
                }
                defaults.addAll(texts(root.get("default")));
                half = ints(root.path("cadence").path("half"), half);
                authentic = ints(root.path("cadence").path("authentic"), authentic);
                JsonNode leap = root.get("leap");
                if (leap != null && leap.isArray() && leap.size() >= 2) {
                    leapMin = leap.get(0).asInt(3);
                    leapMax = leap.get(1).asInt(6);
                }
            }
            if (cells.isEmpty()) {
                cells.put("quarter", List.of("1/4", "1/4", "1/4", "1/4"));
            }
            if (defaults.isEmpty()) {
                defaults.add(cells.keySet().iterator().next());
            }
            return new Library(cells, forms, List.copyOf(defaults), half, authentic, leapMin, leapMax);
        }

        private static List<String> texts(JsonNode node) {
            List<String> values = new ArrayList<>();
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    if (!item.asText("").isBlank()) {
                        values.add(item.asText());
                    }
                }
            }
            return values;
        }

        private static int[] ints(JsonNode node, int[] fallback) {
            if (node == null || !node.isArray() || node.isEmpty()) {
                return fallback;
            }
            int[] values = new int[node.size()];
            for (int i = 0; i < node.size(); i++) {
                values[i] = node.get(i).asInt();
            }
            return values;
        }

        private record Form(int bars, int phrase, List<String> plan, List<String> develop) {
            private Form {
                bars = Math.max(1, bars);
                phrase = Math.max(1, phrase);
                plan = plan == null || plan.isEmpty() ? List.of("idea") : List.copyOf(plan);
                develop = develop == null ? List.of() : List.copyOf(develop);
            }
        }
    }
}
