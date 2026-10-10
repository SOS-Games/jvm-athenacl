// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.io.IOException;
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
import java.util.Locale;

/**
 * Downloads a catalog soundbank into the cache and checks its SHA-256.
 * {@code --dry-run} reports the plan and does not create the cache or the file.
 * A second install of the same bytes reports {@code status=present changed=false}.
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

    public static Plan install(String id, Path cache, boolean dryRun) throws IOException {
        SoundbankCatalog.Entry entry = SoundbankCatalog.byId(id);
        if (entry == null) {
            throw new IllegalArgumentException(id == null ? "" : id);
        }
        Path dest = cache.resolve(entry.fileName());
        boolean present = matches(dest, entry);
        if (present) {
            return new Plan(entry.id(), "present", "present", entry.size(), entry.sha256(),
                    dest, false, dryRun);
        }
        if (dryRun) {
            return new Plan(entry.id(), "download", "absent", entry.size(), entry.sha256(),
                    dest, true, true);
        }
        Files.createDirectories(cache);
        Path part = cache.resolve(entry.fileName() + ".part");
        Files.deleteIfExists(part);
        String url = url(entry);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "athenacl-soundbank-install")
                .GET()
                .build();
        HttpResponse<Path> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofFile(part));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("download interrupted", e);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw new IOException("could not download " + entry.id() + " from " + url, e);
        }
        if (response.statusCode() != 200) {
            Files.deleteIfExists(part);
            throw new IOException("download of " + entry.id() + " returned HTTP " + response.statusCode());
        }
        if (!matches(part, entry)) {
            Files.deleteIfExists(part);
            throw new IOException("downloaded " + entry.id() + " did not match the pinned size and sha256");
        }
        try {
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Plan(entry.id(), "download", "installed", entry.size(), entry.sha256(),
                dest, true, false);
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
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String url(SoundbankCatalog.Entry entry) {
        String prop = System.getProperty("athenacl.soundbank." + entry.id() + ".url");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("ATHENACL_SOUNDBANK_" + entry.id().toUpperCase(Locale.ROOT) + "_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return entry.url();
    }
}
