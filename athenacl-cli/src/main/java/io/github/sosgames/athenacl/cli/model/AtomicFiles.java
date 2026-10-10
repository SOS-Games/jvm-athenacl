// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Writes a file by filling a temp file in the same folder and renaming it into place.
 * If the destination already holds the same bytes, the file and its modified time stay
 * as they are ({@code changed=false}). Dry-run returns that plan and creates nothing,
 * including parent folders.
 */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public record WritePlan(Path path, int bytes, boolean changed, boolean dryRun) {
    }

    public static WritePlan write(Path path, byte[] bytes, boolean dryRun) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        boolean changed = !sameBytes(target, bytes);
        if (!dryRun && changed) {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = Files.createTempFile(parent == null ? Path.of(".") : parent, ".athenacl-", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    channel.write(ByteBuffer.wrap(bytes));
                    channel.force(true);
                }
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException | UnsupportedOperationException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        return new WritePlan(target, bytes.length, changed, dryRun);
    }

    private static boolean sameBytes(Path target, byte[] bytes) throws IOException {
        if (!Files.isRegularFile(target)) {
            return false;
        }
        return Arrays.equals(bytes, Files.readAllBytes(target));
    }
}
