// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.events.Observer;
import io.github.sosgames.athenacl.events.Provenance;
import io.github.sosgames.athenacl.events.Score;

import java.util.Map;

/**
 * {@code --debug off} is the normal render. {@code provenance} and {@code trace} write a
 * sidecar next to the event log. The sidecar is not hashed. If the observer changes the
 * event log, the render exits 6.
 */
final class DebugMode {
    static final String OFF = "off";

    private DebugMode() {
    }

    static boolean known(String mode) {
        return OFF.equals(mode) || "provenance".equals(mode) || "trace".equals(mode);
    }

    static Integer reject(AgentCli parent, String mode) {
        if (known(mode)) {
            return null;
        }
        return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--debug",
                "unknown debug mode " + mode,
                "Use off, provenance, or trace.");
    }

    /** Null when the observer matches, or the process should stop with this code. */
    static Integer observer(AgentCli parent, String mode, Score score) {
        if (OFF.equals(mode)) {
            return null;
        }
        if (Observer.matches(score)) {
            return null;
        }
        return parent.fail(ExitCode.CHECK_FAILED, "OBSERVER", "--debug",
                "debug output changed the event log",
                "Provenance and traces stay out of events.jsonl. The hash has to match --debug off.");
    }

    static void sidecars(Map<String, byte[]> bodies, String mode, Score score) {
        if ("provenance".equals(mode)) {
            bodies.put("provenance", Provenance.notes(score));
        } else if ("trace".equals(mode)) {
            bodies.put("trace", Provenance.trace(score));
        }
    }

    static void fields(Map<String, String> fields, Map<String, Object> data, String mode, int notes) {
        if (OFF.equals(mode)) {
            return;
        }
        fields.put("debug", mode);
        fields.put(mode, Integer.toString(notes));
        if (data != null) {
            data.put("debug", mode);
            data.put(mode, notes);
        }
    }
}
