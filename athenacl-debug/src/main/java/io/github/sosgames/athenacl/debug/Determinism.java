// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.debug;

import java.util.function.Supplier;

/**
 * Renders the same inputs more than once and compares event-log hashes. Audio bytes are not
 * compared. They can change when the JDK's synth changes.
 */
public final class Determinism {
    private Determinism() {
    }

    public record Report(int runs, boolean identical, String hash) {
    }

    public static Report run(int runs, Supplier<String> hashOfOneRun) {
        String first = null;
        boolean identical = true;
        for (int i = 0; i < runs; i++) {
            String hash = hashOfOneRun.get();
            if (first == null) {
                first = hash;
            } else if (!first.equals(hash)) {
                identical = false;
            }
        }
        return new Report(runs, identical, first == null ? "" : first);
    }
}
