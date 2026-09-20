package org.opensagetv.vibe.ffmpeg;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PluginSmokeTest {
  public static void main(String[] args) throws Exception {
    Path home=Paths.get(args[0]);
    System.setProperty("vibe.ffmpeg.sageHome",home.toString());
    Path runtime=home.resolve("plugins/SageTVFFmpegPlugin/runtime");
    Path launcher=home.resolve("plugins/SageTVFFmpegPlugin/launcher");
    Files.createDirectories(runtime); Files.createDirectories(launcher);
    String ini="; keep this comment\n[hardware]\n; backend note\nbackend=auto\nunknown_future_key=preserve-me\n\n[logging]\nlevel=info\n";
    Files.write(runtime.resolve("ffmpeg.real.ini.default"),ini.getBytes(StandardCharsets.UTF_8));
    Path mim=runtime.resolve("ffmpeg_MIM");
    String sh="#!/bin/sh\nif [ \"$1\" = \"--mim-status\" ]; then echo '{\"mimVersion\":\"0.4.9\",\"platform\":\"linux-x64\",\"activeJobs\":[],\"lastTranscodeJob\":{\"state\":\"stopped\",\"backend\":\"vaapi\",\"encoder\":\"h264_vaapi\",\"hardwareDecode\":false}}'; exit 0; fi\nif [ \"$1\" = \"--mim-capabilities\" ]; then echo '{\"mimVersion\":\"0.4.9\",\"platform\":\"linux-x64\",\"ffmpegVersion\":\"ffmpeg version 9.0.1\",\"selectedBackend\":\"vaapi\",\"backends\":{\"vaapi\":{\"usable\":true},\"qsv\":{\"usable\":false},\"software\":{\"usable\":true}}}'; exit 0; fi\nexit 2\n";
    Files.write(mim,sh.getBytes(StandardCharsets.UTF_8)); mim.toFile().setExecutable(true,false);
    Path canonical=launcher.resolve("SageTVTranscoder"); Files.write(canonical,"bridge".getBytes(StandardCharsets.UTF_8)); canonical.toFile().setExecutable(true,false);

    SageTVFFmpegPlugin p=new SageTVFFmpegPlugin(null,false); p.start();
    if(!"auto".equals(p.getConfigValue("hardware.backend"))) throw new AssertionError();
    p.setConfigValue("hardware.backend","vaapi");
    String changed=new String(Files.readAllBytes(runtime.resolve("ffmpeg.real.ini")),StandardCharsets.UTF_8);
    if(!changed.contains("; keep this comment") || !changed.contains("unknown_future_key=preserve-me") || !changed.contains("backend=vaapi")) throw new AssertionError(changed);
    if(!Files.isRegularFile(runtime.resolve("ffmpeg.real.ini.bak"))) throw new AssertionError("backup missing");
    if(!"vaapi".equals(p.getConfigValue("status.last.backend"))) throw new AssertionError(p.getConfigValue("status.raw"));
    if(!"true".equals(p.getConfigValue("capabilities.vaapi.usable"))) throw new AssertionError(p.getConfigValue("capabilities.raw"));
    if(!"healthy".equals(p.getConfigValue("health.launcher"))) throw new AssertionError(p.getConfigValue("health.launcher"));
    System.out.println("PluginSmokeTest PASS");
  }
}
