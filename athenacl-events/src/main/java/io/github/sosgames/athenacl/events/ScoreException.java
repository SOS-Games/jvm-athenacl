// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/** The note list or event log could not be read. The CLI turns this into exit 3. */
public final class ScoreException extends Exception {
    private final String path;
    private final String fix;

    public ScoreException(String path, String message, String fix) {
        super(message);
        this.path = path;
        this.fix = fix;
    }

    public String path() {
        return path;
    }

    public String fix() {
        return fix;
    }
}
