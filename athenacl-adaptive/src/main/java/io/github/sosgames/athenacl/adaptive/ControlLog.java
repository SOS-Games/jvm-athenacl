// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The control log is the timeline a render actually used. Replay reads it back and
 * does not look at the wall clock, so the same file produces the same event-log hash.
 */
public final class ControlLog {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ControlLog() {
    }

    public record Log(String theme, long seed, int bars, List<Theme.Control> controls, Double reference) {
    }

    public static byte[] encode(String theme, long seed, int bars, List<Theme.Control> controls) {
        return encode(theme, seed, bars, controls, null);
    }

    public static byte[] encode(String theme, long seed, int bars, List<Theme.Control> controls, Double reference) {
        StringBuilder text = new StringBuilder();
        text.append("{\"type\":\"header\",\"schema\":\"athenacl.control/1\",\"theme\":\"")
                .append(escape(theme))
                .append("\",\"seed\":").append(seed)
                .append(",\"bars\":").append(bars);
        if (reference != null && Double.isFinite(reference)) {
            text.append(",\"reference\":").append(reference);
        }
        text.append("}\n");
        for (Theme.Control control : controls) {
            if (control.hasState()) {
                text.append("{\"type\":\"state\",\"bar\":").append(control.bar())
                        .append(",\"beat\":").append(control.beat())
                        .append(",\"state\":\"").append(escape(control.state()))
                        .append("\",\"intensity\":").append(Themes.number(control.intensity()))
                        .append("}\n");
            }
            if (control.hasOverlay()) {
                text.append("{\"type\":\"overlay\",\"bar\":").append(control.bar())
                        .append(",\"beat\":").append(control.beat())
                        .append(",\"overlay\":\"").append(escape(control.overlay()))
                        .append("\",\"level\":").append(Themes.number(control.overlayLevel()))
                        .append("}\n");
            }
            if (control.hasTheme()) {
                text.append("{\"type\":\"theme\",\"bar\":").append(control.bar())
                        .append(",\"beat\":").append(control.beat())
                        .append(",\"theme\":\"").append(escape(control.themePath()))
                        .append("\"}\n");
            }
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Log read(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        String theme = null;
        long seed = 0;
        int bars = 0;
        Double reference = null;
        List<Theme.Control> controls = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = JSON.readTree(line);
            String type = node.path("type").asText("");
            if (type.equals("header")) {
                theme = node.path("theme").asText("");
                seed = node.path("seed").asLong();
                bars = node.path("bars").asInt();
                if (node.has("reference")) {
                    reference = node.path("reference").asDouble();
                }
            } else if (type.equals("state")) {
                controls.add(new Theme.Control(
                        node.path("bar").asInt(1),
                        node.path("beat").asInt(1),
                        node.path("state").asText(""),
                        Themes.intensity(node.path("intensity").asDouble())));
            } else if (type.equals("overlay")) {
                controls.add(new Theme.Control(
                        node.path("bar").asInt(1),
                        node.path("beat").asInt(1),
                        "",
                        0,
                        node.path("overlay").asText(""),
                        Themes.intensity(node.path("level").asDouble())));
            } else if (type.equals("theme")) {
                controls.add(new Theme.Control(
                        node.path("bar").asInt(1),
                        node.path("beat").asInt(1),
                        "",
                        0,
                        "",
                        0,
                        node.path("theme").asText("")));
            }
        }
        if (theme == null || theme.isBlank()) {
            throw new IOException("control log has no header");
        }
        return new Log(theme, seed, bars, List.copyOf(controls), reference);
    }

    public static List<Theme.Control> readTimeline(Path path) throws IOException {
        JsonNode root = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
        if (!root.isArray()) {
            throw new IOException("timeline must be a JSON array");
        }
        List<Theme.Control> controls = new ArrayList<>();
        for (JsonNode node : root) {
            controls.add(new Theme.Control(
                    node.path("bar").asInt(1),
                    node.path("beat").asInt(1),
                    node.path("state").asText(""),
                    node.has("intensity") ? Themes.intensity(node.get("intensity").asDouble()) : 0.3));
        }
        return controls;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
