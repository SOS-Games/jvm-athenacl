// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.util.Map;

/**
 * One named part in a state. {@code behaviour} is the player (pad, bass, arpeggio).
 * {@code instrument} is the YAML reference that chose the program. A drum part uses
 * MIDI channel 10. The note's layer name is {@code name}, so a part called shimmer
 * stays shimmer in the event log.
 */
public record LayerSpec(String name, String behaviour, int channel, int program, boolean percussion,
                        String instrument, String kitName, Map<String, String> params) {
    public LayerSpec {
        params = params == null || params.isEmpty() ? Map.of() : Map.copyOf(params);
        instrument = instrument == null ? "" : instrument;
        kitName = kitName == null ? "" : kitName;
    }
}
