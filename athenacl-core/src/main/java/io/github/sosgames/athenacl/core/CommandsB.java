// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The rest of the athenaCL command names, from {@code TCcp} through {@code menu}.
 * The source assigns a name 104 times. {@code AUpc} is assigned twice, so this
 * half is the 48 names that are not already in the first 55.
 */
public final class CommandsB {
    public record Check(int pass, int fail, List<String> failed) {
    }

    private static final List<String> NAMES = List.of(
            "TCcp", "TCmap", "TCdoc", "TCrm", "TCals", "TTls", "TTo",
            "EOo", "EOrm", "EOls",
            "EMo", "EMls", "EMv", "EMi",
            "ELn", "ELw", "ELr", "ELh", "ELv", "ELauto",
            "AOl", "AOw", "AOmg", "AOals", "AOrm",
            "APdlg", "APgfx", "APcurs", "APr", "APwid", "APdir", "APea", "APa",
            "AHls", "AHrm", "AHexe",
            "AUdoc", "AUup", "AUlog", "AUsys", "AUbeat", "AUpc", "AUmg", "AUma", "AUca",
            "TMsd", "TPsd", "menu");

    private CommandsB() {
    }

    public static List<String> names() {
        return NAMES;
    }

    public static Check check(Path names) throws IOException {
        List<String> expect = new ArrayList<>();
        for (String line : Files.readAllLines(names, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                expect.add(line);
            }
        }
        if (expect.equals(NAMES)) {
            return new Check(NAMES.size(), 0, List.of());
        }
        return new Check(0, 1, List.of("commands"));
    }
}
