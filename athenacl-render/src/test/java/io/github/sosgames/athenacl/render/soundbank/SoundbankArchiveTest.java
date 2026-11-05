// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundbankArchiveTest {
    private static final byte[] BODY = "RIFF-sfbk-fixture".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path dir;

    @Test
    void extractsTheNamedSf2From7zZipAndTarBz2() throws Exception {
        Path seven = dir.resolve("bank.7z");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry entry = out.createArchiveEntry(seven.toFile(), "nested/Piano.sf2");
            out.putArchiveEntry(entry);
            out.write(BODY);
            out.closeArchiveEntry();
        }
        Path zip = dir.resolve("bank.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("folder/Piano.sf2"));
            out.write(BODY);
            out.closeEntry();
        }
        Path tar = dir.resolve("bank.tar.bz2");
        try (OutputStream file = Files.newOutputStream(tar);
             OutputStream bzip = new BZip2CompressorOutputStream(file);
             TarArchiveOutputStream archive = new TarArchiveOutputStream(bzip)) {
            TarArchiveEntry entry = new TarArchiveEntry("YDP/Piano.sf2");
            entry.setSize(BODY.length);
            archive.putArchiveEntry(entry);
            archive.write(BODY);
            archive.closeArchiveEntry();
            archive.finish();
        }
        assertExtract(seven);
        assertExtract(zip);
        assertExtract(tar);
    }

    @Test
    void downloadPartSuffixStillSelectsTheType() throws Exception {
        Path seven = dir.resolve("bank.7z.part");
        try (SevenZOutputFile out = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry entry = out.createArchiveEntry(seven.toFile(), "Piano.sf2");
            out.putArchiveEntry(entry);
            out.write(BODY);
            out.closeArchiveEntry();
        }
        Path dest = dir.resolve("Piano.sf2");
        SoundbankArchive.extract(seven, dest, "Piano.sf2");
        assertArrayEquals(BODY, Files.readAllBytes(dest));
    }

    @Test
    void missingNameFails() throws Exception {
        Path zip = dir.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write(BODY);
            out.closeEntry();
        }
        Path dest = dir.resolve("Piano.sf2");
        assertThrows(java.io.IOException.class, () -> SoundbankArchive.extract(zip, dest, "Piano.sf2"));
        assertFalse(Files.isRegularFile(dest));
    }

    private void assertExtract(Path archive) throws Exception {
        Path dest = dir.resolve(archive.getFileName() + ".sf2");
        SoundbankArchive.extract(archive, dest, "Piano.sf2");
        assertTrue(Files.isRegularFile(dest));
        assertArrayEquals(BODY, Files.readAllBytes(dest));
    }
}
