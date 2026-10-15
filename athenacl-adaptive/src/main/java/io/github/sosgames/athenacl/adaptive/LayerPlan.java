// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns layer YAML into {@link LayerSpec} rows. A list of the old names keeps each
 * name's channel and program. A map of free names takes the next free channel, and a
 * drum part takes MIDI channel 10.
 */
public final class LayerPlan {
    private LayerPlan() {
    }

    public static List<LayerSpec> legacy(List<String> names, Map<String, Integer> programs,
                                         Map<String, String> instruments) {
        List<LayerSpec> specs = new ArrayList<>();
        if (names == null) {
            return specs;
        }
        for (String name : names) {
            String behaviour = Behaviours.legacy(name);
            if (behaviour == null) {
                continue;
            }
            int program = programs == null ? Behaviours.program(name) : programs.getOrDefault(name, Behaviours.program(name));
            String kitName = instruments == null ? name : instruments.getOrDefault(name, name);
            boolean percussion = "drums".equals(name);
            specs.add(new LayerSpec(name, behaviour, Behaviours.channel(name), program, percussion,
                    kitName, kitName, Map.of()));
        }
        return List.copyOf(specs);
    }

    public static List<LayerSpec> allocate(List<Draft> drafts) {
        boolean[] used = new boolean[16];
        List<LayerSpec> specs = new ArrayList<>();
        for (Draft draft : drafts) {
            boolean drums = draft.percussion || "drum-pattern".equals(draft.behaviour);
            int channel = drums ? 9 : next(used);
            used[channel] = true;
            specs.add(new LayerSpec(draft.name, draft.behaviour, channel, draft.program, drums,
                    draft.instrument, draft.kitName, draft.params));
        }
        return List.copyOf(specs);
    }

    private static int next(boolean[] used) {
        for (int channel = 0; channel < used.length; channel++) {
            if (channel == 9 || used[channel]) {
                continue;
            }
            return channel;
        }
        return 0;
    }

    public record Draft(String name, String behaviour, int program, boolean percussion,
                        String instrument, String kitName, Map<String, String> params) {
    }
}
