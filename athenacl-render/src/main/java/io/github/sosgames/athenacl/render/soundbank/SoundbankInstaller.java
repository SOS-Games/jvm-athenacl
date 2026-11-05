// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Downloads a catalog soundbank into the cache and checks its SHA-256.
 * An archived bank (FreePats 7z or tar.bz2) is unpacked, then the extracted SF2 is checked
 * against its own pin. {@code --dry-run} reports the plan and does not create the cache.
 * A second install of the same bytes reports {@code status=present changed=false}.
 * SF3 is rejected after the hash matches, before the file is kept.
 */
public final class SoundbankInstaller {
    private SoundbankInstaller() {
    }

    public record Plan(
            String id,
            String action,
            String status,
            long size,
            String sha256,
            Path path,
            boolean changed,
            boolean dryRun) {
    }

    public static Plan install(String id, Path cache, boolean dryRun) throws IOException, SoundbankRejected {
        SoundbankCatalog.Entry entry = SoundbankCatalog.byId(id);
        if (entry == null) {
            throw new IllegalArgumentException(id == null ? "" : id);
        }
        Path dest = cache.resolve(entry.fileName());
        boolean present = matches(dest, entry);
        if (present) {
            if (!dryRun) {
                copyLicense(entry, cache);
            }
            return new Plan(entry.id(), "present", "present", entry.size(), entry.sha256(),
                    dest, false, dryRun);
        }
        if (dryRun) {
            return new Plan(entry.id(), "download", "absent", entry.size(), entry.sha256(),
                    dest, true, true);
        }
        Files.createDirectories(cache);
        Path archivePart = entry.archived() ? cache.resolve(entry.archiveName() + ".part") : null;
        Path bodyPart = cache.resolve(entry.fileName() + ".part");
        try {
            Path fetched = archivePart != null ? archivePart : bodyPart;
            download(entry, fetched);
            if (archivePart != null) {
                if (!archiveMatches(archivePart, entry)) {
                    throw new IOException("downloaded " + entry.id() + " archive did not match the pinned size and sha256");
                }
                SoundbankArchive.extract(archivePart, bodyPart, entry.fileName());
                Files.deleteIfExists(archivePart);
            }
            if (!matches(bodyPart, entry)) {
                throw new IOException("downloaded " + entry.id() + " did not match the pinned size and sha256");
            }
            if (SoundbankSniff.inspect(bodyPart) == SoundbankSniff.Kind.SF3) {
                throw SoundbankRejected.sf3(entry.id());
            }
            move(bodyPart, dest);
            copyLicense(entry, cache);
            return new Plan(entry.id(), "download", "installed", entry.size(), entry.sha256(),
                    dest, true, false);
        } catch (IOException | SoundbankRejected | RuntimeException e) {
            if (archivePart != null) {
                Files.deleteIfExists(archivePart);
            }
            Files.deleteIfExists(bodyPart);
            throw e;
        }
    }

    public static boolean matches(Path file, SoundbankCatalog.Entry entry) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) != entry.size()) {
            return false;
        }
        return sha256(file).equals(entry.sha256());
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean archiveMatches(Path file, SoundbankCatalog.Entry entry) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) != entry.archiveSize()) {
            return false;
        }
        return sha256(file).equalsIgnoreCase(entry.archiveSha256());
    }

    private static void download(SoundbankCatalog.Entry entry, Path part) throws IOException {
        Files.deleteIfExists(part);
        String url = url(entry);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(20))
                .header("User-Agent", "athenacl-soundbank-install")
                .GET()
                .build();
        HttpResponse<Path> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(part));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(part);
            throw new IOException("download interrupted", e);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw new IOException("could not download " + entry.id() + " from " + url, e);
        }
        if (response.statusCode() != 200) {
            Files.deleteIfExists(part);
            throw new IOException("download of " + entry.id() + " returned HTTP " + response.statusCode());
        }
    }

    private static void move(Path part, Path dest) throws IOException {
        try {
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Copies the repo license next to the installed bank. A missing path is left alone. */
    private static void copyLicense(SoundbankCatalog.Entry entry, Path cache) throws IOException {
        if (entry.licenseFile() == null || entry.licenseFile().isBlank()) {
            return;
        }
        Path source = Path.of(entry.licenseFile());
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.createDirectories(cache);
        Files.copy(source, cache.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String url(SoundbankCatalog.Entry entry) {
        String prop = System.getProperty("athenacl.soundbank." + entry.id() + ".url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(entry.envKey());
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return entry.url();
    }
}
