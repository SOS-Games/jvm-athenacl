// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

/**
 * Calls the measured-analysis entry points by name. The command classes do not mention those
 * types, so a normal command does not load the probe renderer.
 */
public final class Experimental {
    private static final String TYPE =
            "io.github.sosgames.athenacl.instruments.analysis.experimental.MeasuredKit";

    private Experimental() {
    }

    public static String call(String method, Class<?>[] types, Object[] args) throws Exception {
        Class<?> cls = Class.forName(TYPE);
        try {
            return (String) cls.getMethod(method, types).invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }
}
