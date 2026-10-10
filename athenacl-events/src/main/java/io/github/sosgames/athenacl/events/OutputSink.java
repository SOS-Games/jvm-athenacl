// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/** Something that receives notes in score order. The text dump is one. Audio is another. */
public interface OutputSink {
    OutputSink NOOP = note -> {
    };

    void note(NoteEvent note);
}
