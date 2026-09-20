package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;

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
