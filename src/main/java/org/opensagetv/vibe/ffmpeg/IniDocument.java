package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small line-preserving INI editor.
 *
 * It deliberately does not normalize the file. Comments, blank lines, section
 * ordering, unknown keys and user-added content survive STVi/plugin edits.
 */
final class IniDocument {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final Path path;
    private final List<String> lines = new ArrayList<String>();
    private String newline = "\n";
    private boolean trailingNewline;

    private IniDocument(Path path) {
        this.path = path;
    }

    static IniDocument load(Path path) throws IOException {
        IniDocument doc = new IniDocument(path);
        byte[] bytes = Files.readAllBytes(path);
        String text = new String(bytes, UTF8);
        doc.newline = text.indexOf("\r\n") >= 0 ? "\r\n" : "\n";
        doc.trailingNewline = text.endsWith("\n") || text.endsWith("\r");
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n", -1);
        int count = raw.length;
        if (doc.trailingNewline && count > 0 && raw[count - 1].length() == 0) count--;
        for (int i = 0; i < count; i++) doc.lines.add(raw[i]);
        return doc;
    }

    String get(String section, String key, String defaultValue) {
        int[] range = sectionRange(section);
        if (range == null) return defaultValue;
        for (int i = range[0]; i < range[1]; i++) {
            KeyLine parsed = parseKey(lines.get(i));
            if (parsed != null && equalsIgnoreCase(parsed.key, key)) return parsed.value;
        }
        return defaultValue;
    }

    boolean getBoolean(String section, String key, boolean defaultValue) {
        String value = get(section, key, defaultValue ? "true" : "false").trim().toLowerCase(Locale.ROOT);
        if (value.equals("1") || value.equals("true") || value.equals("yes") || value.equals("on")) return true;
        if (value.equals("0") || value.equals("false") || value.equals("no") || value.equals("off")) return false;
        return defaultValue;
    }

    void set(String section, String key, String value) {
        int[] range = sectionRange(section);
        if (range == null) {
            if (!lines.isEmpty() && lines.get(lines.size() - 1).trim().length() != 0) lines.add("");
            lines.add("[" + section + "]");
            lines.add(key + "=" + value);
            trailingNewline = true;
            return;
        }
        for (int i = range[0]; i < range[1]; i++) {
            KeyLine parsed = parseKey(lines.get(i));
            if (parsed != null && equalsIgnoreCase(parsed.key, key)) {
                lines.set(i, parsed.prefix + value + parsed.suffix);
                return;
            }
        }
        int insertAt = range[1];
        while (insertAt > range[0] && lines.get(insertAt - 1).trim().length() == 0) insertAt--;
        lines.add(insertAt, key + "=" + value);
    }

    void saveWithBackup() throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName().toString() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append(newline);
            out.append(lines.get(i));
        }
        if (trailingNewline) out.append(newline);
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(tmp, out.toString().getBytes(UTF8));
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int[] sectionRange(String section) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("[") && t.endsWith("]")) {
                String name = t.substring(1, t.length() - 1).trim();
                if (start >= 0) return new int[] { start, i };
                if (equalsIgnoreCase(name, section)) start = i + 1;
            }
        }
        return start >= 0 ? new int[] { start, lines.size() } : null;
    }

    private static KeyLine parseKey(String line) {
        String trimmed = line.trim();
        if (trimmed.length() == 0 || trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("[")) return null;
        int eq = line.indexOf('=');
        if (eq < 0) return null;
        String key = line.substring(0, eq).trim();
        if (key.length() == 0) return null;
        int valueStart = eq + 1;
        while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) valueStart++;
        String prefix = line.substring(0, valueStart);
        String remainder = line.substring(valueStart);
        // Preserve an inline comment only when separated from the value by whitespace.
        int commentAt = findInlineComment(remainder);
        String value = commentAt >= 0 ? remainder.substring(0, commentAt).trim() : remainder.trim();
        String suffix = commentAt >= 0 ? remainder.substring(commentAt) : "";
        return new KeyLine(key, value, prefix, suffix);
    }

    private static int findInlineComment(String s) {
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == ';' || c == '#') && Character.isWhitespace(s.charAt(i - 1))) {
                int start = i;
                while (start > 0 && Character.isWhitespace(s.charAt(start - 1))) start--;
                return start;
            }
        }
        return -1;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a.equalsIgnoreCase(b);
    }

    private static final class KeyLine {
        final String key;
        final String value;
        final String prefix;
        final String suffix;
        KeyLine(String key, String value, String prefix, String suffix) {
            this.key = key;
            this.value = value;
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }
}
