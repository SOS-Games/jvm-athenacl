// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.sosgames.athenacl.cli.model.JsonOut;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs {@code checks/L*.check} against the athenacl CLI in a child process.
 * One block is one invocation. Blocks are split by a line that is only {@code ---}.
 * Blank lines and {@code #} comments are ignored. A directive is {@code key: value},
 * or a bare flag such as {@code stderr-empty}. Unknown keys fail the run.
 * {@code -Player=L0a} (passed through as the third argument) selects one file.
 * {@code args} honors double quotes, so a scale name can contain a space.
 * The full key list lives in AGENTS.md under "Layer checks".
 */
public final class LayerCheck {
    private static final String MAIN = "io.github.sosgames.athenacl.cli.agent.AgentCli";
    private static final Set<String> KEYS = Set.of(
            "args", "exit",
            "stdout-contains", "stdout-regex", "stdout-not-contains", "stdout-empty", "stdout-same-as",
            "stderr-contains", "stderr-regex", "stderr-empty",
            "stdout-count", "capture", "stdout-contains-capture",
            "json-document", "json-array-nonempty", "json-equals", "json-regex",
            "file-exists", "file-absent", "delete-before");

    private LayerCheck() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: LayerCheck <repo> <classpath> [layer]");
            System.exit(1);
            return;
        }
        String layer = args.length > 2 ? args[2] : null;
        int code = run(Path.of(args[0]), args[1], layer);
        System.exit(code);
    }

    public static int run(Path root, String classpath, String layer) {
        Path checks = root.resolve("checks");
        List<Path> files;
        try {
            files = select(checks, layer);
        } catch (IOException e) {
            System.err.println("error code=IO_ERROR path=checks message=" + e.getMessage());
            return 1;
        }
        if (files.isEmpty()) {
            System.err.println("error code=NOT_FOUND path=layer message=no check file for " + layer);
            return 1;
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Map<String, String> captures = new LinkedHashMap<>();
        int total = 0;
        int failed = 0;
        try {
            for (Path file : files) {
                captures.clear();
                List<Block> blocks = parse(Files.readString(file, StandardCharsets.UTF_8));
                for (Block block : blocks) {
                    total++;
                    String problem = runBlock(root, classpath, pool, block, captures);
                    String label = file.getFileName() + " " + block.name;
                    if (problem == null) {
                        System.out.println("PASS " + label);
                    } else {
                        failed++;
                        System.out.println("FAIL " + label);
                        System.out.println(problem);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("error code=IO_ERROR path=checks message=" + e.getMessage());
            return 1;
        } finally {
            pool.shutdownNow();
        }
        System.out.println("checks=" + total + " failed=" + failed);
        return failed == 0 ? 0 : 1;
    }

    private static String runBlock(Path root, String classpath, ExecutorService pool, Block block,
                                   Map<String, String> captures) {
        try {
            require(block, "args");
            require(block, "exit");
            for (String key : block.dirs.keySet()) {
                if (!KEYS.contains(key)) {
                    return "  unknown directive: " + key;
                }
            }
            for (String rel : block.dirs.getOrDefault("delete-before", List.of())) {
                deleteUnderBuild(root, rel);
            }
            int want = Integer.parseInt(only(block, "exit"));
            Captured got = spawn(root, classpath, pool, split(only(block, "args")));
            StringBuilder fail = new StringBuilder();
            if (got.timedOut) {
                fail.append("  timed out\n");
            }
            if (got.exit != want) {
                fail.append("  exit: expected ").append(want).append(" but was ").append(got.exit).append('\n');
            }
            for (String literal : block.dirs.getOrDefault("stdout-contains", List.of())) {
                if (!got.stdout.contains(literal)) {
                    fail.append("  stdout missing: ").append(literal).append('\n');
                }
            }
            for (String literal : block.dirs.getOrDefault("stdout-not-contains", List.of())) {
                if (got.stdout.contains(literal)) {
                    fail.append("  stdout should not contain: ").append(literal).append('\n');
                }
            }
            for (String regex : block.dirs.getOrDefault("stdout-regex", List.of())) {
                if (!Pattern.compile(regex).matcher(got.stdout).find()) {
                    fail.append("  stdout regex missed: ").append(regex).append('\n');
                }
            }
            for (String literal : block.dirs.getOrDefault("stderr-contains", List.of())) {
                if (!got.stderr.contains(literal)) {
                    fail.append("  stderr missing: ").append(literal).append('\n');
                }
            }
            for (String regex : block.dirs.getOrDefault("stderr-regex", List.of())) {
                if (!Pattern.compile(regex).matcher(got.stderr).find()) {
                    fail.append("  stderr regex missed: ").append(regex).append('\n');
                }
            }
            if (block.dirs.containsKey("stderr-empty") && !got.stderr.isBlank()) {
                fail.append("  stderr was not empty\n");
            }
            if (block.dirs.containsKey("stdout-empty") && !got.stdout.isBlank()) {
                fail.append("  stdout was not empty\n");
            }
            for (String spec : block.dirs.getOrDefault("stdout-count", List.of())) {
                String[] parts = splitOnce(spec);
                int expected = Integer.parseInt(parts[0]);
                int actual = countOf(got.stdout, parts[1]);
                if (actual != expected) {
                    fail.append("  stdout count of \"").append(parts[1]).append("\": expected ")
                            .append(expected).append(" but was ").append(actual).append('\n');
                }
            }
            for (String spec : block.dirs.getOrDefault("capture", List.of())) {
                String[] parts = splitOnce(spec);
                Matcher matcher = Pattern.compile(parts[1]).matcher(got.stdout);
                if (!matcher.find() || matcher.groupCount() < 1) {
                    fail.append("  capture ").append(parts[0]).append(" missed\n");
                } else {
                    captures.put(parts[0], matcher.group(1));
                }
            }
            for (String name : block.dirs.getOrDefault("stdout-contains-capture", List.of())) {
                String value = captures.get(name);
                if (value == null) {
                    fail.append("  no capture named ").append(name).append('\n');
                } else if (!got.stdout.contains(value)) {
                    fail.append("  stdout missing capture ").append(name).append('\n');
                }
            }
            for (String other : block.dirs.getOrDefault("stdout-same-as", List.of())) {
                Captured ref = spawn(root, classpath, pool, split(other));
                if (!got.stdout.equals(ref.stdout)) {
                    fail.append("  stdout differs from: ").append(other).append('\n');
                }
            }
            boolean wantJson = block.dirs.containsKey("json-document")
                    || block.dirs.containsKey("json-array-nonempty")
                    || block.dirs.containsKey("json-equals")
                    || block.dirs.containsKey("json-regex");
            JsonNode tree = null;
            if (wantJson) {
                try {
                    tree = oneDocument(got.stdout);
                } catch (IOException | IllegalStateException e) {
                    fail.append("  json: ").append(e.getMessage()).append('\n');
                }
            }
            if (tree != null) {
                for (String path : block.dirs.getOrDefault("json-array-nonempty", List.of())) {
                    JsonNode node = walk(tree, path);
                    if (node == null || !node.isArray() || node.isEmpty()) {
                        fail.append("  json array empty or missing: ").append(path).append('\n');
                    }
                }
                for (String spec : block.dirs.getOrDefault("json-equals", List.of())) {
                    String[] parts = splitOnce(spec);
                    JsonNode node = walk(tree, parts[0]);
                    String actual = node == null || node.isNull() ? "null" : node.asText();
                    if (!parts[1].equals(actual)) {
                        fail.append("  json ").append(parts[0]).append(": expected ")
                                .append(parts[1]).append(" but was ").append(actual).append('\n');
                    }
                }
                for (String spec : block.dirs.getOrDefault("json-regex", List.of())) {
                    String[] parts = splitOnce(spec);
                    JsonNode node = walk(tree, parts[0]);
                    String actual = node == null || node.isNull() ? "" : node.asText();
                    if (!Pattern.compile(parts[1]).matcher(actual).find()) {
                        fail.append("  json regex ").append(parts[0]).append(" missed: ")
                                .append(parts[1]).append(" actual=").append(actual).append('\n');
                    }
                }
            }
            for (String rel : block.dirs.getOrDefault("file-exists", List.of())) {
                if (!Files.isRegularFile(underRoot(root, rel))) {
                    fail.append("  missing file: ").append(rel).append('\n');
                }
            }
            for (String rel : block.dirs.getOrDefault("file-absent", List.of())) {
                if (Files.exists(underRoot(root, rel))) {
                    fail.append("  path should be absent: ").append(rel).append('\n');
                }
            }
            if (fail.isEmpty()) {
                return null;
            }
            fail.append("  stdout:\n").append(indent(got.stdout));
            fail.append("  stderr:\n").append(indent(got.stderr));
            return fail.toString();
        } catch (Exception e) {
            return "  " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static Captured spawn(Path root, String classpath, ExecutorService pool, List<String> args)
            throws IOException, InterruptedException {
        String javaName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dpicocli.ansi=false");
        cmd.add("--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED");
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(MAIN);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(root.toFile());
        Process process = pb.start();
        Future<byte[]> outFuture = pool.submit(() -> process.getInputStream().readAllBytes());
        Future<byte[]> errFuture = pool.submit(() -> process.getErrorStream().readAllBytes());
        boolean finished = process.waitFor(600, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        try {
            String stdout = new String(outFuture.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            String stderr = new String(errFuture.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            return new Captured(finished ? process.exitValue() : -1, stdout, stderr, !finished);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            process.destroyForcibly();
            throw new IOException("cli process failed", e);
        }
    }

    private static JsonNode oneDocument(String stdout) throws IOException {
        try (JsonParser parser = JsonOut.MAPPER.getFactory().createParser(stdout)) {
            JsonNode tree = JsonOut.MAPPER.readTree(parser);
            if (tree == null || tree.isMissingNode()) {
                throw new IllegalStateException("empty document");
            }
            if (parser.nextToken() != null) {
                throw new IllegalStateException("trailing data after the JSON document");
            }
            return tree;
        }
    }

    private static JsonNode walk(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.", -1)) {
            if (current == null) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private static int countOf(String text, String literal) {
        int count = 0;
        int from = 0;
        while (from <= text.length()) {
            int at = text.indexOf(literal, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + Math.max(1, literal.length());
        }
        return count;
    }

    private static String[] splitOnce(String spec) {
        int space = spec.indexOf(' ');
        if (space < 0) {
            throw new IllegalArgumentException("expected \"path value\" but was: " + spec);
        }
        return new String[] {spec.substring(0, space), spec.substring(space + 1)};
    }

    private static List<String> split(String args) {
        if (args.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        boolean started = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                quote = !quote;
                started = true;
                continue;
            }
            if (!quote && Character.isWhitespace(c)) {
                if (started) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
                continue;
            }
            current.append(c);
            started = true;
        }
        if (quote) {
            throw new IllegalArgumentException("unclosed quote in args");
        }
        if (started) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static void require(Block block, String key) {
        if (!block.dirs.containsKey(key) || block.dirs.get(key).isEmpty()) {
            throw new IllegalArgumentException("missing " + key + " in " + block.name);
        }
    }

    private static String only(Block block, String key) {
        List<String> values = block.dirs.get(key);
        if (values.size() != 1) {
            throw new IllegalArgumentException(key + " must appear once in " + block.name);
        }
        return values.get(0);
    }

    private static void deleteUnderBuild(Path root, String rel) throws IOException {
        Path build = root.resolve("build").normalize();
        Path target = underRoot(root, rel);
        if (target.equals(build) || !target.startsWith(build)) {
            throw new IllegalArgumentException("delete-before must stay under build/: " + rel);
        }
        if (!Files.exists(target)) {
            return;
        }
        try (var walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Path underRoot(Path root, String rel) {
        Path base = root.toAbsolutePath().normalize();
        Path target = base.resolve(rel).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("path escapes the repo: " + rel);
        }
        return target;
    }

    private static String indent(String text) {
        if (text.isEmpty()) {
            return "  |\n";
        }
        String[] lines = text.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(lines.length, 80);
        for (int i = 0; i < limit; i++) {
            sb.append("  |").append(lines[i]).append('\n');
        }
        if (lines.length > limit) {
            sb.append("  |... truncated\n");
        }
        return sb.toString();
    }

    static List<Path> select(Path checks, String layer) throws IOException {
        if (!Files.isDirectory(checks)) {
            return List.of();
        }
        try (var stream = Files.list(checks)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().matches("L.*\\.check"))
                    .sorted(Comparator.comparing(LayerCheck::layerKey).thenComparing(Path::getFileName))
                    .toList();
            if (layer == null || layer.isBlank() || "all".equalsIgnoreCase(layer)) {
                return files;
            }
            String stem = layer.startsWith("L") || layer.startsWith("l") ? layer : "L" + layer;
            return files.stream()
                    .filter(path -> stem.equalsIgnoreCase(stripExt(path.getFileName().toString())))
                    .toList();
        }
    }

    private static String layerKey(Path path) {
        String stem = stripExt(path.getFileName().toString());
        String body = stem.length() > 1 ? stem.substring(1) : "";
        int i = 0;
        while (i < body.length() && Character.isDigit(body.charAt(i))) {
            i++;
        }
        int number = i == 0 ? 0 : Integer.parseInt(body.substring(0, i));
        return String.format("%08d%s", number, body.substring(i));
    }

    private static String stripExt(String name) {
        return name.endsWith(".check") ? name.substring(0, name.length() - ".check".length()) : name;
    }

    static List<Block> parse(String text) {
        List<Block> blocks = new ArrayList<>();
        Map<String, List<String>> dirs = new LinkedHashMap<>();
        String name = null;
        for (String raw : text.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if ("---".equals(trimmed)) {
                flush(blocks, name, dirs);
                dirs = new LinkedHashMap<>();
                name = null;
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                dirs.computeIfAbsent(trimmed, key -> new ArrayList<>()).add("true");
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if ("name".equals(key)) {
                name = value;
            } else {
                dirs.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
        }
        flush(blocks, name, dirs);
        return blocks;
    }

    private static void flush(List<Block> blocks, String name, Map<String, List<String>> dirs) {
        if (dirs.isEmpty() && name == null) {
            return;
        }
        blocks.add(new Block(name == null ? "block " + (blocks.size() + 1) : name, dirs));
    }

    record Block(String name, Map<String, List<String>> dirs) {
    }

    private record Captured(int exit, String stdout, String stderr, boolean timedOut) {
    }
}
