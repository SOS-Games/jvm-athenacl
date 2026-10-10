// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

/**
 * When a state change is allowed to land. {@code bar} waits for the next bar line.
 * {@code beat} waits for the next quarter. {@code immediate} keeps the requested tick.
 */
public enum Quantize {
    BAR("bar"),
    BEAT("beat"),
    IMMEDIATE("immediate");

    private final String id;

    Quantize(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Quantize parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("missing quantize");
        }
        return switch (text.trim().toLowerCase()) {
            case "bar" -> BAR;
            case "beat" -> BEAT;
            case "immediate" -> IMMEDIATE;
            default -> throw new IllegalArgumentException("quantize " + text);
        };
    }
}
