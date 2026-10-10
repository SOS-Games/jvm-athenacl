// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.PrintStream;

/** Writes exactly one JSON document to stdout. Logs never go through here. */
public final class JsonOut {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private JsonOut() {
    }

    public static void write(PrintStream out, Object value) {
        try {
            out.write(MAPPER.writeValueAsBytes(value));
            out.write('\n');
            out.flush();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not write JSON", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not write JSON", e);
        }
    }
}
