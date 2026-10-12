// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The first 55 athenaCL commands, through {@code TCe}. The source lists 104
 * command names. This half is the path, texture, and clone commands that come
 * first. The check also writes one project file and reads it back.
 */
public final class CommandsA {
    public record Check(int pass, int fail, List<String> failed) {
    }

    private static final List<String> NAMES = List.of(
            "w", "c", "r", "cmd", "pypath", "bug", "clear", "py", "shell", "help", "quit",
            "PIals", "PIn", "PIv", "PIe", "PIdf", "PIls", "PIcp", "PIrm", "PIo", "PImv", "PIret", "PIrot", "PIslc", "PIh",
            "TMo", "TMls",
            "TPls", "TPv", "TPmap", "TPe",
            "TIn", "TIo", "TImute", "TImode", "TImidi", "TIv", "TIe", "TIls", "TIrm", "TIcp", "TImv", "TIdoc", "TIals", "TImap",
            "TEe", "TEv", "TEmap", "TEmidi",
            "TCn", "TCv", "TCo", "TCmute", "TCls", "TCe");

    private CommandsA() {
    }

    public static List<String> names() {
        return NAMES;
    }

    public static Check check(Path names, Path xml) throws IOException {
        int pass = 0;
        List<String> failed = new ArrayList<>();
        List<String> expect = new ArrayList<>();
        for (String line : Files.readAllLines(names, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                expect.add(line);
            }
        }
        if (expect.equals(NAMES)) {
            pass += NAMES.size();
        } else {
            failed.add("commands");
        }
        String got = ProjectXml.sample();
        String file = Files.readString(xml, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (file.endsWith("\n")) {
            file = file.substring(0, file.length() - 1);
        }
        Map<String, Object> read = ProjectXml.read(got);
        if (got.equals(file) && leavesMatch(read)) {
            pass++;
        } else {
            failed.add("xml");
        }
        return new Check(pass, failed.size(), List.copyOf(failed));
    }

    @SuppressWarnings("unchecked")
    private static boolean leavesMatch(Map<String, Object> root) {
        Map<String, Object> athena = (Map<String, Object>) root.get("athena");
        Map<String, Object> path = (Map<String, Object>) ((Map<String, Object>) root.get("paths")).get("a");
        Map<String, Object> voice = (Map<String, Object>) ((Map<String, Object>) path.get("voiceLib")).get("v1");
        Map<String, Object> texture = (Map<String, Object>) ((Map<String, Object>) root.get("textures")).get("lg");
        return "c-major".equals(athena.get("name"))
                && "120".equals(athena.get("tempo"))
                && "(0, 4, 7)".equals(path.get("pcs"))
                && "60".equals(voice.get("ps"))
                && "0.7 & 1".equals(texture.get("amp"))
                && "0".equals(texture.get("inst"));
    }
}
