// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes and reads an athenaCL project file. Three sections sit under
 * {@code athenaObject}: athena, paths, and textures. Keys are written in
 * alphabetical order. A {@code &} in a value is stored as {@code &amp;}.
 * A nested voice library is tagged {@code pi}.
 */
public final class ProjectXml {
    private ProjectXml() {
    }

    public static String sample() {
        Map<String, Object> athena = map("name", "c-major", "tempo", "120");
        Map<String, Object> path = map("pcs", "(0, 4, 7)", "voiceLib", map("v1", map("ps", "60")));
        Map<String, Object> texture = map("amp", "0.7 & 1", "inst", "0");
        StringBuilder text = new StringBuilder();
        text.append("<?xml version=\"1.0\"?>");
        text.append("\n<athenaObject>");
        text.append(section("athenaObject", "athena", athena, List.of()));
        text.append(section("athenaObject", "paths", map("a", path),
                levels(null, null, "pi", null, List.of("voiceLib", "pv"))));
        text.append(section("athenaObject", "textures", map("lg", texture),
                levels(null, null, List.of("textureLib", "ti", "cloneLib", "tc"))));
        text.append("\n</athenaObject>");
        return text.toString();
    }

    public static Map<String, Object> read(String xml) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            return element(document.getDocumentElement());
        } catch (Exception e) {
            throw new IllegalArgumentException("project xml", e);
        }
    }

    private static String section(String parent, String name, Map<String, Object> data, List<Object> manifest) {
        return write(parent, name, data, 1, manifest);
    }

    private static String write(String parent, String name, Map<String, Object> data, int indent, List<Object> manifest) {
        Object level = manifest.isEmpty() ? null : manifest.get(0);
        StringBuilder text = new StringBuilder();
        text.append(tag(parent, name, level, indent, true));
        Map<String, Object> sorted = new TreeMap<>(data);
        List<Object> rest = manifest.size() <= 1 ? List.of() : manifest.subList(1, manifest.size());
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> child) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) child;
                text.append(write(name, entry.getKey(), nested, indent + 1, rest));
            } else {
                String tab = "\t".repeat(indent + 1);
                text.append("\n").append(tab)
                        .append("<attr key=\"").append(entry.getKey())
                        .append("\" value=\"").append(scrub(String.valueOf(entry.getValue())))
                        .append("\"/>");
            }
        }
        text.append(tag(parent, name, level, indent, false));
        return text.toString();
    }

    private static String tag(String parent, String name, Object level, int indent, boolean open) {
        String tab = "\t".repeat(indent);
        String tag = name;
        String named = "";
        if (level instanceof String all) {
            tag = all;
            named = " name=\"" + name + "\"";
        } else if (level instanceof List<?> pairs && pairs.contains(parent)) {
            int at = pairs.indexOf(parent);
            if (at + 1 < pairs.size()) {
                tag = String.valueOf(pairs.get(at + 1));
                named = " name=\"" + name + "\"";
            }
        }
        if (!open) {
            return "\n" + tab + "</" + tag + ">";
        }
        return "\n" + tab + "<" + tag + named + ">";
    }

    private static String scrub(String value) {
        return value.replace("&", "&amp;").replace(">", "&gt;").replace("<", "&lt;");
    }

    private static Map<String, Object> element(org.w3c.dom.Element node) {
        Map<String, Object> data = new LinkedHashMap<>();
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof org.w3c.dom.Element child)) {
                continue;
            }
            String key = child.getAttribute("key");
            String value = child.getAttribute("value");
            if (!key.isEmpty() && !value.isEmpty() || child.getTagName().equals("attr")) {
                data.put(key, value);
                continue;
            }
            String name = child.getAttribute("name");
            if (name.isEmpty()) {
                name = child.getAttribute("category");
            }
            if (name.isEmpty()) {
                name = child.getTagName();
            }
            data.put(name, element(child));
        }
        return data;
    }

    private static List<Object> levels(Object... items) {
        return java.util.Arrays.asList(items);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            data.put((String) pairs[i], pairs[i + 1]);
        }
        return data;
    }
}
