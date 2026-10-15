// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns one probe's numbers into ok, caution, or bad for a {@link SoundClass}.
 * Each reason names the class rule that fired. An octave miss is not a reason.
 * A hot level is a loud note with a trim, not a failure.
 */
public final class ClassVerdict {
    /** Chorus and section detune sit under this. Beating past four times it is the flag. */
    static final double ENSEMBLE_ROUGHNESS = 400;
    static final double TUNED_PITCH_HARD = 100;
    static final double TUNED_PITCH_SOFT = 50;
    static final double ENSEMBLE_INHARMONIC = 0.55;

    private ClassVerdict() {
    }

    public record Decision(String verdict, List<String> reasons, List<String> soft, String note) {
        public Decision {
            reasons = List.copyOf(reasons);
            soft = List.copyOf(soft);
        }
    }

    public static Decision judge(String soundClass, double pitchCents, double inharmonic, double flatness,
                                 int clips, int clicks, double roughness, boolean died, double peakDb,
                                 Thresholds thresholds) {
        String cls = soundClass == null || soundClass.isBlank() ? SoundClass.SUSTAINED : soundClass;
        boolean noisy = flatness >= thresholds.flatness();
        boolean unpitched = SoundClass.UNPITCHED.equals(cls);
        boolean tuned = SoundClass.TUNED.equals(cls);
        boolean ensemble = SoundClass.ENSEMBLE.equals(cls);
        List<String> reasons = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        if (clips >= 8) {
            reasons.add(rule("clipping", cls));
        }
        if (!unpitched && !died && clicks >= thresholds.loopClickCount()) {
            reasons.add(rule("loopClicks", cls));
        }
        if (!unpitched && !tuned && !noisy && inharmonicFails(cls, inharmonic, roughness, thresholds)) {
            reasons.add(rule("inharmonicity", cls));
        }
        if (!unpitched && !noisy) {
            double hard = tuned ? TUNED_PITCH_HARD : thresholds.pitchHardCents();
            double softCents = tuned ? TUNED_PITCH_SOFT : thresholds.pitchSoftCents();
            double abs = Math.abs(pitchCents);
            if (abs > hard) {
                reasons.add(rule("pitch", cls));
            } else if (abs > softCents) {
                soft.add(rule("pitch", cls));
            }
        }
        String note = "";
        if (clips < 8 && Double.isFinite(peakDb) && peakDb > thresholds.clipDbfs() + 0.1) {
            double trim = thresholds.clipDbfs() - peakDb;
            note = String.format(Locale.US, "loud trim=%.1fdB", trim);
        }
        String verdict = !reasons.isEmpty() ? "bad" : !soft.isEmpty() ? "caution" : "ok";
        return new Decision(verdict, reasons, soft, note);
    }

    private static boolean inharmonicFails(String cls, double inharmonic, double roughness, Thresholds thresholds) {
        if (SoundClass.ENSEMBLE.equals(cls)) {
            return inharmonic >= ENSEMBLE_INHARMONIC && roughness > ENSEMBLE_ROUGHNESS * 4;
        }
        // A plucked string and a raw synth are stretched or filtered. Only a
        // series that does not fit any harmonic grid is a failure.
        double limit = thresholds.inharmonicEnergy();
        if (SoundClass.PERCUSSIVE.equals(cls)) {
            limit = 0.70;
        } else if (SoundClass.SYNTH.equals(cls)) {
            limit = 0.80;
        }
        return inharmonic >= limit;
    }

    static String rule(String code, String cls) {
        return code + "@class=" + cls;
    }
}
