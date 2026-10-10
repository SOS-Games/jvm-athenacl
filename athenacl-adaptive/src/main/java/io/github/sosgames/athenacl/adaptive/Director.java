// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline director. It walks the musical clock from bar 1, applies state changes on the
 * grid their rule names, and asks the stock layers for notes. The same seed and the same
 * controls always build the same score. Nothing here reads the wall clock.
 */
public final class Director {
    private Director() {
    }

    public record Played(NoteEvent note, String why) {
    }

    public record Transition(String from, String to, long requestTick, long appliedTick, String quantize) {
    }

    public record BarInfo(int bar, String state, double intensity, String key, int bpm) {
    }

    public record Performance(Score score, List<Played> notes, List<Transition> transitions, List<BarInfo> bars) {
        public String stateList() {
            List<String> names = new ArrayList<>();
            for (BarInfo bar : bars) {
                if (!names.contains(bar.state())) {
                    names.add(bar.state());
                }
            }
            return String.join(",", names);
        }
    }

    public static Performance perform(Theme theme, List<Theme.Control> controls, long seed) {
        long barTicks = theme.barTicks();
        List<Scheduled> scheduled = new ArrayList<>();
        for (Theme.Control control : controls) {
            long request = MusicalClock.tick(control.bar(), control.beat(), barTicks);
            Quantize quantize = theme.quantizeTo(control.state());
            long applied = MusicalClock.quantize(request, quantize, barTicks);
            int appliedBar = (int) (applied / barTicks) + 1;
            scheduled.add(new Scheduled(control, request, applied, appliedBar, quantize));
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
        String stateId = null;
        double target = theme.hold() == null ? 0.3 : theme.hold().intensity();
        int lastTempo = -1;
        int leadDegree = -1;
        int held = 0;
        int cursor = 0;
        for (int bar = 1; bar <= theme.bars(); bar++) {
            boolean changed = false;
            while (cursor < scheduled.size() && scheduled.get(cursor).appliedBar == bar) {
                Scheduled next = scheduled.get(cursor++);
                if (stateId != null && !stateId.equals(next.control.state())) {
                    transitions.add(new Transition(
                            stateId, next.control.state(), next.request, next.applied, next.quantize.id()));
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
            int every = theme.motifEvery();
            int phraseBar = held / every;
            boolean playMotif = held % every == 0;
            Theme.StateDef state = theme.state(stateId);
            double smoothed = intensity.apply(target, changed);
            if (state.tempo() != lastTempo) {
                events.add(new ControlEvent.Tempo((bar - 1L) * barTicks, state.tempo()));
                lastTempo = state.tempo();
            }
            BarInfo info = new BarInfo(bar, stateId, smoothed, state.key().name(), state.tempo());
            bars.add(info);
            ids.start(bar);
            long start = (bar - 1L) * barTicks;
            if (changed) {
                for (String layer : state.layers()) {
                    events.add(new ControlEvent.ProgramChange(start, StockLayers.channel(layer),
                            StockLayers.program(state, layer)));
                }
            }
            if (changed && state.stinger() != null) {
                Theme.StingerDef stinger = theme.stingers().get(state.stinger());
                if (stinger != null) {
                    StockLayers.stinger(theme, state, info, stinger, start, seed, ids, notes);
                }
            }
            boolean drums = intensity.drums(smoothed);
            leadDegree = StockLayers.bar(theme, state, info, start, seed, ids, notes, leadDegree, drums,
                    phraseBar, playMotif);
        }
        List<NoteEvent> eventsNotes = new ArrayList<>();
        for (Played played : notes) {
            eventsNotes.add(played.note());
        }
        int bpm = bars.isEmpty() ? theme.bpm() : bars.get(0).bpm();
        Score score = new Score(events, eventsNotes, bpm, theme.numerator(), theme.denominator(), theme.bars());
        return new Performance(score, List.copyOf(notes), List.copyOf(transitions), List.copyOf(bars));
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
        return new Performance(score, notes, full.transitions(), bars);
    }

    /** Later control at the same bar and beat replaces an earlier one. */
    public static List<Theme.Control> merge(List<Theme.Control> base, List<Theme.Control> extra) {
        Map<String, Theme.Control> byTime = new LinkedHashMap<>();
        for (Theme.Control control : base) {
            byTime.put(control.bar() + ":" + control.beat(), control);
        }
        for (Theme.Control control : extra) {
            byTime.put(control.bar() + ":" + control.beat(), control);
        }
        List<Theme.Control> merged = new ArrayList<>(byTime.values());
        merged.sort(Comparator.comparingInt(Theme.Control::bar).thenComparingInt(Theme.Control::beat));
        return merged;
    }

    public static ThemeIssue unknown(Theme theme, List<Theme.Control> controls) {
        for (Theme.Control control : controls) {
            if (theme.state(control.state()) == null) {
                return new ThemeIssue("THEME_UNKNOWN_STATE", "/timeline",
                        "unknown state " + control.state(),
                        "Use a state declared under states, such as " + theme.firstState() + ".");
            }
        }
        return null;
    }

    private record Scheduled(Theme.Control control, long request, long applied, int appliedBar, Quantize quantize) {
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
