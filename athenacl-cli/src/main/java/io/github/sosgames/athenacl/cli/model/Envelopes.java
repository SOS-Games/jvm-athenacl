// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the Result envelope, including the meta map every JSON command shares. */
public final class Envelopes {
    private Envelopes() {
    }

    public static Result make(String schema, boolean ok, String command, Object data,
                              List<Issue> warnings, List<Issue> errors, long startedNanos) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("version", BuildVersion.PROJECT);
        meta.put("seed", "0");
        long elapsed = System.nanoTime() - startedNanos;
        long ms = elapsed <= 0 ? 0 : elapsed / 1_000_000L;
        meta.put("durationMs", Long.toString(ms));
        return new Result(schema, ok, command, data, warnings, errors, meta);
    }
}
