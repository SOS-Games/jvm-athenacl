// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pulls one {@code .sf2} out of the archive {@code soundbank install} downloaded.
 * FreePats publishes 7z and tar.bz2. zip, tar.gz, and tar.xz are accepted the same way.
 * The extracted name is the file's own name, so a folder around it does not matter.
 */
public final class SoundbankArchive {
    private SoundbankArchive() {
    }

    public static void extract(Path archive, Path dest, String fileName) throws IOException {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".part")) {
            name = name.substring(0, name.length() - ".part".length());
        }
        if (name.endsWith(".7z")) {
            extract7z(archive, dest, fileName);
        } else if (name.endsWith(".zip")) {
            extractZip(archive, dest, fileName);
        } else if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2")) {
            extractTar(archive, dest, fileName, Compression.BZIP2);
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            extractTar(archive, dest, fileName, Compression.GZIP);
        } else if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
            extractTar(archive, dest, fileName, Compression.XZ);
        } else if (name.endsWith(".tar")) {
            extractTar(archive, dest, fileName, Compression.NONE);
        } else {
            throw new IOException("unsupported soundbank archive " + archive.getFileName());
        }
        if (!Files.isRegularFile(dest)) {
            throw new IOException("archive did not contain " + fileName);
        }
    }

    private static void extract7z(Path archive, Path dest, String fileName) throws IOException {
        try (SevenZFile seven = SevenZFile.builder().setPath(archive).get()) {
            SevenZArchiveEntry entry;
            while ((entry = seven.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (named(entry.getName(), fileName)) {
                    copy(buffer -> seven.read(buffer), dest);
                    return;
                }
                drain(buffer -> seven.read(buffer));
            }
        }
    }

    private static void extractZip(Path archive, Path dest, String fileName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && named(entry.getName(), fileName)) {
                    copy(zip, dest);
                    return;
                }
            }
        }
    }

    private static void extractTar(Path archive, Path dest, String fileName, Compression compression)
            throws IOException {
        InputStream raw = Files.newInputStream(archive);
        try (InputStream decoded = compression.wrap(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(decoded)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isDirectory() && named(entry.getName(), fileName)) {
                    copy(tar, dest);
                    return;
                }
            }
        }
    }

    private static boolean named(String entryName, String fileName) {
        if (entryName == null) {
            return false;
        }
        String slash = entryName.replace('\\', '/');
        int cut = slash.lastIndexOf('/');
        String base = cut >= 0 ? slash.substring(cut + 1) : slash;
        return base.equals(fileName);
    }

    private static void copy(InputStream in, Path dest) throws IOException {
        Files.createDirectories(dest.getParent() == null ? Path.of(".") : dest.getParent());
        try (OutputStream out = Files.newOutputStream(dest)) {
            in.transferTo(out);
        }
    }

    private static void copy(ByteSource source, Path dest) throws IOException {
        Files.createDirectories(dest.getParent() == null ? Path.of(".") : dest.getParent());
        try (OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = source.read(buffer)) >= 0) {
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static void drain(ByteSource source) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        while (source.read(buffer) >= 0) {
            // A solid 7z entry has to be read before the next one will decode.
        }
    }

    @FunctionalInterface
    private interface ByteSource {
        int read(byte[] buffer) throws IOException;
    }

    private enum Compression {
        NONE, GZIP, BZIP2, XZ;

        InputStream wrap(InputStream in) throws IOException {
            return switch (this) {
                case NONE -> in;
                case GZIP -> new GzipCompressorInputStream(in);
                case BZIP2 -> new BZip2CompressorInputStream(in);
                case XZ -> new XZCompressorInputStream(in);
            };
        }
    }
}
