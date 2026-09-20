package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

final class LauncherRepair {
    private final RuntimePaths paths;
    LauncherRepair(RuntimePaths paths) { this.paths = paths; }

    String health() {
        try {
            if (!Files.isRegularFile(paths.canonicalLauncher)) return "canonical launcher missing";
            if (!Files.isRegularFile(paths.rootLauncher)) return "root launcher missing";
            if (!sha256(paths.canonicalLauncher).equals(sha256(paths.rootLauncher))) return "root launcher differs";
            if (!paths.windows && !Files.isExecutable(paths.canonicalLauncher)) return "canonical launcher is not executable";
            if (!paths.windows && !Files.isExecutable(paths.rootLauncher)) return "root launcher is not executable";
            String execution = executionHealth();
            if (!"healthy".equals(execution)) return execution;
            return "healthy";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    synchronized String repair() {
        try {
            if (!Files.isRegularFile(paths.canonicalLauncher)) return "FAILED: canonical launcher missing: " + paths.canonicalLauncher;
            if (!paths.windows && !paths.canonicalLauncher.toFile().setExecutable(true, false))
                return "FAILED: unable to make canonical launcher executable: " + paths.canonicalLauncher;
            Path temporary = paths.rootLauncher.resolveSibling(paths.rootLauncher.getFileName().toString() + ".vibe-plugin.tmp");
            Files.copy(paths.canonicalLauncher, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            if (!paths.windows && !temporary.toFile().setExecutable(true, false))
                return "FAILED: unable to make temporary launcher executable: " + temporary;
            try {
                Files.move(temporary, paths.rootLauncher, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, paths.rootLauncher, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!paths.windows && !paths.rootLauncher.toFile().setExecutable(true, false))
                return "FAILED: unable to make root launcher executable: " + paths.rootLauncher;
            String execution = executionHealth();
            return "healthy".equals(execution) ? "OK" : "FAILED: " + execution;
        } catch (Exception e) {
            return "FAILED: " + e;
        }
    }

    private String executionHealth() {
        Process process = null;
        try {
            process = new ProcessBuilder(paths.rootLauncher.toString(), "--mim-version")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "launcher execution timed out";
            }
            String output = readLimited(process.getInputStream(), 4096);
            if (process.exitValue() != 0)
                return "launcher execution failed (exit " + process.exitValue() + "): " + output.trim();
            if (!output.contains("SageTV FFmpeg MIM"))
                return "launcher reached an unexpected runtime: " + output.trim();
            return "healthy";
        } catch (Exception e) {
            return "launcher execution failed: " + e.getMessage();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static String readLimited(InputStream in, int maximumBytes) throws IOException {
        byte[] buffer = new byte[512];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int count;
        while (out.size() < maximumBytes && (count = in.read(buffer, 0,
                Math.min(buffer.length, maximumBytes - out.size()))) >= 0) {
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = Files.newInputStream(path);
        try {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) >= 0) digest.update(buf, 0, n);
        } finally { in.close(); }
        byte[] hash = digest.digest(); StringBuilder s = new StringBuilder();
        for (byte b : hash) s.append(String.format("%02x", b & 0xff));
        return s.toString();
    }
}
