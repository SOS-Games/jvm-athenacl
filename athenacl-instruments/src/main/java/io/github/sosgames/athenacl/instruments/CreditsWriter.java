// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes CREDITS.md for the soundfonts a theme's kits actually use, and copies each license file
 * next to it. Sources that no instrument resolved to are left out.
 */
public final class CreditsWriter {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private CreditsWriter() {
    }

    public record Written(int sources, List<String> licenses, Path file) {
    }

    public static Written write(List<Path> themes, Path out, Path cache, boolean dryRun)
            throws IOException, KitException {
        Map<String, Playable> sources = new LinkedHashMap<>();
        for (Path theme : themes) {
            JsonNode root = YAML.readTree(Files.readString(theme, StandardCharsets.UTF_8));
            List<String> kits = new ArrayList<>();
            JsonNode listed = root.path("instruments").path("kits");
            if (listed.isArray() && !listed.isEmpty()) {
                listed.forEach(item -> kits.add(item.asText()));
            } else {
                kits.add("gm-lite");
            }
            for (String id : kits) {
                KitSession session = KitSession.open(Kits.find(id), cache, false);
                for (Playable playable : session.instruments()) {
                    sources.putIfAbsent(playable.sourceKey(), playable);
                }
            }
        }
        StringBuilder text = new StringBuilder();
        text.append("# Credits\n\n");
        List<String> licenses = new ArrayList<>();
        for (Playable source : sources.values()) {
            if (!licenses.contains(source.license())) {
                licenses.add(source.license());
            }
            text.append("## ").append(source.attribution()).append("\n\n");
            text.append("Source: ").append(source.sourceKey()).append("\n\n");
            text.append("License: ").append(source.license()).append("\n\n");
        }
        Path file = out.resolve("CREDITS.md");
        if (!dryRun) {
            Files.createDirectories(out);
            Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
            for (Playable source : sources.values()) {
                if (source.licenseFile() == null || source.licenseFile().isBlank()) {
                    continue;
                }
                Path license = Path.of(source.licenseFile());
                if (Files.isRegularFile(license)) {
                    Files.copy(license, out.resolve(license.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return new Written(sources.size(), licenses, file);
    }
}
