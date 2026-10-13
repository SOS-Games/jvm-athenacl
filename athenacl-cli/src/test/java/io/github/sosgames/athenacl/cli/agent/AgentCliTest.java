// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sosgames.athenacl.cli.model.JsonOut;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCliTest {
    @Test
    void helpListsEveryExample() {
        Run help = run("help");
        assertEquals(0, help.exit);
        assertTrue(help.err.isBlank());
        for (CommandCatalog.CommandInfo info : CommandCatalog.all()) {
            assertTrue(help.out.contains(info.name()), info.name());
            for (String example : info.examples()) {
                assertTrue(help.out.contains(example), example);
            }
        }
    }

    @Test
    void helpJsonIsOneDocument() throws Exception {
        Run help = run("help", "--json");
        assertEquals(0, help.exit);
        JsonNode tree = one(help.out);
        assertTrue(tree.get("data").get("commands").size() >= 3);
        assertEquals("0", tree.get("meta").get("seed").asText());
        assertEquals("0.1.0", tree.get("meta").get("version").asText());
    }

    @Test
    void jsonFlagWorksBeforeTheCommand() throws Exception {
        Run help = run("--json", "version");
        assertEquals(0, help.exit);
        assertEquals("version", one(help.out).get("command").asText());
    }

    @Test
    void unknownCommandExits2AndPrintsHelp() {
        Run run = run("bogus");
        assertEquals(2, run.exit);
        assertTrue(run.err.startsWith("unknown command: bogus"));
        assertTrue(run.out.contains("--args=\"help\""));
    }

    @Test
    void nearMissSuggestsTheCommand() {
        Run run = run("versio");
        assertEquals(2, run.exit);
        assertTrue(run.err.contains("Did you mean version?"));
    }

    @Test
    void versionLineIsStableWhenVerbose() {
        Run quiet = run("version");
        Run loud = run("version", "-vv");
        assertEquals(0, quiet.exit);
        assertEquals(0, loud.exit);
        assertEquals(quiet.out, loud.out);
        assertTrue(quiet.err.isBlank());
        assertTrue(loud.err.contains("TRACE athenacl.cli"));
        assertFalse(loud.out.contains("TRACE"));
        assertTrue(quiet.out.contains("version=0.1.0"));
        assertTrue(quiet.out.contains("gervill=present"));
    }

    @Test
    void logLevelOverridesVerbosity() {
        Run run = run("version", "-vv", "--log-level", "error");
        assertEquals(0, run.exit);
        assertTrue(run.err.isBlank());
    }

    @Test
    void badLogLevelIsUsage() {
        Run run = run("--log-level", "loud");
        assertEquals(2, run.exit);
        assertTrue(run.err.contains("unknown log level: loud"));
        assertTrue(run.out.contains("--args=\"help\""));
    }

    @Test
    void unknownFlagIsUsage() {
        Run run = run("version", "--nope");
        assertEquals(2, run.exit);
        assertTrue(run.out.contains("--args=\"help\""));
    }

    @Test
    void batchPrintsWhatEachCommandPrintsAlone(@TempDir Path dir) throws Exception {
        Run version = run("version");
        Run scale = run("describe", "scale", "D-dorian");
        Path file = dir.resolve("batch.jsonl");
        Files.writeString(file, """
                {"id":1,"cmd":"version"}
                {"id":2,"cmd":"describe","args":["scale","D-dorian"]}
                """, StandardCharsets.UTF_8);
        Run batch = run("batch", file.toString());
        assertEquals(0, batch.exit);
        assertTrue(batch.out.contains(version.out.trim()), batch.out);
        assertTrue(batch.out.contains(scale.out.trim()), batch.out);
        assertTrue(batch.err.isBlank(), batch.err);
    }

    @Test
    void missingSchemaIsNotFound() {
        Run run = run("schema", "athenacl.missing/1");
        assertEquals(4, run.exit);
        assertTrue(run.out.contains("error code=NOT_FOUND"));
        assertTrue(run.out.contains("path=argv[1]"));
        assertTrue(run.out.contains("fix="));
    }

    @Test
    void dryRunDoesNotCreateTheFile(@TempDir Path dir) {
        Path out = dir.resolve("out");
        Run run = run("schema", "athenacl.result.version/1", "--out", out.toString(), "--dry-run");
        assertEquals(0, run.exit);
        assertTrue(run.out.contains("dryRun=true"));
        assertTrue(run.out.contains("action=write"));
        assertFalse(Files.exists(out));
    }

    @Test
    void secondWriteKeepsTheFileTime(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Run first = run("schema", "athenacl.result.version/1", "--out", out.toString());
        assertEquals(0, first.exit);
        assertTrue(first.out.contains("changed=true"));
        Path file = out.resolve("athenacl.result.version_1.json");
        assertTrue(Files.isRegularFile(file));
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_577_836_800_000L));
        FileTime stored = Files.getLastModifiedTime(file);
        Run second = run("schema", "athenacl.result.version/1", "--out", out.toString());
        assertEquals(0, second.exit);
        assertTrue(second.out.contains("changed=false"));
        assertEquals(stored, Files.getLastModifiedTime(file));
    }

    private static JsonNode one(String json) throws Exception {
        try (var parser = JsonOut.MAPPER.getFactory().createParser(json)) {
            JsonNode tree = JsonOut.MAPPER.readTree(parser);
            if (parser.nextToken() != null) {
                throw new AssertionError("trailing json");
            }
            return tree;
        }
    }

    private static Run run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        int code = AgentCli.execute(args, out, err);
        return new Run(code, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exit, String out, String err) {
    }
}
