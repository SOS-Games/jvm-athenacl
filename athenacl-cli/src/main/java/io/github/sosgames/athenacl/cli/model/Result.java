// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.util.List;
import java.util.Map;

/**
 * The one JSON document a command prints when {@code --json} is set.
 * {@code schema} is a versioned id such as {@code athenacl.result.version/1}.
 * {@code meta.durationMs} is wall-clock time and is not part of any musical hash.
 */
public record Result(
        String schema,
        boolean ok,
        String command,
        Object data,
        List<Issue> warnings,
        List<Issue> errors,
        Map<String, String> meta) {
}
