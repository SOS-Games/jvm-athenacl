// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import io.github.sosgames.athenacl.adaptive.LayerSpec;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.render.SlotSpec;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.render.loudness.StateLoudness;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a theme through its instrument kits. Each named instrument is normalized first.
 * The state's loudness contour is applied after that, on the mixed audio. The event log
 * keeps the original program changes, so the musical hash does not move.
 */
public final class ThemeMix {
    private ThemeMix() {
    }

    public static byte[] wav(Theme theme, Score score, List<String> stateByBar)
            throws IOException, KitException, MidiUnavailableException {
        if (theme.kits().isEmpty()) {
            throw new KitException(new KitException.Issue(
                    "KIT_UNKNOWN", "/instruments/kits",
                    "theme has no kit",
                    "Set instruments.kits, such as gm-lite."));
        }
        List<KitSession> sessions = new ArrayList<>();
        for (String id : theme.kits()) {
            sessions.add(KitSession.open(Kits.find(id), SoundbankPaths.cache(null), false));
        }
        Map<String, Playable> byPatch = new LinkedHashMap<>();
        Map<String, Playable> used = new LinkedHashMap<>();
        for (Theme.StateDef state : theme.states().values()) {
            for (LayerSpec spec : state.specs()) {
                int channel = spec.channel();
                int program = spec.program();
                String patch = channel + ":" + program;
                String name = state.instrument(spec.name());
                if (name.startsWith("sf:") || name.startsWith("kit:") || name.indexOf(':') > 0) {
                    name = spec.kitName().isBlank() ? name : spec.kitName();
                }
                Playable playable = resolve(sessions, name);
                if (playable == null) {
                    if (spec.instrument().startsWith("sf:")) {
                        continue;
                    }
                    throw new KitException(new KitException.Issue(
                            "KIT_UNKNOWN_INSTRUMENT", "/states/" + state.id() + "/" + spec.name(),
                            "unknown instrument " + name,
                            "Use a name from the kit, such as harp or taiko."));
                }
                byPatch.putIfAbsent(patch, playable);
                used.putIfAbsent(playable.name(), playable);
            }
        }
        for (Playable playable : used.values()) {
            ProbeNormalizer.measure(playable);
        }
        List<SlotSpec> slots = new ArrayList<>();
        for (Playable playable : used.values()) {
            slots.add(ProbeNormalizer.spec(playable));
        }
        byte[] body = WavRenderer.write(retarget(score, byPatch), null, slots);
        if (theme.loudness() != null && stateByBar != null && !stateByBar.isEmpty()) {
            body = StateLoudness.fit(body, score.bpm(), score.barTicks(), stateByBar,
                    theme.loudness().reference(), theme.loudness().offsets());
        }
        return body;
    }

    private static Playable resolve(List<KitSession> sessions, String name) throws IOException, KitException {
        for (KitSession session : sessions) {
            Playable playable = session.resolve(name);
            if (playable != null) {
                return playable;
            }
        }
        return null;
    }

    private static Score retarget(Score score, Map<String, Playable> byPatch) {
        List<ControlEvent> controls = new ArrayList<>();
        for (ControlEvent control : score.controls()) {
            if (control instanceof ControlEvent.ProgramChange change) {
                Playable playable = byPatch.get(change.channel() + ":" + change.program());
                if (playable == null) {
                    controls.add(change);
                } else {
                    controls.addAll(ProbeNormalizer.controls(playable, change.channel(), 100));
                }
            } else {
                controls.add(control);
            }
        }
        return new Score(controls, score.notes(), score.bpm(), score.numerator(), score.denominator(), score.bars());
    }
}
