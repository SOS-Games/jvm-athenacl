// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.util.List;

/** A theme file that failed validation. The issues carry JSON pointers and fix hints. */
public final class ThemeException extends Exception {
    private final List<ThemeIssue> issues;

    public ThemeException(List<ThemeIssue> issues) {
        super(issues.isEmpty() ? "theme is invalid" : issues.get(0).message());
        this.issues = List.copyOf(issues);
    }

    public List<ThemeIssue> issues() {
        return issues;
    }
}
