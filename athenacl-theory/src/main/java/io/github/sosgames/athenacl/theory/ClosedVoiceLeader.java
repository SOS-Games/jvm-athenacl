// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Close voicings with no crossed voices. The next chord keeps every leap small and
 * refuses parallel fifths and octaves when a voicing like that exists.
 * The first chord sits near C4 with the root in the bass.
 */
public final class ClosedVoiceLeader {
    private static final int LOW = 48;
    private static final int HIGH = 84;
    private static final int SPAN = 16;
    private static final int CENTER = 64;

    private ClosedVoiceLeader() {
    }

    public static int[] first(int[] pcs) {
        int[] best = null;
        int bestScore = Integer.MAX_VALUE;
        int root = Math.floorMod(pcs[0], 12);
        for (int[] voicing : voicings(pcs)) {
            int distance = 0;
            for (int midi : voicing) {
                distance += Math.abs(midi - CENTER);
            }
            if (Math.floorMod(voicing[0], 12) != root) {
                distance += 48;
            }
            if (best == null || distance < bestScore || (distance == bestScore && Arrays.compare(voicing, best) < 0)) {
                best = voicing;
                bestScore = distance;
            }
        }
        return best == null ? block(pcs) : best;
    }

    public static int[] next(int[] pcs, int[] previous) {
        int[] best = null;
        int bestParallels = Integer.MAX_VALUE;
        int bestMax = Integer.MAX_VALUE;
        int bestTotal = Integer.MAX_VALUE;
        for (int[] voicing : voicings(pcs)) {
            if (voicing.length != previous.length) {
                continue;
            }
            int parallels = parallels(previous, voicing);
            int max = maxMotion(previous, voicing);
            int total = totalMotion(previous, voicing);
            boolean better = parallels < bestParallels
                    || (parallels == bestParallels && max < bestMax)
                    || (parallels == bestParallels && max == bestMax && total < bestTotal)
                    || (parallels == bestParallels && max == bestMax && total == bestTotal
                    && (best == null || Arrays.compare(voicing, best) < 0));
            if (better) {
                best = voicing;
                bestParallels = parallels;
                bestMax = max;
                bestTotal = total;
            }
        }
        return best == null ? block(pcs) : best;
    }

    /** One octave starting at C4, sorted. Used when voice leading is off. */
    public static int[] block(int[] pcs) {
        int[] midi = new int[pcs.length];
        for (int i = 0; i < pcs.length; i++) {
            midi[i] = 60 + Math.floorMod(pcs[i], 12);
        }
        Arrays.sort(midi);
        return midi;
    }

    public static int maxMotion(int[] before, int[] after) {
        int max = 0;
        int count = Math.min(before.length, after.length);
        for (int i = 0; i < count; i++) {
            max = Math.max(max, Math.abs(after[i] - before[i]));
        }
        return max;
    }

    public static int parallels(int[] before, int[] after) {
        if (before.length != after.length) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < before.length; i++) {
            for (int j = i + 1; j < before.length; j++) {
                int interval = before[j] - before[i];
                if (interval != after[j] - after[i] || interval <= 0 || !perfect(interval)) {
                    continue;
                }
                if (after[i] - before[i] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int totalMotion(int[] before, int[] after) {
        int total = 0;
        for (int i = 0; i < before.length; i++) {
            total += Math.abs(after[i] - before[i]);
        }
        return total;
    }

    private static boolean perfect(int semitones) {
        int mod = Math.floorMod(semitones, 12);
        return mod == 0 || mod == 7;
    }

    private static List<int[]> voicings(int[] pcs) {
        List<int[]> found = new ArrayList<>();
        pick(pcs, 0, new int[pcs.length], found);
        return found;
    }

    private static void pick(int[] pcs, int index, int[] choice, List<int[]> found) {
        if (index == pcs.length) {
            int[] midi = choice.clone();
            Arrays.sort(midi);
            for (int i = 1; i < midi.length; i++) {
                if (midi[i] == midi[i - 1]) {
                    return;
                }
            }
            if (midi[midi.length - 1] - midi[0] > SPAN) {
                return;
            }
            found.add(midi);
            return;
        }
        int pc = Math.floorMod(pcs[index], 12);
        for (int midi = pc; midi <= HIGH; midi += 12) {
            if (midi < LOW) {
                continue;
            }
            choice[index] = midi;
            pick(pcs, index + 1, choice, found);
        }
    }
}
