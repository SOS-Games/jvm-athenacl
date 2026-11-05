// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.LayerSpec;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.render.SlotSpec;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.render.loudness.StateLoudness;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a theme through its instrument kits. Each named instrument is normalized first.
 * A declared state that asks for auto selection plays that ensemble for this seed.
 * The genre base, the stream a preset button auditions, keeps the instruments written
 * on the genre. The state's loudness contour is applied after that, on the mixed audio.
 * The event log keeps the original program changes, so the musical hash does not move.
 */
public final class ThemeMix {
    private ThemeMix() {
    }

    /** The mixed WAV and any source that fell back because its bank was missing or mismatched. */
    public record Mix(byte[] wav, List<KitSession.Notice> notices) {
    }

    public static byte[] wav(Theme theme, Score score, List<String> stateByBar, long seed)
            throws IOException, KitException, MidiUnavailableException {
        return mix(theme, score, stateByBar, seed, null).wav();
    }

    public static Mix mix(Theme theme, Score score, List<String> stateByBar, long seed, Path cache)
            throws IOException, KitException, MidiUnavailableException {
        if (theme.kits().isEmpty()) {
            throw new KitException(new KitException.Issue(
                    "KIT_UNKNOWN", "/instruments/kits",
                    "theme has no kit",
                    "Set instruments.kits, such as gm-full, or leave it unset to use the default."));
        }
        List<KitSession> sessions = new ArrayList<>();
        List<KitSession.Notice> notices = new ArrayList<>();
        for (String id : theme.kits()) {
            KitSession session = KitSession.open(Kits.find(id), SoundbankPaths.cache(cache), false);
            sessions.add(session);
            notices.addAll(session.notices());
        }
        Map<String, Map<String, Playable>> byState = new LinkedHashMap<>();
        Map<String, Playable> used = new LinkedHashMap<>();
        for (Theme.StateDef state : playbackStates(theme)) {
            Ensemble.Result ensemble = Ensemble.forPlayback(state.composition(), state.id(), seed);
            Map<String, Playable> patches = new LinkedHashMap<>();
            for (LayerSpec spec : state.specs()) {
                String patch = spec.channel() + ":" + spec.program();
                String name = heardName(state, spec, ensemble);
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
                patches.putIfAbsent(patch, playable);
                used.putIfAbsent(playable.name(), playable);
            }
            byState.put(state.id(), patches);
        }
        for (Playable playable : used.values()) {
            ProbeNormalizer.measure(playable);
        }
        List<SlotSpec> slots = new ArrayList<>();
        for (Playable playable : used.values()) {
            slots.add(ProbeNormalizer.spec(playable));
        }
        byte[] body = WavRenderer.write(retarget(score, byState, stateByBar), null, slots);
        if (theme.loudness() != null && stateByBar != null && !stateByBar.isEmpty()) {
            body = StateLoudness.fit(body, score.bpm(), score.barTicks(), stateByBar,
                    theme.loudness().reference(), theme.loudness().offsets());
        }
        return new Mix(body, List.copyOf(notices));
    }

    /**
     * States the mix should load. A theme that declared none plays {@code base}, the genre.
     * A theme that declared states keeps those and leaves the base out.
     */
    static List<Theme.StateDef> playbackStates(Theme theme) {
        if (!theme.states().isEmpty()) {
            return List.copyOf(theme.states().values());
        }
        if (theme.base() == null) {
            return List.of();
        }
        return List.of(theme.base());
    }

    /** Kit name this layer should load. A null ensemble keeps the name written on the state. */
    static String heardName(Theme.StateDef state, LayerSpec spec, Ensemble.Result ensemble) {
        String name = ensemble == null
                ? state.instrument(spec.name())
                : ensemble.playing(spec.name(), state.instrument(spec.name()));
        if (name.startsWith("sf:") || name.startsWith("kit:") || name.indexOf(':') > 0) {
            name = spec.kitName().isBlank() ? name : spec.kitName();
        }
        return name;
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

    private static Score retarget(Score score, Map<String, Map<String, Playable>> byState, List<String> stateByBar) {
        List<ControlEvent> controls = new ArrayList<>();
        long barTicks = Math.max(1, score.barTicks());
        for (ControlEvent control : score.controls()) {
            if (control instanceof ControlEvent.ProgramChange change) {
                String stateId = stateAt(change.startTick(), barTicks, stateByBar);
                Map<String, Playable> patches = byState.get(stateId);
                if (patches == null && !byState.isEmpty() && (stateByBar == null || stateByBar.isEmpty())) {
                    patches = byState.values().iterator().next();
                }
                Playable playable = patches == null ? null : patches.get(change.channel() + ":" + change.program());
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

    private static String stateAt(long tick, long barTicks, List<String> stateByBar) {
        if (stateByBar == null || stateByBar.isEmpty()) {
            return "";
        }
        int bar = (int) (tick / barTicks);
        if (bar < 0) {
            bar = 0;
        }
        if (bar >= stateByBar.size()) {
            bar = stateByBar.size() - 1;
        }
        return stateByBar.get(bar);
    }
}
