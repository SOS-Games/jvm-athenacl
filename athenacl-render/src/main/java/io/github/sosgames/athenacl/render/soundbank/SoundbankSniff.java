// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Reads a soundfont header before Gervill opens it. SF3 is a RIFF bank whose {@code ifil}
 * version is 3, or whose sample headers set the compression bit. Gervill would play those
 * samples as noise, so callers reject the file first.
 */
public final class SoundbankSniff {
    public enum Kind {
        SF2, DLS, SF3, UNREADABLE
    }

    private SoundbankSniff() {
    }

    public static Kind inspect(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sf3")) {
            return Kind.SF3;
        }
        try (RandomAccessFile in = new RandomAccessFile(path.toFile(), "r")) {
            if (in.length() < 12) {
                return Kind.UNREADABLE;
            }
            byte[] head = new byte[12];
            in.readFully(head);
            if (!"RIFF".equals(ascii(head, 0))) {
                return Kind.UNREADABLE;
            }
            String form = ascii(head, 8);
            if ("DLS ".equals(form)) {
                return Kind.DLS;
            }
            if (!"sfbk".equals(form)) {
                return Kind.UNREADABLE;
            }
            long pos = 12;
            boolean compressed = false;
            int major = 2;
            while (pos + 8 <= in.length()) {
                in.seek(pos);
                byte[] chunk = new byte[8];
                in.readFully(chunk);
                String id = ascii(chunk, 0);
                long size = le32(chunk, 4) & 0xffffffffL;
                if ("LIST".equals(id) && size >= 4) {
                    byte[] type = new byte[4];
                    in.readFully(type);
                    if ("INFO".equals(ascii(type, 0))) {
                        major = ifilMajor(in, pos + 12, size - 4, major);
                    }
                } else if ("shdr".equals(id)) {
                    compressed = compressed || compressedSamples(in, size);
                }
                pos += 8 + size + (size & 1);
            }
            if (major >= 3 || compressed) {
                return Kind.SF3;
            }
            return Kind.SF2;
        }
    }

    private static int ifilMajor(RandomAccessFile in, long start, long size, int current) throws IOException {
        long pos = start;
        long end = start + size;
        while (pos + 8 <= end) {
            in.seek(pos);
            byte[] chunk = new byte[8];
            in.readFully(chunk);
            long chunkSize = le32(chunk, 4) & 0xffffffffL;
            if ("ifil".equals(ascii(chunk, 0)) && chunkSize >= 2) {
                byte[] version = new byte[2];
                in.readFully(version);
                return version[0] & 0xff | (version[1] & 0xff) << 8;
            }
            pos += 8 + chunkSize + (chunkSize & 1);
        }
        return current;
    }

    /** SF2 sample records are 46 bytes. The type field is the last two, and bit 0x10 means compressed. */
    private static boolean compressedSamples(RandomAccessFile in, long size) throws IOException {
        long records = size / 46;
        byte[] type = new byte[2];
        for (long i = 0; i < records; i++) {
            in.seek(in.getFilePointer() + 44);
            in.readFully(type);
            int value = type[0] & 0xff | (type[1] & 0xff) << 8;
            if ((value & 0x10) != 0) {
                return true;
            }
        }
        return false;
    }

    private static int le32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | (bytes[offset + 1] & 0xff) << 8
                | (bytes[offset + 2] & 0xff) << 16
                | (bytes[offset + 3] & 0xff) << 24;
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
