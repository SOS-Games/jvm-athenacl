// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Published JSON schemas shipped on the classpath. Ids look like
 * {@code athenacl.result.version/1}. The file name swaps the slash for an underscore.
 * These copies are written by hand for the skeleton; later layers can generate them
 * from records once there are enough types to drift.
 */
public final class SchemaRegistry {
    public static final List<String> IDS = List.of(
            "athenacl.result.help/1",
            "athenacl.result.version/1",
            "athenacl.result.schema/1",
            "athenacl.result.error/1",
            "athenacl.result.render/1",
            "athenacl.result.play/1",
            "athenacl.result.describe-music/1",
            "athenacl.result.determinism/1",
            "athenacl.result.bisect/1",
            "athenacl.result.diff/1",
            "athenacl.result.overhead/1",
            "athenacl.result.inspect/1",
            "athenacl.result.soundbank/1",
            "athenacl.result.instruments/1",
            "athenacl.result.analyze/1",
            "athenacl.result.check/1",
            "athenacl.result.scale/1",
            "athenacl.result.chord/1",
            "athenacl.result.progression/1",
            "athenacl.result.modulate/1",
            "athenacl.result.rhythm/1",
            "athenacl.result.poly/1",
            "athenacl.result.validate/1",
            "athenacl.result.explain/1",
            "athenacl.result.trace/1",
            "athenacl.result.bar/1",
            "athenacl.result.replay/1",
            "athenacl.result.preset/1",
            "athenacl.result.motif/1",
            "athenacl.result.melody/1",
            "athenacl.result.fuzz/1",
            "athenacl.result.adapt/1",
            "athenacl.result.instrument/1",
            "athenacl.result.kit/1",
            "athenacl.result.credits/1",
            "athenacl.result.compat/1",
            "athenacl.result.conformance/1",
            "athenacl.result.conformance-run/1",
            "athenacl.result.po/1",
            "athenacl.result.pos/1",
            "athenacl.result.textures/1",
            "athenacl.result.setclass/1",
            "athenacl.result.commands/1",
            "athenacl.result.prompt/1",
            "athenacl.result.athena/1");

    private SchemaRegistry() {
    }

    public static List<String> ids() {
        return IDS;
    }

    public static String fileName(String id) {
        return id.replace('/', '_') + ".json";
    }

    public static Optional<byte[]> bytes(String id) {
        if (!IDS.contains(id)) {
            return Optional.empty();
        }
        String resource = "/schema/" + fileName(id);
        try (InputStream in = SchemaRegistry.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n');
            if (!text.endsWith("\n")) {
                text = text + "\n";
            }
            return Optional.of(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + resource, e);
        }
    }

    public static JsonNode node(String id) {
        byte[] body = bytes(id).orElseThrow(() -> new IllegalArgumentException("no schema " + id));
        try {
            return JsonOut.MAPPER.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("schema is not JSON: " + id, e);
        }
    }
}
