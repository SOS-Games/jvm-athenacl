// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/**
 * Observer for a render. The default does nothing and must not change the event log.
 * Later layers add bar and scheduler callbacks here.
 */
public interface DebugSink {
    DebugSink NOOP = new DebugSink() {
    };

    default void note(NoteEvent note) {
    }

    default void warning(String code, String message) {
    }
}
