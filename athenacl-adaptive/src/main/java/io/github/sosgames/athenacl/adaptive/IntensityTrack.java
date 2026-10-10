// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

/**
 * Intensity moves halfway toward the target each bar, and snaps when the state changes.
 * Drums use a wide switch: they turn on at 0.50 and stay on until the value falls below 0.35.
 */
public final class IntensityTrack {
    private double smoothed;
    private boolean started;
    private boolean drumsOn;

    public double apply(double target, boolean stateChanged) {
        if (!started || stateChanged) {
            smoothed = target;
            started = true;
        } else {
            smoothed += (target - smoothed) * 0.5;
        }
        return smoothed;
    }

    public boolean drums(double intensity) {
        if (drumsOn) {
            if (intensity < 0.35) {
                drumsOn = false;
            }
        } else if (intensity >= 0.50) {
            drumsOn = true;
        }
        return drumsOn;
    }

    public double smoothed() {
        return smoothed;
    }
}
