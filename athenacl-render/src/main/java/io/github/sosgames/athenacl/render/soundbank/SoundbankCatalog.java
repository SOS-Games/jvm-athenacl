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
            "https://github.com/mrbumpy409/GeneralUser-GS/raw/6f2014e815237de02e26e793f8c66c796ba66db5/GeneralUser-GS.sf2");

    private SoundbankCatalog() {
    }

    public static List<Entry> all() {
        return List.of(GENERAL_USER);
    }

    public static Entry byId(String id) {
        if (id == null) {
            return null;
        }
        for (Entry entry : all()) {
            if (entry.id().equalsIgnoreCase(id)) {
                return entry;
            }
        }
        return null;
    }

    public record Entry(String id, String fileName, long size, String sha256, String url) {
    }
}
