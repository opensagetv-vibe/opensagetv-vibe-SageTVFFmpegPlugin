package org.opensagetv.vibe.ffmpeg;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import sage.DVDStreamTransform;
import sage.DVDStreamTransformRequest;

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
    String sh="#!/bin/sh\nif [ \"$1\" = \"--mim-version\" ]; then echo 'SageTV FFmpeg MIM 0.4.9 (test)'; exit 0; fi\nif [ \"$1\" = \"--mim-status\" ]; then echo '{\"mimVersion\":\"0.4.9\",\"platform\":\"linux-x64\",\"activeJobs\":[],\"lastTranscodeJob\":{\"state\":\"stopped\",\"backend\":\"vaapi\",\"encoder\":\"h264_vaapi\",\"hardwareDecode\":false}}'; exit 0; fi\nif [ \"$1\" = \"--mim-capabilities\" ]; then echo '{\"mimVersion\":\"0.4.9\",\"platform\":\"linux-x64\",\"ffmpegVersion\":\"ffmpeg version 9.0.1\",\"selectedBackend\":\"vaapi\",\"dvdStreamTransform\":true,\"backends\":{\"vaapi\":{\"usable\":true},\"qsv\":{\"usable\":false},\"software\":{\"usable\":true}}}'; exit 0; fi\nif [ \"$1\" = \"-sagetvdiscstream\" ]; then printf '%s\\n' \"$@\" > \"$(dirname \"$0\")/dvd-transform-args.txt\"; cat; exit 0; fi\nexit 2\n";
    Files.write(mim,sh.getBytes(StandardCharsets.UTF_8)); mim.toFile().setExecutable(false,false);
    Path ffmpeg=runtime.resolve("ffmpeg.real"); Files.write(ffmpeg,"runtime".getBytes(StandardCharsets.UTF_8)); ffmpeg.toFile().setExecutable(false,false);
    Path ffprobe=runtime.resolve("ffprobe"); Files.write(ffprobe,"probe".getBytes(StandardCharsets.UTF_8)); ffprobe.toFile().setExecutable(false,false);
    Path canonical=launcher.resolve("SageTVTranscoder");
    String bridge="#!/bin/sh\nexec \"$(dirname \"$0\")/plugins/SageTVFFmpegPlugin/runtime/ffmpeg_MIM\" \"$@\"\n";
    Files.write(canonical,bridge.getBytes(StandardCharsets.UTF_8)); canonical.toFile().setExecutable(false,false);

    SageTVFFmpegPlugin p=new SageTVFFmpegPlugin(null,false); p.start();
    if(!Files.isExecutable(mim) || !Files.isExecutable(ffmpeg) || !Files.isExecutable(ffprobe) || !Files.isExecutable(canonical) || !Files.isExecutable(home.resolve("SageTVTranscoder")))
      throw new AssertionError("startup did not repair Linux executable permissions");
    if(!Arrays.asList(p.getConfigSettings()).contains("capabilities.software.usable")) throw new AssertionError("virtual settings missing");
    if(!"auto".equals(p.getConfigValue("hardware.backend"))) throw new AssertionError();
    if(!"healthy".equals(p.getConfigValue("health.runtime"))) throw new AssertionError(p.getConfigValue("health.runtime"));
    p.setConfigValue("hardware.backend","vaapi");
    String changed=new String(Files.readAllBytes(runtime.resolve("ffmpeg.real.ini")),StandardCharsets.UTF_8);
    if(!changed.contains("; keep this comment") || !changed.contains("unknown_future_key=preserve-me") || !changed.contains("backend=vaapi")) throw new AssertionError(changed);
    if(!Files.isRegularFile(runtime.resolve("ffmpeg.real.ini.bak"))) throw new AssertionError("backup missing");
    if(!"vaapi".equals(p.getConfigValue("status.last.backend"))) throw new AssertionError(p.getConfigValue("status.raw"));
    if(!"true".equals(p.getConfigValue("capabilities.vaapi.usable"))) throw new AssertionError(p.getConfigValue("capabilities.raw"));
    if(!"unavailable".equals(p.getConfigValue("capabilities.vaapi.preflight"))) throw new AssertionError(p.getConfigValue("capabilities.vaapi.preflight"));
    if(!"healthy".equals(p.getConfigValue("health.launcher"))) throw new AssertionError(p.getConfigValue("health.launcher"));
    MimDVDStreamTransformProvider dvdProvider=new MimDVDStreamTransformProvider();
    if(!MimDVDStreamTransformProvider.TRANSPORT_ID.equals(dvdProvider.getTransportId())) throw new AssertionError();
    if(!"mpegts".equals(dvdProvider.getOutputFormat())) throw new AssertionError();
    if(!dvdProvider.isAvailable()) throw new AssertionError("DVD transform capability not discovered");
    DVDStreamTransform session=dvdProvider.open(new DVDStreamTransformRequest(
            MimDVDStreamTransformProvider.TRANSPORT_ID,"7M"));
    byte[] input="provider-stream".getBytes(StandardCharsets.UTF_8);
    session.write(input,0,input.length); session.closeInput();
    byte[] output=null;
    for(int i=0;i<20 && output==null;i++) output=session.pollOutput(100);
    if(!Arrays.equals(input,output)) throw new AssertionError("DVD transform stream did not round trip");
    session.close();
    String transformArgs=new String(Files.readAllBytes(runtime.resolve("dvd-transform-args.txt")),StandardCharsets.UTF_8);
    if(!transformArgs.contains("-sagetvdiscstream") || !transformArgs.contains("7M")
            || !transformArgs.contains("mpegts")) throw new AssertionError(transformArgs);

    // A runtime/default upgrade must not overwrite the user-owned live INI.
    Files.write(runtime.resolve("ffmpeg.real.ini.default"),"[hardware]\nbackend=software\n".getBytes(StandardCharsets.UTF_8));
    SageTVFFmpegPlugin upgraded=new SageTVFFmpegPlugin(null,false); upgraded.start();
    String preserved=new String(Files.readAllBytes(runtime.resolve("ffmpeg.real.ini")),StandardCharsets.UTF_8);
    if(!preserved.contains("backend=vaapi") || preserved.contains("backend=software")) throw new AssertionError(preserved);

    // A missing/corrupt root bridge is repaired from the canonical copy.
    Files.write(home.resolve("SageTVTranscoder"),"corrupt".getBytes(StandardCharsets.UTF_8));
    upgraded.setConfigValue("action.repairLauncher","1");
    if(!"healthy".equals(upgraded.getConfigValue("health.launcher"))) throw new AssertionError(upgraded.getConfigValue("health.launcher"));

    // Restore is deliberately two-step and backs up the live file.
    upgraded.setConfigValue("action.restoreDefaultIni","1");
    if(!upgraded.getConfigValue("action.last").startsWith("confirmation required")) throw new AssertionError(upgraded.getConfigValue("action.last"));
    if(!preserved.equals(new String(Files.readAllBytes(runtime.resolve("ffmpeg.real.ini")),StandardCharsets.UTF_8))) throw new AssertionError("first press changed INI");
    upgraded.setConfigValue("action.restoreDefaultIni","1");
    String restored=new String(Files.readAllBytes(runtime.resolve("ffmpeg.real.ini")),StandardCharsets.UTF_8);
    if(!restored.contains("backend=software")) throw new AssertionError(restored);
    if(!Files.isRegularFile(runtime.resolve("ffmpeg.real.ini.bak"))) throw new AssertionError("restore backup missing");
    System.out.println("PluginSmokeTest PASS");
  }
}
