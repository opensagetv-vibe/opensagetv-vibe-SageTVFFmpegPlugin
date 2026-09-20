package org.opensagetv.vibe.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class MimRuntime {
    private final RuntimePaths paths;
    private volatile Snapshot status = Snapshot.empty();
    private volatile Snapshot capabilities = Snapshot.empty();

    MimRuntime(RuntimePaths paths) { this.paths = paths; }

    Snapshot status(boolean force) {
        long now = System.currentTimeMillis();
        Snapshot current = status;
        if (!force && now - current.timeMs < 1000) return current;
        status = query("--mim-status");
        return status;
    }

    Snapshot capabilities(boolean force) {
        long now = System.currentTimeMillis();
        Snapshot current = capabilities;
        if (!force && now - current.timeMs < 30000) return current;
        capabilities = query("--mim-capabilities");
        return capabilities;
    }

    String health() {
        if (!Files.isRegularFile(paths.mimExecutable)) return "missing MIM wrapper";
        if (!Files.isRegularFile(paths.ffmpegExecutable)) return "missing FFmpeg runtime";
        if (!Files.isRegularFile(paths.ffprobeExecutable)) return "missing FFprobe runtime";
        if (!paths.windows && !Files.isExecutable(paths.mimExecutable)) return "MIM wrapper is not executable";
        if (!paths.windows && !Files.isExecutable(paths.ffmpegExecutable)) return "FFmpeg runtime is not executable";
        if (!paths.windows && !Files.isExecutable(paths.ffprobeExecutable)) return "FFprobe runtime is not executable";
        if (!Files.isRegularFile(paths.defaultIni)) return "missing default INI";
        if (!Files.isRegularFile(paths.ini)) return "missing live INI";
        return "healthy";
    }

    String repairExecutablePermissions() {
        if (paths.windows) return "OK";
        String failure = makeExecutable(paths.mimExecutable, "MIM wrapper");
        if (failure != null) return failure;
        failure = makeExecutable(paths.ffmpegExecutable, "FFmpeg runtime");
        if (failure != null) return failure;
        failure = makeExecutable(paths.ffprobeExecutable, "FFprobe runtime");
        return failure == null ? "OK" : failure;
    }

    private static String makeExecutable(java.nio.file.Path path, String label) {
        if (!Files.isRegularFile(path)) return "FAILED: " + label + " missing: " + path;
        if (Files.isExecutable(path)) return null;
        return path.toFile().setExecutable(true, false)
                ? null : "FAILED: unable to make " + label + " executable: " + path;
    }

    private Snapshot query(String arg) {
        long now = System.currentTimeMillis();
        if (!Files.isRegularFile(paths.mimExecutable)) return Snapshot.error(now, "MIM executable missing: " + paths.mimExecutable);
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(paths.mimExecutable.toString(), arg);
            pb.directory(paths.runtimeDir.toFile());
            pb.redirectErrorStream(true);
            pb.environment().put("SAGETV_FFMPEG_MIM_INI", paths.ini.toString());
            process = pb.start();
            boolean done = process.waitFor(7, TimeUnit.SECONDS);
            if (!done) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
                return Snapshot.error(now, arg + " timed out");
            }
            String raw = readAll(process.getInputStream(), 262144).trim();
            if (process.exitValue() != 0) return Snapshot.error(now, arg + " exit=" + process.exitValue() + " output=" + raw);
            Map<String, Object> parsed = raw.startsWith("{") ? MiniJson.object(raw) : Collections.<String,Object>emptyMap();
            return new Snapshot(now, raw, parsed, null);
        } catch (Exception e) {
            return Snapshot.error(now, e.toString());
        } finally {
            if (process != null) process.destroy();
        }
    }

    static Object path(Map<String, Object> map, String dotted) {
        Object cur = map;
        String[] parts = dotted.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if (!(cur instanceof Map)) return null;
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) cur;
            cur = m.get(parts[i]);
        }
        return cur;
    }

    static Object lastTranscode(Map<String, Object> map, String field) {
        Object job = map.get("lastTranscodeJob");
        if (!(job instanceof Map)) return null;
        @SuppressWarnings("unchecked") Map<String,Object> m = (Map<String,Object>) job;
        return m.get(field);
    }

    static String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private static String readAll(InputStream in, int maximumBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) >= 0) {
            if (out.size() + n > maximumBytes) throw new IOException("MIM query output exceeded " + maximumBytes + " bytes");
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static final class Snapshot {
        final long timeMs;
        final String raw;
        final Map<String, Object> json;
        final String error;
        Snapshot(long timeMs, String raw, Map<String,Object> json, String error) {
            this.timeMs = timeMs; this.raw = raw; this.json = json; this.error = error;
        }
        static Snapshot empty() { return new Snapshot(0, "", Collections.<String,Object>emptyMap(), "not queried"); }
        static Snapshot error(long timeMs, String error) { return new Snapshot(timeMs, "", Collections.<String,Object>emptyMap(), error); }
        boolean ok() { return error == null; }
    }
}
