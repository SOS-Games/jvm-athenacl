// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/**
 * The only random stream generation is allowed to use. {@code fork} is keyed by the scope
 * numbers, so the same seed and the same scope always continue the same way.
 */
public interface RandomSource {
    double nextDouble();

    int nextInt(int bound);

    RandomSource fork(long... scope);

    /** Path such as {@code 0/melody/bar1/note#3}. Wall-clock time never appears in it. */
    String seedPath();
}
