// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

/** One theme problem. {@code path} is a JSON pointer, such as {@code /transitions/rules/0/to}. */
public record ThemeIssue(String code, String path, String message, String fix) {
}
