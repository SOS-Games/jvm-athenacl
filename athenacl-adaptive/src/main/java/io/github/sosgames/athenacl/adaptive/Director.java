// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline director. It walks the musical clock from bar 1, applies state changes on the
 * grid their rule names, and asks the stock layers for notes. Combat and danger land on
 * the next beat and drop notes already queued after that beat. An overlay adds a layer
 * on top of the state. Leaving combat can play a wind-down, and a loudness jump past
 * the contour step writes a CC11 ramp. The same seed and the same controls always
 * build the same score. Nothing here reads the wall clock.
 */
public final class Director {
    private Director() {
    }

    public record Played(NoteEvent note, String why) {
    }

    public record Transition(String from, String to, long requestTick, long appliedTick, String quantize,
                             int retracted, long latencyMs, String bridge) {
    }

    public record BarInfo(int bar, String state, double intensity, String key, int bpm) {
    }

    public record Performance(Score score, List<Played> notes, List<Transition> transitions, List<BarInfo> bars,
                              List<String> overlays) {
        public String stateList() {
            List<String> names = new ArrayList<>();
            for (BarInfo bar : bars) {
                if (!names.contains(bar.state())) {
                    names.add(bar.state());
                }
            }
            return String.join(",", names);
        }

        public String overlayList() {
            return String.join(",", overlays);
        }
    }

    public static Performance perform(Theme theme, List<Theme.Control> controls, long seed) {
        StockLayers.prepareHooks(theme, seed);
        long barTicks = theme.barTicks();
        List<Scheduled> scheduled = new ArrayList<>();
        List<OverlayCue> overlayCues = new ArrayList<>();
        String previous = null;
        for (Theme.Control control : controls) {
            long request = MusicalClock.tick(control.bar(), control.beat(), barTicks);
            request = MusicalClock.snapFrame(request, theme.frameMs(), theme.bpm());
            if (control.hasOverlay()) {
                long applied = MusicalClock.quantize(request, Quantize.BAR, barTicks);
                int appliedBar = (int) (applied / barTicks) + 1;
                overlayCues.add(new OverlayCue(control.overlay(), control.overlayLevel(), appliedBar));
            }
            if (!control.hasState()) {
                continue;
            }
            String from = previous == null ? "*" : previous;
            Quantize quantize = theme.quantize(from, control.state());
            long applied = MusicalClock.quantize(request, quantize, barTicks);
            int appliedBar = (int) (applied / barTicks) + 1;
            scheduled.add(new Scheduled(control, request, applied, appliedBar, quantize));
            previous = control.state();
        }
        scheduled.sort(Comparator.comparingInt(Scheduled::appliedBar).thenComparingLong(Scheduled::request));

        List<Played> notes = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();
        List<BarInfo> bars = new ArrayList<>();
        List<ControlEvent> events = new ArrayList<>();
        events.add(new ControlEvent.Meter(0, theme.numerator(), theme.denominator()));
        events.add(new ControlEvent.ProgramChange(0, 0, 48));
        events.add(new ControlEvent.ProgramChange(0, 1, 32));
        events.add(new ControlEvent.ProgramChange(0, 2, 73));
        events.add(new ControlEvent.ProgramChange(0, 3, 56));
        events.add(new ControlEvent.ProgramChange(0, 9, 0));

        IntensityTrack intensity = new IntensityTrack();
        Ids ids = new Ids();
        int[] stingerBar = {-1000};
        Map<String, Double> activeOverlays = new LinkedHashMap<>();
        List<String> heardOverlays = new ArrayList<>();
        String stateId = null;
        double target = theme.hold() == null ? 0.3 : theme.hold().intensity();
        int lastTempo = -1;
        int leadDegree = -1;
        int held = 0;
        int cursor = 0;
        for (int bar = 1; bar <= theme.bars(); bar++) {
            long barStart = (bar - 1L) * barTicks;
            int mark = cursor;
            boolean mid = false;
            while (cursor < scheduled.size() && scheduled.get(cursor).appliedBar == bar) {
                if (scheduled.get(cursor).applied != barStart) {
                    mid = true;
                }
                cursor++;
            }
            int stop = cursor;
            cursor = mark;
            foldOverlays(overlayCues, bar, activeOverlays, heardOverlays);
            if (!mid) {
                boolean changed = false;
                String bridge = "";
                while (cursor < scheduled.size() && scheduled.get(cursor).appliedBar == bar) {
                    Scheduled next = scheduled.get(cursor++);
                    if (stateId != null && !stateId.equals(next.control.state())) {
                        int removed = retract(notes, next.applied, barTicks);
                        bridge = bridgeName(theme, stateId, next.control.state());
                        transitions.add(landed(stateId, next, removed, clock(lastTempo, theme), bridge));
                        ramp(theme, stateId, next.control.state(), next.applied, barTicks, events);
                        changed = true;
                        leadDegree = -1;
                    } else if (stateId == null) {
                        changed = true;
                        leadDegree = -1;
                    }
                    stateId = next.control.state();
                    target = next.control.intensity();
                }
                if (stateId == null) {
                    stateId = theme.hold() == null ? theme.firstState() : theme.hold().state();
                    changed = true;
                }
                if (changed) {
                    held = 0;
                } else {
                    held++;
                }
                leadDegree = playBar(theme, stateId, target, changed, bar, barStart, seed, ids, notes, events,
                        bars, intensity, leadDegree, held, lastTempo, true, barStart, bridge,
                        activeOverlays, stingerBar);
                lastTempo = bars.get(bars.size() - 1).bpm();
            } else {
                cursor = stop;
                boolean changed = false;
                String bridge = "";
                while (mark < stop && scheduled.get(mark).applied == barStart) {
                    Scheduled next = scheduled.get(mark++);
                    if (stateId != null && !stateId.equals(next.control.state())) {
                        int removed = retract(notes, next.applied, barTicks);
                        bridge = bridgeName(theme, stateId, next.control.state());
                        transitions.add(landed(stateId, next, removed, clock(lastTempo, theme), bridge));
                        ramp(theme, stateId, next.control.state(), next.applied, barTicks, events);
                        changed = true;
                        leadDegree = -1;
                    } else if (stateId == null) {
                        changed = true;
                        leadDegree = -1;
                    }
                    stateId = next.control.state();
                    target = next.control.intensity();
                }
                if (stateId == null) {
                    stateId = theme.hold() == null ? theme.firstState() : theme.hold().state();
                    changed = true;
                }
                if (changed) {
                    held = 0;
                } else {
                    held++;
                }
                leadDegree = playBar(theme, stateId, target, changed, bar, barStart, seed, ids, notes, events,
                        bars, intensity, leadDegree, held, lastTempo, true, barStart, bridge,
                        activeOverlays, stingerBar);
                lastTempo = bars.get(bars.size() - 1).bpm();
                while (mark < stop) {
                    Scheduled next = scheduled.get(mark++);
                    if (stateId != null && stateId.equals(next.control.state())) {
                        target = next.control.intensity();
                        continue;
                    }
                    int bpm = lastTempo > 0 ? lastTempo : theme.bpm();
                    int removed = retract(notes, next.applied, barTicks);
                    String left = stateId == null
                            ? (theme.hold() == null ? theme.firstState() : theme.hold().state())
                            : stateId;
                    String arrival = bridgeName(theme, left, next.control.state());
                    transitions.add(landed(left, next, removed, bpm, arrival));
                    ramp(theme, left, next.control.state(), next.applied, barTicks, events);
                    stateId = next.control.state();
                    target = next.control.intensity();
                    leadDegree = -1;
                    held = 0;
                    List<Played> fresh = new ArrayList<>();
                    leadDegree = playBar(theme, stateId, target, true, bar, barStart, seed, ids, fresh, events,
                            bars, intensity, leadDegree, held, lastTempo, false, next.applied, arrival,
                            activeOverlays, stingerBar);
                    adopt(notes, fresh, next.applied, barTicks);
                    lastTempo = bars.get(bars.size() - 1).bpm();
                }
            }
        }
        dropScaleUnderMotif(notes);
        List<NoteEvent> eventsNotes = new ArrayList<>();
        for (Played played : notes) {
            eventsNotes.add(played.note());
        }
        int bpm = bars.isEmpty() ? theme.bpm() : bars.get(0).bpm();
        Score score = new Score(events, eventsNotes, bpm, theme.numerator(), theme.denominator(), theme.bars());
        return new Performance(score, List.copyOf(notes), List.copyOf(transitions), List.copyOf(bars),
                List.copyOf(heardOverlays));
    }

    /** A motif that spills into the next bar wins over the plain scale line on the same pitch. */
    private static void dropScaleUnderMotif(List<Played> notes) {
        List<NoteEvent> motifs = new ArrayList<>();
        for (Played played : notes) {
            NoteEvent note = played.note();
            if ("lead".equals(note.layer()) && note.motif() != null && !note.motif().isBlank()) {
                motifs.add(note);
            }
        }
        notes.removeIf(played -> {
            NoteEvent note = played.note();
            if (!"lead".equals(note.layer()) || (note.motif() != null && !note.motif().isBlank())) {
                return false;
            }
            for (NoteEvent motif : motifs) {
                long motifEnd = motif.startTick() + motif.durationTicks();
                long noteEnd = note.startTick() + note.durationTicks();
                if (motif.pitch() == note.pitch() && note.startTick() < motifEnd && motif.startTick() < noteEnd) {
                    return true;
                }
            }
            return false;
        });
    }

    private static int clock(int lastTempo, Theme theme) {
        return lastTempo > 0 ? lastTempo : theme.bpm();
    }

    private static Transition landed(String from, Scheduled next, int retracted, int bpm, String bridge) {
        return new Transition(from, next.control.state(), next.request, next.applied, next.quantize.id(),
                retracted, Scheduler.ticksToMs(next.applied - next.request, bpm), bridge);
    }

    private static String bridgeName(Theme theme, String from, String to) {
        Theme.Rule rule = theme.ruleFor(from, to);
        if (rule == null || rule.bridge().isBlank()) {
            return "";
        }
        return rule.bridge();
    }

    /** A jump past {@code maxStep} ramps CC11 across the arrival bar instead of stepping. */
    private static void ramp(Theme theme, String from, String to, long tick, long barTicks,
                             List<ControlEvent> events) {
        if (theme.loudness() == null || from == null) {
            return;
        }
        double step = Math.abs(theme.loudness().offset(to) - theme.loudness().offset(from));
        if (step <= theme.loudness().maxStep()) {
            return;
        }
        Theme.Rule rule = theme.ruleFor(from, to);
        int bars = rule == null ? 1 : Math.max(1, rule.crossfadeBars());
        int points = 4;
        long span = barTicks * bars;
        for (int i = 0; i < points; i++) {
            long at = tick + span * i / points;
            int value = 96 + (31 * i) / (points - 1);
            events.add(new ControlEvent.ControlChange(at, 0, 11, value));
        }
    }

    private static void foldOverlays(List<OverlayCue> cues, int bar, Map<String, Double> active,
                                     List<String> heard) {
        for (OverlayCue cue : cues) {
            if (cue.appliedBar != bar) {
                continue;
            }
            if (cue.level <= 0) {
                active.remove(cue.id);
            } else {
                active.put(cue.id, cue.level);
                if (!heard.contains(cue.id)) {
                    heard.add(cue.id);
                }
            }
        }
    }

    private static int playBar(Theme theme, String stateId, double target, boolean changed, int bar, long barStart,
                               long seed, Ids ids, List<Played> notes, List<ControlEvent> events, List<BarInfo> bars,
                               IntensityTrack intensity, int leadDegree, int held, int lastTempo,
                               boolean resetIds, long eventTick, String bridge, Map<String, Double> overlays,
                               int[] stingerBar) {
        int every = theme.motifEvery();
        int phraseBar = held / every;
        boolean playMotif = held % every == 0;
        Theme.StateDef state = theme.state(stateId);
        double smoothed = intensity.apply(target, changed);
        if (state.tempo() != lastTempo) {
            events.add(new ControlEvent.Tempo(eventTick, state.tempo()));
        }
        BarInfo info = new BarInfo(bar, stateId, smoothed, state.key().name(), state.tempo());
        bars.add(info);
        if (resetIds) {
            ids.start(bar);
        }
        if (changed) {
            for (String layer : state.layers()) {
                events.add(new ControlEvent.ProgramChange(eventTick, StockLayers.channel(layer),
                        StockLayers.program(state, layer)));
            }
        }
        if (changed && state.stinger() != null) {
            Theme.StingerDef stinger = theme.stingers().get(state.stinger());
            if (stinger != null && (stinger.cooldownBars() <= 0 || bar - stingerBar[0] >= stinger.cooldownBars())) {
                StockLayers.stinger(theme, state, info, stinger, eventTick, seed, ids, notes);
                stingerBar[0] = bar;
            }
        }
        boolean drums = intensity.drums(smoothed);
        int degree = StockLayers.bar(theme, state, info, barStart, seed, ids, notes, leadDegree, drums,
                phraseBar, playMotif);
        if (resetIds && "windDown".equals(bridge)) {
            events.add(new ControlEvent.ProgramChange(eventTick, 11, 73));
            StockLayers.windDown(state, info, barStart, seed, ids, notes);
        }
        if (resetIds && !overlays.isEmpty()) {
            events.add(new ControlEvent.ProgramChange(eventTick, 8, 47));
            for (double level : overlays.values()) {
                StockLayers.heartbeat(state, info, barStart, seed, ids, notes, level);
            }
        }
        return degree;
    }

    /** Drops notes queued at or after the switch, and ends a sounding note on that tick. */
    private static int retract(List<Played> notes, long cut, long barTicks) {
        int removed = 0;
        for (int i = notes.size() - 1; i >= 0; i--) {
            NoteEvent note = notes.get(i).note();
            long end = note.startTick() + note.durationTicks();
            if (note.startTick() >= cut) {
                notes.remove(i);
                removed++;
            } else if (end > cut) {
                notes.set(i, new Played(retimed(note, note.startTick(), cut - note.startTick(), barTicks),
                        notes.get(i).why()));
            }
        }
        return removed;
    }

    /** Keeps the new state's notes that sound at the switch or later. A held chord starts at the switch. */
    private static void adopt(List<Played> notes, List<Played> fresh, long cut, long barTicks) {
        List<Played> kept = new ArrayList<>();
        for (Played played : fresh) {
            if (played.note().startTick() >= cut) {
                kept.add(played);
            }
        }
        for (Played played : fresh) {
            NoteEvent note = played.note();
            long end = note.startTick() + note.durationTicks();
            if (note.startTick() < cut && end > cut) {
                long next = end;
                for (Played other : kept) {
                    NoteEvent later = other.note();
                    if (later.channel() == note.channel() && later.pitch() == note.pitch()
                            && later.startTick() < next) {
                        next = later.startTick();
                    }
                }
                if (next > cut) {
                    kept.add(new Played(retimed(note, cut, next - cut, barTicks), played.why()));
                }
            }
        }
        notes.addAll(kept);
    }

    private static NoteEvent retimed(NoteEvent note, long start, long duration, long barTicks) {
        int beat = (int) ((start % barTicks) / TimeBase.PPQ) + 1;
        return new NoteEvent(note.id(), start, duration, note.pitch(), note.pitchName(), note.velocity(),
                note.channel(), note.layer(), note.bar(), Integer.toString(beat), note.duration(),
                note.seedPath(), note.motif());
    }

    public static Performance slice(Performance full, int from, int to) {
        List<Played> notes = new ArrayList<>();
        List<ControlEvent> controls = new ArrayList<>();
        long barTicks = full.score().barTicks();
        long start = (from - 1L) * barTicks;
        long end = to * barTicks;
        for (ControlEvent control : full.score().controls()) {
            if (control.startTick() >= start && control.startTick() < end) {
                controls.add(control);
            }
        }
        for (Played played : full.notes()) {
            if (played.note().bar() >= from && played.note().bar() <= to) {
                notes.add(played);
            }
        }
        List<NoteEvent> eventsNotes = new ArrayList<>();
        for (Played played : notes) {
            eventsNotes.add(played.note());
        }
        List<BarInfo> bars = new ArrayList<>();
        for (BarInfo bar : full.bars()) {
            if (bar.bar() >= from && bar.bar() <= to) {
                bars.add(bar);
            }
        }
        int bpm = bars.isEmpty() ? full.score().bpm() : bars.get(0).bpm();
        Score score = new Score(controls, eventsNotes, bpm, full.score().numerator(), full.score().denominator(), to);
        return new Performance(score, notes, full.transitions(), bars, full.overlays());
    }

    /** Later control at the same bar and beat replaces an earlier one. An overlay does not replace a state. */
    public static List<Theme.Control> merge(List<Theme.Control> base, List<Theme.Control> extra) {
        Map<String, Theme.Control> byTime = new LinkedHashMap<>();
        for (Theme.Control control : base) {
            byTime.put(controlKey(control), control);
        }
        for (Theme.Control control : extra) {
            byTime.put(controlKey(control), control);
        }
        List<Theme.Control> merged = new ArrayList<>(byTime.values());
        merged.sort(Comparator.comparingInt(Theme.Control::bar).thenComparingInt(Theme.Control::beat));
        return merged;
    }

    public static ThemeIssue unknown(Theme theme, List<Theme.Control> controls) {
        for (Theme.Control control : controls) {
            if (control.hasState() && theme.state(control.state()) == null) {
                return new ThemeIssue("THEME_UNKNOWN_STATE", "/timeline",
                        "unknown state " + control.state(),
                        "Use a state declared under states, such as " + theme.firstState() + ".");
            }
            if (control.hasOverlay() && theme.overlay(control.overlay()) == null) {
                return new ThemeIssue("THEME_UNKNOWN_OVERLAY", "/timeline",
                        "unknown overlay " + control.overlay(),
                        "Declare " + control.overlay() + " under overlays.");
            }
        }
        return null;
    }

    private static String controlKey(Theme.Control control) {
        if (control.hasOverlay() && !control.hasState()) {
            return "o:" + control.bar() + ":" + control.beat() + ":" + control.overlay();
        }
        return "s:" + control.bar() + ":" + control.beat();
    }

    private record Scheduled(Theme.Control control, long request, long applied, int appliedBar, Quantize quantize) {
    }

    private record OverlayCue(String id, double level, int appliedBar) {
    }

    static final class Ids {
        long next = 1;

        void start(int bar) {
            next = bar * 1000L;
        }

        long take() {
            return next++;
        }
    }
}
