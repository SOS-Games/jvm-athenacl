// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.util.List;
import java.util.Locale;

/**
 * Soundbanks this tool knows how to install. Each entry is pinned by size and SHA-256.
 * FreePats banks are published as archives; {@code archiveName} is set for those, and the
 * size and hash beside them are the extracted SF2. GeneralUser is fetched from a pinned
 * commit, not from schristiancollins.com.
 */
public final class SoundbankCatalog {
    public static final Entry GENERAL_USER = direct(
            "generaluser",
            "GeneralUser-GS.sf2",
            32_319_396L,
            "9575028c7a1f589f5770fccc8cff2734566af40cd26ed836944e9a5152688cfe",
            "https://github.com/mrbumpy409/GeneralUser-GS/raw/6f2014e815237de02e26e793f8c66c796ba66db5/GeneralUser-GS.sf2",
            "LicenseRef-GeneralUser-GS-2.0",
            "licenses/GeneralUser-GS-LICENSE.txt");

    /** MuseScore_General v0.2 SF2 from the OSUOSL mirror, until this project hosts its own copy. */
    public static final Entry MUSESCORE = direct(
            "musescore-general",
            "MuseScore_General.sf2",
            215_614_036L,
            "ee51d2c4b1525e70f19a45909c4fd7a2e26d91d115fa89dbf5a6bc413d8b9bf3",
            "https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf2",
            "MIT",
            "licenses/MuseScore_General_License.md");

    /** FreePats Upright Piano KW, CC0-1.0. The pin is the full SF2, not the small edition. */
    public static final Entry FREEPATS_UPRIGHT = archived(
            "freepats-upright-kw",
            "UprightPianoKW-20220221.sf2",
            57_377_848L,
            "d9f5157720963671906727ca2e12b3293fd822c831bb0de477dd1c5f3ad37108",
            "https://freepats.zenvoid.org/Piano/UprightPianoKW/UprightPianoKW-SF2-20220221.7z",
            "CC0-1.0",
            "licenses/FreePats-UprightPianoKW-CC0.txt",
            "UprightPianoKW-SF2-20220221.7z",
            28_832_466L,
            "17c084c6e4205233dc49b34e4bc44a9b2d7c7a2c02b04729ecda77079b07c826");

    /** FreePats Spanish classical guitar, CC0-1.0. */
    public static final Entry FREEPATS_GUITAR = archived(
            "freepats-spanish-guitar",
            "SpanishClassicalGuitar-20190618.sf2",
            19_822_546L,
            "0d4c08ea8c1c8924b3829084b0c8778f6d6b906f331e44fde51bc159f76b03b9",
            "https://freepats.zenvoid.org/Guitar/SpanishClassicalGuitar/SpanishClassicalGuitar-SF2-20190618.7z",
            "CC0-1.0",
            "licenses/FreePats-SpanishClassicalGuitar-CC0.txt",
            "SpanishClassicalGuitar-SF2-20190618.7z",
            9_990_750L,
            "8c192f1fc640a553199a4a43a17ae12354a6a05e3d6aad79bd43b5d29314ff51");

    /**
     * FreePats YDP Grand, CC-BY-3.0. Optional. The samples are Zenph Studios recordings
     * for OLPC; the SoundFont is by Roberto for FreePats. Keep the attribution.
     */
    public static final Entry FREEPATS_YDP = archived(
            "freepats-ydp-grand",
            "YDP-GrandPiano-20160804.sf2",
            118_398_836L,
            "8757076aecf80abdad1e8f8f6168370399b06f94481796986e6e75ceca09ad21",
            "https://freepats.zenvoid.org/Piano/YDP-GrandPiano/YDP-GrandPiano-SF2-20160804.tar.bz2",
            "CC-BY-3.0",
            "licenses/FreePats-YDP-Grand-CC-BY-3.0.txt",
            "YDP-GrandPiano-SF2-20160804.tar.bz2",
            36_737_684L,
            "d243dc3e182a60df2a16e92828c1821cf3eb5748b45e2e2bdcfa9cf7af056026");

    private SoundbankCatalog() {
    }

    public static List<Entry> all() {
        return List.of(GENERAL_USER, MUSESCORE, FREEPATS_UPRIGHT, FREEPATS_GUITAR, FREEPATS_YDP);
    }

    public static String knownIds() {
        StringBuilder text = new StringBuilder();
        for (Entry entry : all()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(entry.id());
        }
        return text.toString();
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

    /** {@code archiveName} is null when the URL is the SF2 itself. */
    public record Entry(
            String id,
            String fileName,
            long size,
            String sha256,
            String url,
            String license,
            String licenseFile,
            String archiveName,
            long archiveSize,
            String archiveSha256) {

        public boolean archived() {
            return archiveName != null && !archiveName.isBlank();
        }

        /** Env var suffix for an override URL. Hyphens become underscores. */
        public String envKey() {
            return "ATHENACL_SOUNDBANK_" + id.toUpperCase(Locale.ROOT).replace('-', '_') + "_URL";
        }
    }

    private static Entry direct(String id, String fileName, long size, String sha256, String url,
                                String license, String licenseFile) {
        return new Entry(id, fileName, size, sha256, url, license, licenseFile, null, 0L, null);
    }

    private static Entry archived(String id, String fileName, long size, String sha256, String url,
                                  String license, String licenseFile, String archiveName,
                                  long archiveSize, String archiveSha256) {
        return new Entry(id, fileName, size, sha256, url, license, licenseFile,
                archiveName, archiveSize, archiveSha256);
    }
}
