package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

final class LauncherRepair {
    private final RuntimePaths paths;
    LauncherRepair(RuntimePaths paths) { this.paths = paths; }

    String health() {
        try {
            if (!Files.isRegularFile(paths.canonicalLauncher)) return "canonical launcher missing";
            if (!Files.isRegularFile(paths.rootLauncher)) return "root launcher missing";
            if (!sha256(paths.canonicalLauncher).equals(sha256(paths.rootLauncher))) return "root launcher differs";
            return "healthy";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    synchronized String repair() {
        try {
            if (!Files.isRegularFile(paths.canonicalLauncher)) return "FAILED: canonical launcher missing: " + paths.canonicalLauncher;
            Files.copy(paths.canonicalLauncher, paths.rootLauncher, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            paths.rootLauncher.toFile().setExecutable(true, false);
            return "OK";
        } catch (Exception e) {
            return "FAILED: " + e;
        }
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
