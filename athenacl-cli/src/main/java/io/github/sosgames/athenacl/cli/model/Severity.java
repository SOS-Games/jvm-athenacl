// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

/** How serious an {@link Issue} is. Warnings still exit 0 unless {@code --strict} is set. */
public enum Severity {
    WARNING,
    ERROR
}
