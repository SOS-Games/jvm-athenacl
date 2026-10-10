// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

/**
 * Process status for every athenacl command. 0, 1, and 2 match the jvm-mw debug CLI.
 * 3 through 7 are for validation and checks, which that CLI only signals by throwing.
 */
public enum ExitCode {
    OK(0),
    INTERNAL(1),
    USAGE(2),
    INVALID_INPUT(3),
    NOT_FOUND(4),
    RUNTIME(5),
    CHECK_FAILED(6),
    ENVIRONMENT(7);

    private final int number;

    ExitCode(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }
}
