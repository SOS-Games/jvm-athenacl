// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

/**
 * A path script that this slice cannot run. {@code code} is the error token the CLI prints.
 */
public final class ScriptException extends RuntimeException {
    private final String code;
    private final String path;
    private final String fix;

    public ScriptException(String code, String path, String message, String fix) {
        super(message);
        this.code = code;
        this.path = path;
        this.fix = fix;
    }

    public String code() {
        return code;
    }

    public String path() {
        return path;
    }

    public String fix() {
        return fix;
    }
}
