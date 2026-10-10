// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.analysis;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.theory.HarmonyEstimate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Per-bar text for an event log. One column of a roll is a 16th note: {@code ●} starts the note,
 * {@code ─} holds it, {@code ·} is empty. A quarter note at the downbeat is {@code C4 |●───}.
 * Channel 9 is a drum grid under the pitched roll. When the notes support it, the summary
 * adds {@code chord=}, {@code key=}, and {@code conf=} from {@link HarmonyEstimate}.
 */
public final class DescribeMusic {
    private static final Map<Integer, String> DRUMS = Map.of(
            36, "kick",
            35, "kick",
            38, "snare",
            40, "snare",
            42, "hat",
            44, "hat",
            46, "hatOpen");

    private DescribeMusic() {
    }

    public static String text(Score score, boolean roll, int fromBar, int toBar) {
        HarmonyEstimate.Reading harmony = HarmonyEstimate.read(score, fromBar, toBar);
        StringBuilder out = new StringBuilder();
        for (int bar = fromBar; bar <= toBar; bar++) {
            List<NoteEvent> inBar = notesIn(score, bar);
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(summary(score, bar, inBar, harmony));
            if (roll) {
                out.append('\n').append(roll(score, bar, inBar, false));
                String drums = roll(score, bar, inBar, true);
                if (!drums.isBlank()) {
                    out.append('\n').append(drums);
                }
            }
        }
        return out.toString();
    }

    public static String summary(Score score, int bar, List<NoteEvent> inBar) {
        return summary(score, bar, inBar, HarmonyEstimate.read(score));
    }

    private static String summary(Score score, int bar, List<NoteEvent> inBar, HarmonyEstimate.Reading harmony) {
        List<NoteEvent> pitched = pitched(inBar);
        String range = range(pitched);
        int minVel = 0;
        int maxVel = 0;
        for (NoteEvent note : pitched) {
            if (minVel == 0 || note.velocity() < minVel) {
                minVel = note.velocity();
            }
            maxVel = Math.max(maxVel, note.velocity());
        }
        double beats = score.numerator() * 4.0 / score.denominator();
        String density = beats == 0 ? "0" : trim(pitched.size() / beats);
        StringBuilder line = new StringBuilder();
        line.append("bar=").append(bar)
                .append(" notes=").append(pitched.size())
                .append(" range=").append(range)
                .append(" bpm=").append(score.bpm())
                .append(" density=").append(density);
        if (!pitched.isEmpty()) {
            line.append(" vel=").append(minVel).append("..").append(maxVel);
        }
        if (harmony.key() != null) {
            String chord = harmony.chord(bar);
            if (chord != null) {
                line.append(" chord=").append(chord);
            }
            line.append(" key=").append(harmony.key());
            line.append(" conf=").append(harmony.confidence());
        }
        String layers = layers(inBar);
        if (!layers.isEmpty()) {
            line.append(" layers=").append(layers);
        }
        return line.toString();
    }

    private static String layers(List<NoteEvent> inBar) {
        TreeSet<String> names = new TreeSet<>();
        for (NoteEvent note : inBar) {
            if (note.layer() != null && !note.layer().isBlank()) {
                names.add(note.layer());
            }
        }
        return String.join(",", names);
    }

    private static String roll(Score score, int bar, List<NoteEvent> inBar, boolean drums) {
        int width = score.numerator() * 16 / score.denominator();
        int columns = width <= 0 ? 16 : width;
        long columnTicks = TimeBase.PPQ / 4;
        long barStart = (bar - 1L) * score.barTicks();
        Map<String, char[]> rows = new TreeMap<>((left, right) -> {
            int byPitch = Integer.compare(sortKey(right), sortKey(left));
            return byPitch != 0 ? byPitch : left.compareTo(right);
        });
        for (NoteEvent note : inBar) {
            boolean isDrum = note.channel() == 9;
            if (isDrum != drums) {
                continue;
            }
            String label = isDrum ? DRUMS.getOrDefault(note.pitch(), note.pitchName()) : note.pitchName();
            char[] cells = rows.computeIfAbsent(label, key -> rest(columns));
            int start = (int) ((note.startTick() - barStart) / columnTicks);
            int length = (int) (note.durationTicks() / columnTicks);
            if (length < 1) {
                length = 1;
            }
            for (int i = 0; i < length; i++) {
                int column = start + i;
                if (column >= 0 && column < cells.length) {
                    cells[column] = i == 0 ? '●' : '─';
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, char[]> row : rows.entrySet()) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(row.getKey()).append(" |").append(row.getValue());
        }
        return out.toString();
    }

    private static int sortKey(String label) {
        try {
            return io.github.sosgames.athenacl.events.PitchName.midi(label);
        } catch (IllegalArgumentException e) {
            return DRUMS.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(label))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(0);
        }
    }

    private static char[] rest(int columns) {
        char[] cells = new char[columns];
        java.util.Arrays.fill(cells, '·');
        return cells;
    }

    private static List<NoteEvent> notesIn(Score score, int bar) {
        long barStart = (bar - 1L) * score.barTicks();
        long barEnd = barStart + score.barTicks();
        List<NoteEvent> found = new ArrayList<>();
        for (NoteEvent note : score.notes()) {
            long noteEnd = note.startTick() + note.durationTicks();
            if (note.startTick() < barEnd && noteEnd > barStart) {
                found.add(note);
            }
        }
        return found;
    }

    private static List<NoteEvent> pitched(List<NoteEvent> notes) {
        List<NoteEvent> found = new ArrayList<>();
        for (NoteEvent note : notes) {
            if (note.channel() != 9) {
                found.add(note);
            }
        }
        return found;
    }

    private static String range(List<NoteEvent> notes) {
        if (notes.isEmpty()) {
            return "none";
        }
        NoteEvent low = notes.get(0);
        NoteEvent high = notes.get(0);
        for (NoteEvent note : notes) {
            if (note.pitch() < low.pitch()) {
                low = note;
            }
            if (note.pitch() > high.pitch()) {
                high = note;
            }
        }
        return low.pitchName() + ".." + high.pitchName();
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public static Map<String, Object> data(Score score, boolean roll, int fromBar, int toBar) {
        HarmonyEstimate.Reading harmony = HarmonyEstimate.read(score);
        List<Map<String, Object>> bars = new ArrayList<>();
        for (int bar = fromBar; bar <= toBar; bar++) {
            List<NoteEvent> inBar = notesIn(score, bar);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bar", bar);
            row.put("text", summary(score, bar, inBar, harmony));
            if (harmony.key() != null) {
                String chord = harmony.chord(bar);
                if (chord != null) {
                    row.put("chord", chord);
                }
                row.put("key", harmony.key());
                row.put("confidence", harmony.confidence());
            }
            if (roll) {
                row.put("roll", roll(score, bar, inBar, false));
            }
            bars.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bars", bars);
        return data;
    }
}
