// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

/**
 * Holds a theme change until the next bar line. The next kit is loaded on another thread.
 * {@link #preloaded()} marks that load finished. {@link #poll(int)} applies it on a later bar,
 * and not before {@code earliestBar}.
 */
public final class ThemeSwitch {
    private final int earliestBar;
    private String active;
    private String pending;
    private boolean ready;
    private int seenBar = -1;

    public ThemeSwitch(String active, int earliestBar) {
        this.active = active;
        this.earliestBar = earliestBar;
    }

    public synchronized void arm(String next) {
        pending = next;
        ready = false;
        seenBar = -1;
    }

    public synchronized void preloaded() {
        ready = true;
    }

    public synchronized String kit() {
        return active;
    }

    /** The theme that should be audible at this bar. */
    public synchronized String poll(int bar) {
        if (!ready || pending == null) {
            return active;
        }
        if (seenBar < 0) {
            seenBar = bar;
        }
        if (bar <= seenBar || bar < earliestBar) {
            return active;
        }
        active = pending;
        pending = null;
        ready = false;
        seenBar = -1;
        return active;
    }
}
