// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import java.util.List;

/** A kit file, license, or soundfont the command should reject. The first issue is the one printed. */
public final class KitException extends Exception {
    private final List<Issue> issues;

    public KitException(List<Issue> issues) {
        super(issues.isEmpty() ? "kit failed" : issues.get(0).message());
        this.issues = List.copyOf(issues);
    }

    public KitException(Issue issue) {
        this(List.of(issue));
    }

    public List<Issue> issues() {
        return issues;
    }

    public record Issue(String code, String path, String message, String fix) {
    }
}
