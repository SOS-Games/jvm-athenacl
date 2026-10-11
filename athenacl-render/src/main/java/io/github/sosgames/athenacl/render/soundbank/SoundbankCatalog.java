// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.util.List;

/**
 * Soundbanks this tool knows how to install. GeneralUser GS is pinned by size and SHA-256.
 * The download URL is a pinned commit of the author's repository, used until this project
 * publishes its own copy. Do not point it at schristiancollins.com.
 */
public final class SoundbankCatalog {
    public static final Entry GENERAL_USER = new Entry(
            "generaluser",
            "GeneralUser-GS.sf2",
            32_319_396L,
            "9575028c7a1f589f5770fccc8cff2734566af40cd26ed836944e9a5152688cfe",
            "https://github.com/mrbumpy409/GeneralUser-GS/raw/6f2014e815237de02e26e793f8c66c796ba66db5/GeneralUser-GS.sf2",
            "LicenseRef-GeneralUser-GS-2.0");

    /** MuseScore_General v0.2 SF2 from the OSUOSL mirror, until this project hosts its own copy. */
    public static final Entry MUSESCORE = new Entry(
            "musescore-general",
            "MuseScore_General.sf2",
            215_614_036L,
            "ee51d2c4b1525e70f19a45909c4fd7a2e26d91d115fa89dbf5a6bc413d8b9bf3",
            "https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf2",
            "MIT");

    private SoundbankCatalog() {
    }

    public static List<Entry> all() {
        return List.of(GENERAL_USER, MUSESCORE);
    }

    public static Entry byId(String id) {
        if (id == null) {
            return null;
        }
        if (id.equalsIgnoreCase("musescore-general-0.2")) {
            return MUSESCORE;
        }
        for (Entry entry : all()) {
            if (entry.id().equalsIgnoreCase(id)) {
                return entry;
            }
        }
        return null;
    }

    public record Entry(String id, String fileName, long size, String sha256, String url, String license) {
    }
}
