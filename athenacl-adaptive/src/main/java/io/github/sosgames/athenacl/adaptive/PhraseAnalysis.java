// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a rendered score the way a person would listen for harmony and a lead.
 * Each 8-bar group counts its bass pitch classes. A dominant cadence is a bass
 * a fifth above that group's first bass, followed by a return to it, or a fifth
 * left hanging on the last bar. The lead contributes its durations, leaps of a
 * fifth or more, overlapping notes, and how many different bar rhythms it used.
 */
public final class PhraseAnalysis {
    private PhraseAnalysis() {
    }

    public record Report(
            int distinctRoots,
            int dominantCadences,
            int rhythmValues,
            int leapsOver7,
            int overlaps,
            int distinctBarPatterns,
            boolean dronePresent) {

        public boolean passes() {
            return distinctRoots >= 3 && overlaps == 0;
        }

        public String line() {
            return "harmony.distinctRoots=" + distinctRoots
                    + " harmony.dominantCadences=" + dominantCadences
                    + " lead.rhythmValues=" + rhythmValues
                    + " lead.leapsOver7=" + leapsOver7
                    + " lead.overlaps=" + overlaps
                    + " lead.distinctBarPatterns=" + distinctBarPatterns
                    + " drone.present=" + dronePresent;
        }
    }

    public static Report of(Score score) {
        Map<Integer, Integer> bass = new LinkedHashMap<>();
        List<NoteEvent> lead = new ArrayList<>();
        boolean drone = false;
        int bars = score.bars();
        for (NoteEvent note : score.notes()) {
            bars = Math.max(bars, note.bar());
            if ("drone".equals(note.layer())) {
                drone = true;
            } else if ("bass".equals(note.layer())) {
                bass.putIfAbsent(note.bar(), Math.floorMod(note.pitch(), 12));
            } else if ("lead".equals(note.layer())) {
                lead.add(note);
            }
        }
        lead.sort((left, right) -> {
            int byStart = Long.compare(left.startTick(), right.startTick());
            return byStart != 0 ? byStart : Long.compare(left.id(), right.id());
        });
        int minRoots = Integer.MAX_VALUE;
        int cadences = 0;
        boolean anyWindow = false;
        for (int start = 1; start <= Math.max(bars, 1); start += 8) {
            int end = Math.min(Math.max(bars, 1), start + 7);
            Set<Integer> roots = new LinkedHashSet<>();
            int tonic = -1;
            for (int bar = start; bar <= end; bar++) {
                Integer pc = bass.get(bar);
                if (pc == null) {
                    continue;
                }
                if (tonic < 0) {
                    tonic = pc;
                }
                roots.add(pc);
            }
            anyWindow = true;
            minRoots = Math.min(minRoots, roots.size());
            if (tonic < 0) {
                continue;
            }
            int dominant = Math.floorMod(tonic + 7, 12);
            for (int bar = start; bar <= end; bar++) {
                Integer pc = bass.get(bar);
                if (pc == null || pc != dominant) {
                    continue;
                }
                Integer next = bass.get(bar + 1);
                if (next == null || next == tonic) {
                    cadences++;
                }
            }
        }
        Set<String> durations = new LinkedHashSet<>();
        Set<String> patterns = new LinkedHashSet<>();
        Map<Integer, StringBuilder> byBar = new LinkedHashMap<>();
        int leaps = 0;
        int overlaps = 0;
        for (int i = 0; i < lead.size(); i++) {
            NoteEvent note = lead.get(i);
            if (note.duration() != null && !note.duration().isBlank()) {
                durations.add(note.duration());
            }
            byBar.computeIfAbsent(note.bar(), key -> new StringBuilder());
            StringBuilder pattern = byBar.get(note.bar());
            if (!pattern.isEmpty()) {
                pattern.append(',');
            }
            pattern.append(note.duration());
            if (i == 0) {
                continue;
            }
            NoteEvent previous = lead.get(i - 1);
            if (note.startTick() < previous.startTick() + previous.durationTicks()) {
                overlaps++;
            }
            if (Math.abs(note.pitch() - previous.pitch()) >= 7) {
                leaps++;
            }
        }
        for (StringBuilder pattern : byBar.values()) {
            if (!pattern.isEmpty()) {
                patterns.add(pattern.toString());
            }
        }
        return new Report(anyWindow ? minRoots : 0, cadences, durations.size(), leaps, overlaps,
                patterns.size(), drone);
    }
}
