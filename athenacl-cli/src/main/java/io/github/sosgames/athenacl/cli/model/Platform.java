// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.util.function.Supplier;

/**
 * What this JDK can do, without opening a sound device. {@code version} reports Gervill as
 * present when the class is on the JDK image. A missing sound card is not an absence.
 */
public final class Platform {
    static Supplier<String> gervill = Platform::detectGervill;

    private Platform() {
    }

    public static String gervill() {
        String state = gervill.get();
        return "present".equals(state) ? "present" : "absent";
    }

    static String detectGervill() {
        try {
            Class.forName("com.sun.media.sound.SoftSynthesizer");
            return "present";
        } catch (Throwable ignored) {
            return "absent";
        }
    }
}
