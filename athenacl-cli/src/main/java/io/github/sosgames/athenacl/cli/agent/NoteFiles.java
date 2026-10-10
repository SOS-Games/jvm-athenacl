// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.events.NoteLists;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads a note-list YAML file or an events.jsonl log. */
final class NoteFiles {
    private NoteFiles() {
    }

    static Score yaml(Path path, long seed) throws IOException, ScoreException {
        return NoteLists.load(path, seed);
    }

    static Score events(Path path) throws IOException, ScoreException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return EventLog.read(text);
    }
}
