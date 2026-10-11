// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Which soundfont licenses a kit may use. Defaults allow MIT, CC0, CC-BY, and GeneralUser GS.
 * A license that is not on the allow or warn list is denied, and so is one that forbids
 * redistribution or commercial use. {@code athenacl.yaml} can replace the lists.
 */
public final class LicensePolicy {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private final Set<String> allow;
    private final Set<String> warn;
    private final boolean allowUnlicensed;

    private LicensePolicy(Set<String> allow, Set<String> warn, boolean allowUnlicensed) {
        this.allow = allow;
        this.warn = warn;
        this.allowUnlicensed = allowUnlicensed;
    }

    public static LicensePolicy load(boolean allowUnlicensed) throws IOException {
        Set<String> allow = new HashSet<>(Set.of(
                "MIT", "CC0-1.0", "CC-BY-3.0", "CC-BY-4.0", "LicenseRef-GeneralUser-GS-2.0"));
        Set<String> warn = new HashSet<>(Set.of(
                "LicenseRef-CC-Sampling-Plus-1.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0"));
        Path file = Path.of("athenacl.yaml");
        if (Files.isRegularFile(file)) {
            JsonNode root = YAML.readTree(Files.readString(file, StandardCharsets.UTF_8));
            JsonNode licenses = root.path("licenses");
            if (licenses.path("allow").isArray()) {
                allow.clear();
                licenses.path("allow").forEach(item -> allow.add(item.asText()));
            }
            if (licenses.path("warn").isArray()) {
                warn.clear();
                licenses.path("warn").forEach(item -> warn.add(item.asText()));
            }
        }
        return new LicensePolicy(allow, warn, allowUnlicensed);
    }

    public Verdict judge(Kit.License license) {
        if (license == null || license.spdx() == null || license.spdx().isBlank()) {
            return denied("license is missing");
        }
        if (!license.redistribute() || !license.commercial()) {
            return denied("license does not allow redistribution and commercial use");
        }
        if (allow.contains(license.spdx())) {
            return Verdict.ALLOW;
        }
        if (warn.contains(license.spdx())) {
            return Verdict.WARN;
        }
        return denied("license " + license.spdx() + " is not allowed");
    }

    private Verdict denied(String reason) {
        return allowUnlicensed ? Verdict.WARN : Verdict.DENY;
    }

    public enum Verdict {
        ALLOW, WARN, DENY
    }
}
