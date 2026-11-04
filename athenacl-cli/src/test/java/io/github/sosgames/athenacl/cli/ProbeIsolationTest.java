// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProbeIsolationTest {
    @Test
    void ensembleDescribeAndResolveDoNotLoadTheProbe() throws Exception {
        assertEquals(0, run("ensemble", "select", "--genre", "fantasy", "--state", "sneaking", "--seed", "1"));
        assertEquals(0, run("kit", "describe", "flute"));
        assertEquals(0, run("theme", "resolve", "--genre", "fantasy", "--state", "sneaking", "--seed", "1"));
        assertEquals(0, run("kit", "lint", "samples/themes/lint-clean.yaml", "--all-states"));
    }

    private static int run(String... args) throws Exception {
        String javaName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("io.github.sosgames.athenacl.cli.ProbeIsolationMain");
        cmd.addAll(List.of(args));
        Process process = new ProcessBuilder(cmd).directory(Path.of("").toAbsolutePath().toFile()).start();
        String out = new String(process.getInputStream().readAllBytes());
        String err = new String(process.getErrorStream().readAllBytes());
        int code = process.waitFor();
        if (code != 0) {
            System.err.println(out);
            System.err.println(err);
        }
        return code;
    }
}
