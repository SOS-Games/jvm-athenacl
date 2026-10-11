// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

/**
 * A soundbank file the synth must not open. SF3 is the usual case: Gervill would play the
 * compressed samples as noise instead of raising an error.
 */
public final class SoundbankRejected extends Exception {
    private final String code;
    private final String path;
    private final String fix;

    public SoundbankRejected(String code, String path, String message, String fix) {
        super(message);
        this.code = code;
        this.path = path;
        this.fix = fix;
    }

    public String code() {
        return code;
    }

    public String path() {
        return path;
    }

    public String fix() {
        return fix;
    }

    public static SoundbankRejected sf3(String path) {
        return new SoundbankRejected(
                "SOUNDBANK_SF3_UNSUPPORTED",
                path,
                "SF3 soundfonts are not supported",
                "use the SF2 edition or convert (e.g. Polyphone)");
    }
}
