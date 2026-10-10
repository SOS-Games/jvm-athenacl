// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One structured problem or warning. {@code path} is an argv slot ({@code argv[1]}) or,
 * later, a JSON pointer into a theme file. {@code fix} is a short hint an agent can act on.
 */
public record Issue(
        String code,
        Severity severity,
        String path,
        String message,
        String fix,
        @JsonInclude(JsonInclude.Include.NON_NULL) String docRef) {
}
