package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Writes credential-free, server-local copies of the latest synthetic GPU test. */
final class HardwareReportExporter {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private HardwareReportExporter() { }

    static Path export(RuntimePaths paths, MimRuntime.Snapshot snapshot, String pluginVersion) throws IOException {
        if (snapshot == null || !snapshot.ok() || snapshot.raw == null || !snapshot.raw.trim().startsWith("{") ||
                !"complete".equals(MimRuntime.string(snapshot.json.get("state"))))
            throw new IOException("Run Hardware Test successfully before exporting a report");
        Files.createDirectories(paths.reportDir);
        String generated = Instant.now().toString();
        String payload = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"generatedUtc\": \"" + escape(generated) + "\",\n" +
                "  \"pluginVersion\": \"" + escape(pluginVersion) + "\",\n" +
                "  \"serverPlatform\": \"" + escape(System.getProperty("os.name", "") + " " + System.getProperty("os.arch", "")) + "\",\n" +
                "  \"hardwareTest\": " + snapshot.raw.trim() + "\n" +
                "}\n";
        Path timestamped = paths.reportDir.resolve("OpenSageTV-Vibe-Hardware-Test-" +
                FILE_TIME.format(Instant.now()) + ".json");
        writeAtomic(timestamped, payload);
        writeAtomic(paths.reportDir.resolve("OpenSageTV-Vibe-Hardware-Test-latest.json"), payload);
        return timestamped;
    }

    private static void writeAtomic(Path target, String text) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(temporary, text.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
