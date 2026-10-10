// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli;

import io.github.sosgames.athenacl.cli.agent.CommandCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerCheckTest {
    @Test
    void agentsGuideNamesEveryCommand() throws Exception {
        String guide = Files.readString(repo().resolve("AGENTS.md"));
        for (String name : CommandCatalog.names()) {
            assertTrue(guide.lines().anyMatch(line -> line.matches("\\|\\s*" + name + "\\s*\\|.*")),
                    "AGENTS.md command table is missing " + name);
        }
    }

    @Test
    void l0aChecksPass() {
        int code = LayerCheck.run(repo(), System.getProperty("java.class.path"), "L0a");
        assertEquals(0, code);
    }

    private static Path repo() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("settings.gradle"))
                    && Files.isRegularFile(dir.resolve("AGENTS.md"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Path.of("").toAbsolutePath());
    }
}
