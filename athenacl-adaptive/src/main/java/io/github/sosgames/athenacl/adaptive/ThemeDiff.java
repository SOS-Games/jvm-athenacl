// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.motif.hook.HookSettings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Semantic diff of two themes after the preset has been merged in.
 * One line per leaf that differs. Paths look like {@code /states/combat/key}.
 * Map order is fixed here so two runs print the same lines.
 */
public final class ThemeDiff {
    private ThemeDiff() {
    }

    public record Change(String kind, String path, String from, String to) {
        public String line() {
            if ("changed".equals(kind)) {
                return "changed=" + path + " from=" + from + " to=" + to;
            }
            if ("added".equals(kind)) {
                return "added=" + path;
            }
            return "removed=" + path;
        }
    }

    public static List<Change> compare(Theme left, Theme right) {
        List<Change> changes = new ArrayList<>();
        scalar(changes, "/id", left.id(), right.id());
        scalar(changes, "/preset", left.preset(), right.preset());
        scalar(changes, "/bars", left.bars(), right.bars());
        scalar(changes, "/bpm", left.bpm(), right.bpm());
        scalar(changes, "/meter", left.numerator() + "/" + left.denominator(),
                right.numerator() + "/" + right.denominator());
        scalar(changes, "/motif.every", left.motifEvery(), right.motifEvery());
        scalar(changes, "/harmony.phraseBars", left.phraseBars(), right.phraseBars());
        scalar(changes, "/clock.frameMs", left.frameMs(), right.frameMs());
        scalar(changes, "/instruments.kits", String.join(",", left.kits()), String.join(",", right.kits()));
        scalar(changes, "/transitions/defaults/quantize", left.defaultQuantize().id(), right.defaultQuantize().id());
        hold(changes, left.hold(), right.hold());
        states(changes, left.states(), right.states());
        overlays(changes, left.overlays(), right.overlays());
        rules(changes, left.rules(), right.rules());
        hooks(changes, left.hooks(), right.hooks());
        loudness(changes, left.loudness(), right.loudness());
        return changes;
    }

    public static String text(List<Change> changes) {
        if (changes.isEmpty()) {
            return "identical=true";
        }
        StringBuilder text = new StringBuilder();
        for (Change change : changes) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(change.line());
        }
        return text.toString();
    }

    private static void states(List<Change> changes, Map<String, Theme.StateDef> left, Map<String, Theme.StateDef> right) {
        for (String id : union(left.keySet(), right.keySet())) {
            Theme.StateDef a = left.get(id);
            Theme.StateDef b = right.get(id);
            String path = "/states/" + id;
            if (a == null) {
                changes.add(new Change("added", path, "", ""));
                continue;
            }
            if (b == null) {
                changes.add(new Change("removed", path, "", ""));
                continue;
            }
            scalar(changes, path + "/key", a.key().name(), b.key().name());
            scalar(changes, path + "/tempo", a.tempo(), b.tempo());
            scalar(changes, path + "/layers", String.join(",", a.layers()), String.join(",", b.layers()));
            scalar(changes, path + "/meter", a.meter() == null ? "" : a.meter(), b.meter() == null ? "" : b.meter());
            scalar(changes, path + "/stinger", a.stinger() == null ? "" : a.stinger(),
                    b.stinger() == null ? "" : b.stinger());
            for (String layer : union(a.programs().keySet(), b.programs().keySet())) {
                scalar(changes, path + "/programs/" + layer,
                        a.programs().containsKey(layer) ? Integer.toString(a.programs().get(layer)) : "",
                        b.programs().containsKey(layer) ? Integer.toString(b.programs().get(layer)) : "");
            }
            for (String layer : union(a.instruments().keySet(), b.instruments().keySet())) {
                scalar(changes, path + "/instruments/" + layer,
                        a.instruments().getOrDefault(layer, ""),
                        b.instruments().getOrDefault(layer, ""));
            }
        }
    }

    private static void overlays(List<Change> changes, Map<String, Theme.OverlayDef> left,
                                 Map<String, Theme.OverlayDef> right) {
        for (String id : union(left.keySet(), right.keySet())) {
            String path = "/overlays/" + id;
            Theme.OverlayDef a = left.get(id);
            Theme.OverlayDef b = right.get(id);
            if (a == null) {
                changes.add(new Change("added", path, "", ""));
            } else if (b == null) {
                changes.add(new Change("removed", path, "", ""));
            } else {
                scalar(changes, path + "/layers", String.join(",", a.layers()), String.join(",", b.layers()));
                scalar(changes, path + "/loudness", a.loudness(), b.loudness());
            }
        }
    }

    private static void rules(List<Change> changes, List<Theme.Rule> left, List<Theme.Rule> right) {
        int n = Math.max(left.size(), right.size());
        for (int i = 0; i < n; i++) {
            String path = "/transitions/rules/" + i;
            Theme.Rule a = i < left.size() ? left.get(i) : null;
            Theme.Rule b = i < right.size() ? right.get(i) : null;
            if (a == null) {
                changes.add(new Change("added", path, "", ""));
                continue;
            }
            if (b == null) {
                changes.add(new Change("removed", path, "", ""));
                continue;
            }
            scalar(changes, path + "/from", a.from(), b.from());
            scalar(changes, path + "/to", String.join(",", a.to()), String.join(",", b.to()));
            scalar(changes, path + "/quantize", a.quantize().id(), b.quantize().id());
            scalar(changes, path + "/retract", a.retract(), b.retract());
            scalar(changes, path + "/bridge", a.bridge(), b.bridge());
            scalar(changes, path + "/crossfadeBars", a.crossfadeBars(), b.crossfadeBars());
            scalar(changes, path + "/keyPolicy", a.keyPolicy(), b.keyPolicy());
        }
    }

    private static void hooks(List<Change> changes, HookSettings left, HookSettings right) {
        if (left == null && right == null) {
            return;
        }
        if (left == null) {
            changes.add(new Change("added", "/hooks", "", ""));
            return;
        }
        if (right == null) {
            changes.add(new Change("removed", "/hooks", "", ""));
            return;
        }
        scalar(changes, "/hooks/candidates", left.candidates(), right.candidates());
        scalar(changes, "/hooks/cells", String.join(",", left.cells()), String.join(",", right.cells()));
    }

    private static void loudness(List<Change> changes, LoudnessContour left, LoudnessContour right) {
        if (left == null && right == null) {
            return;
        }
        if (left == null) {
            changes.add(new Change("added", "/loudness", "", ""));
            return;
        }
        if (right == null) {
            changes.add(new Change("removed", "/loudness", "", ""));
            return;
        }
        scalar(changes, "/loudness/reference", left.reference(), right.reference());
        scalar(changes, "/loudness/maxSpread", left.maxSpread(), right.maxSpread());
        scalar(changes, "/loudness/maxStep", left.maxStep(), right.maxStep());
        Set<String> names = new TreeSet<>();
        names.addAll(left.offsets().keySet());
        names.addAll(right.offsets().keySet());
        for (String state : names) {
            scalar(changes, "/loudness/" + state, left.offset(state), right.offset(state));
        }
    }

    private static void hold(List<Change> changes, Theme.Hold left, Theme.Hold right) {
        String a = left == null ? "" : left.state() + "@" + left.intensity();
        String b = right == null ? "" : right.state() + "@" + right.intensity();
        scalar(changes, "/hold", a, b);
    }

    private static void scalar(List<Change> changes, String path, Object left, Object right) {
        String a = left == null ? "" : left.toString();
        String b = right == null ? "" : right.toString();
        if (a.equals(b)) {
            return;
        }
        if (a.isEmpty()) {
            changes.add(new Change("added", path, "", b));
        } else if (b.isEmpty()) {
            changes.add(new Change("removed", path, a, ""));
        } else {
            changes.add(new Change("changed", path, a, b));
        }
    }

    private static List<String> union(Set<String> left, Set<String> right) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.addAll(left);
        ids.addAll(right);
        return List.copyOf(ids);
    }
}
