package org.opensagetv.vibe.ffmpeg;

import java.nio.file.Path;
import java.nio.file.Paths;

final class RuntimePaths {
    final boolean windows;
    final Path sageHome;
    final Path pluginHome;
    final Path runtimeDir;
    final Path launcherDir;
    final Path mimExecutable;
    final Path ffmpegExecutable;
    final Path ffprobeExecutable;
    final Path ini;
    final Path defaultIni;
    final Path rootLauncher;
    final Path canonicalLauncher;

    private RuntimePaths(Path sageHome, boolean windows) {
        this.windows = windows;
        this.sageHome = sageHome.toAbsolutePath().normalize();
        this.pluginHome = this.sageHome.resolve("plugins").resolve("SageTVFFmpegPlugin");
        this.runtimeDir = pluginHome.resolve("runtime");
        this.launcherDir = pluginHome.resolve("launcher");
        this.mimExecutable = runtimeDir.resolve(windows ? "ffmpeg_MIM.exe" : "ffmpeg_MIM");
        this.ffmpegExecutable = runtimeDir.resolve(windows ? "ffmpeg.real.exe" : "ffmpeg.real");
        this.ffprobeExecutable = runtimeDir.resolve(windows ? "ffprobe.exe" : "ffprobe");
        this.ini = runtimeDir.resolve("ffmpeg.real.ini");
        this.defaultIni = runtimeDir.resolve("ffmpeg.real.ini.default");
        this.rootLauncher = this.sageHome.resolve(windows ? "SageTVTranscoder.exe" : "SageTVTranscoder");
        this.canonicalLauncher = launcherDir.resolve(windows ? "SageTVTranscoder.exe" : "SageTVTranscoder");
    }

    static RuntimePaths detect() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String override = System.getProperty("vibe.ffmpeg.sageHome");
        if (override == null || override.trim().length() == 0) override = System.getenv("SAGETV_HOME");
        Path home = (override == null || override.trim().length() == 0)
                ? Paths.get(System.getProperty("user.dir", ".")) : Paths.get(override);
        return new RuntimePaths(home, windows);
    }
}
