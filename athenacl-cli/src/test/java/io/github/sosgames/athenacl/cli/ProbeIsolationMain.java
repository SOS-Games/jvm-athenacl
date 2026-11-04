// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli;

import io.github.sosgames.athenacl.cli.agent.AgentCli;

import java.nio.file.Files;
import java.nio.file.Path;

/** Runs one command, then exits 8 if the probe renderer class was loaded. */
public final class ProbeIsolationMain {
    private ProbeIsolationMain() {
    }

    public static void main(String[] args) throws Exception {
        Path marker = Path.of("build", "probe-renderer-loaded");
        Files.deleteIfExists(marker);
        int code = AgentCli.execute(args, System.out, System.err);
        if (Files.exists(marker)) {
            System.err.println("probe renderer was loaded");
            System.exit(8);
        }
        System.exit(code);
    }
}
