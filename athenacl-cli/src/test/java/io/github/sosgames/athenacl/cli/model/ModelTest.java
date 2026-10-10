// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sosgames.athenacl.cli.agent.AgentCli;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {
    @AfterEach
    void resetProbe() {
        Platform.gervill = Platform::detectGervill;
    }

    @Test
    void strictPromotesAGervillWarning() throws Exception {
        Platform.gervill = () -> "absent";
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int loose = AgentCli.execute(new String[] {"version"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        assertEquals(0, loose);
        assertTrue(outBytes.toString(StandardCharsets.UTF_8).contains("gervill=absent"));
        assertTrue(errBytes.toString(StandardCharsets.UTF_8).contains("GERVILL_ABSENT"));

        outBytes.reset();
        errBytes.reset();
        int strict = AgentCli.execute(new String[] {"version", "--strict", "--json"},
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        assertEquals(3, strict);
        JsonNode tree = JsonOut.MAPPER.readTree(outBytes.toByteArray());
        assertFalse(tree.get("ok").asBoolean());
        assertEquals("GERVILL_ABSENT", tree.get("warnings").get(0).get("code").asText());
    }

    @Test
    void exitCodesMatchTheContract() {
        assertEquals(0, ExitCode.OK.number());
        assertEquals(1, ExitCode.INTERNAL.number());
        assertEquals(2, ExitCode.USAGE.number());
        assertEquals(3, ExitCode.INVALID_INPUT.number());
        assertEquals(4, ExitCode.NOT_FOUND.number());
        assertEquals(5, ExitCode.RUNTIME.number());
        assertEquals(6, ExitCode.CHECK_FAILED.number());
        assertEquals(7, ExitCode.ENVIRONMENT.number());
    }

    @Test
    void keyValueQuotesOnlyWhenNeeded() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("path", "build/a.json");
        fields.put("fix", "Run athenacl help");
        assertEquals("path=build/a.json fix=\"Run athenacl help\"", KeyValue.line(fields));
        assertEquals("error code=NOT_FOUND", KeyValue.record("error", Map.of("code", "NOT_FOUND")));
        assertEquals("\"say \\\"hi\\\"\"", KeyValue.formatValue("say \"hi\""));
    }

    @Test
    void suggestionsStayClose() {
        assertEquals("version", Suggestions.nearest("versio", List.of("help", "version", "schema")));
        assertNull(Suggestions.nearest("bogus", List.of("help", "version", "schema")));
    }

    @Test
    void schemasAdvertiseTheirOwnIds() {
        for (String id : SchemaRegistry.ids()) {
            assertEquals(id, SchemaRegistry.node(id).get("$id").asText());
        }
    }

    @Test
    void atomicWriteSkipsIdenticalBytesAndDryRunCreatesNothing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested/a.json");
        byte[] bytes = "abc\n".getBytes();
        AtomicFiles.WritePlan dry = AtomicFiles.write(file, bytes, true);
        assertTrue(dry.changed());
        assertTrue(dry.dryRun());
        assertFalse(Files.exists(dir.resolve("nested")));

        AtomicFiles.WritePlan wrote = AtomicFiles.write(file, bytes, false);
        assertTrue(wrote.changed());
        assertEquals("abc\n", Files.readString(file));

        FileTime stamp = FileTime.fromMillis(1_577_836_800_000L);
        Files.setLastModifiedTime(file, stamp);
        FileTime stored = Files.getLastModifiedTime(file);
        AtomicFiles.WritePlan again = AtomicFiles.write(file, bytes, false);
        assertFalse(again.changed());
        assertEquals(stored, Files.getLastModifiedTime(file));

        AtomicFiles.WritePlan changed = AtomicFiles.write(file, "xyz\n".getBytes(), false);
        assertTrue(changed.changed());
        assertEquals("xyz\n", Files.readString(file));
    }
}
