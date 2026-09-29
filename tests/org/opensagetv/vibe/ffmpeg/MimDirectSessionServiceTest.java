package org.opensagetv.vibe.ffmpeg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public final class MimDirectSessionServiceTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        check("auto".equals(MimDirectSessionService.normalizeDeinterlace(null)),
                "missing deinterlace policy defaults to auto");
        check("on".equals(MimDirectSessionService.normalizeDeinterlace(" ON ")),
                "on deinterlace policy normalized");
        boolean invalidDeinterlace = false;
        try { MimDirectSessionService.normalizeDeinterlace("sometimes"); }
        catch (IllegalArgumentException expected) { invalidDeinterlace = true; }
        check(invalidDeinterlace, "invalid deinterlace policy rejected");
        Path home = java.nio.file.Paths.get(args[0]).toAbsolutePath();
        System.setProperty("vibe.ffmpeg.sageHome", home.toString());
        RuntimePaths paths = RuntimePaths.detect();
        Files.createDirectories(paths.runtimeDir);
        String script = "#!/bin/sh\n" +
                "status=\"$(dirname \"$0\")/fake-direct-status.json\"\n" +
                "if [ \"$1\" = \"--mim-status\" ]; then if [ -f \"$status\" ]; then cat \"$status\"; else printf '{\"activeJobs\":[]}\\n'; fi; exit 0; fi\n" +
                "printf '%s\\n' \"$@\" > \"$(dirname \"$0\")/last-direct-args.txt\"\n" +
                "if [ -f \"$(dirname \"$0\")/fail-active-once\" ]; then " +
                "for arg in \"$@\"; do if [ \"$arg\" = \"-activefile\" ]; then " +
                "rm -f \"$(dirname \"$0\")/fail-active-once\"; exit 0; fi; done; fi\n" +
                "playlist=''\ninput=''\nprevious=''\nfor arg in \"$@\"; do " +
                "if [ \"$previous\" = \"-segment_list\" ]; then playlist=\"$arg\"; fi; " +
                "if [ \"$previous\" = \"-i\" ]; then input=\"$arg\"; fi; " +
                "previous=\"$arg\"; done\n" +
                "dir=$(dirname \"$playlist\")\n" +
                "now=$(date +%s)000\n" +
                "printf '{\"activeJobs\":[{\"state\":\"running\",\"backend\":\"copy\",\"encoder\":\"copy\",\"hardwareEncode\":false,\"hardwareDecode\":false,\"input\":\"%s\",\"outputFormat\":\"segment\",\"startedEpochMs\":%s}]}\\n' \"$input\" \"$now\" > \"$status\"\n" +
                "printf '#EXTM3U\\n#EXT-X-VERSION:3\\n' > \"$playlist\"\n" +
                "sleep 1\n" +
                "dd if=/dev/zero of=\"$dir/seg_000000.ts\" bs=188 count=8 2>/dev/null\n" +
                "printf '#EXTM3U\\n#EXT-X-VERSION:3\\n#EXTINF:2.0,\\nseg_000000.ts\\n#EXT-X-ENDLIST\\n' > \"$playlist\"\n" +
                "if IFS= read -r control; then printf '%s\\n' \"$control\" > \"$(dirname \"$0\")/last-direct-control.txt\"; fi\n";
        Files.write(paths.mimExecutable, script.getBytes("UTF-8"));
        paths.mimExecutable.toFile().setExecutable(true, false);
        Files.write(paths.ffprobeExecutable,
                // Real MPEG-TS FFprobe output repeats the selected video
                // stream in the program and global stream tables.
                ("#!/bin/sh\n" +
                "for arg in \"$@\"; do if [ \"$arg\" = \"format=duration\" ]; then " +
                "printf '60.000\\n'; exit 0; fi; done\n" +
                "printf '1920,1080\\n\\n1920,1080\\n'\n").getBytes("UTF-8"));
        paths.ffprobeExecutable.toFile().setExecutable(true, false);
        Path source = home.resolve("fixture.ts");
        Files.write(source, new byte[]{0x47, 0x40, 0, 0x10});

        MimDirectSessionService service = new MimDirectSessionService(paths,
                new MimDirectSessionService.SourceAuthorizer() {
                    public boolean isAllowed(Path candidate) { return candidate.equals(source); }
                });
        service.start(true);
        check(service.capabilitiesJson().contains("\"available\":true"),
                service.capabilitiesJson());
        MimDirectSessionService.Session session = service.create(
                source.toString(), "copy", 0L);
        check(Files.isRegularFile(session.playlist), "playlist not created");
        check(Files.isRegularFile(session.directory.resolve("seg_000000.ts")),
                "segment not created");
        String command = new String(Files.readAllBytes(
                paths.runtimeDir.resolve("last-direct-args.txt")), "UTF-8");
        check(command.contains("-f\nsegment\n"),
                "Direct output is not segment M3U8: " + command);
        check(command.contains("-segment_format\nmpegts\n"),
                "Direct output is not MPEG-TS segmented: " + command);
        check(command.contains("-map\n0:s?\n"),
                "Direct output does not preserve broadcast subtitles: " + command);
        Path stale = session.directory.resolve("seg_999999.ts");
        Files.write(stale, new byte[]{0x47, 0, 0, 0});
        Files.setLastModifiedTime(stale,
                FileTime.fromMillis(System.currentTimeMillis() - 60_000L));
        session.cleanupUnlistedSegments();
        check(!Files.exists(stale), "unlisted Direct segment was not bounded");
        check(session.json().contains("/v1/direct/media/" + session.token + "/stream.m3u8"),
                session.json());
        check(session.json().contains("\"path\":\"copy\""),
                "segment-muxer execution proof was not matched: " + session.json());
        check(service.get(session.token) == session, "session not registered");
        MimDirectSessionService.Session restarted = service.restart(session.token, 42000L);
        check(restarted != session, "restart reused the old session");
        check(restarted.startMs == 42000L, "restart did not preserve requested position");
        check(service.get(session.token) == null, "restart retained old session");
        check(service.getMedia(session.token) == session,
                "restart did not retain old media for player handoff");
        check(Files.exists(session.directory),
                "restart removed old session files before player handoff");
        check(service.release(session.token), "retired handoff teardown failed");
        check(!Files.exists(session.directory), "retired handoff files remained");
        check(service.release(restarted.token), "session teardown failed");
        MimDirectSessionService.Session edgeClamped = service.create(
                source.toString(), "copy", 86_400_000L, "auto", true);
        check(edgeClamped.requestedStartMs == 86_400_000L,
                "requested live-edge position was not retained for diagnostics");
        check(edgeClamped.startMs == 56_000L,
                "out-of-range live seek was not bounded with preroll: " + edgeClamped.startMs);
        check(edgeClamped.json().contains("\"requestedStartMs\":86400000"),
                edgeClamped.json());
        check(edgeClamped.json().contains("\"startMs\":56000"), edgeClamped.json());
        service.release(edgeClamped.token);
        check("STOP".equals(new String(Files.readAllBytes(
                        paths.runtimeDir.resolve("last-direct-control.txt")), "UTF-8").trim()),
                "Direct teardown did not use MIM's graceful STOP control");
        check(service.get(session.token) == null, "released session remained visible");
        check(!Files.exists(restarted.directory), "released session files remained");
        MimDirectSessionService.Session noDeinterlace = service.create(
                source.toString(), "transcode", 0L, "off", true);
        command = new String(Files.readAllBytes(
                paths.runtimeDir.resolve("last-direct-args.txt")), "UTF-8");
        check(command.contains("-sagetvdeinterlace\noff\n"),
                "Direct deinterlace policy was not passed to MIM: " + command);
        check(command.contains("-activefile\n"),
                "growing Direct source was not declared active to MIM: " + command);
        check(noDeinterlace.json().contains("\"deinterlace\":\"off\""),
                noDeinterlace.json());
        check(noDeinterlace.json().contains("\"active\":true"),
                noDeinterlace.json());
        MimDirectSessionService.Session noDeinterlaceRestart =
                service.restart(noDeinterlace.token, 12000L);
        check("off".equals(noDeinterlaceRestart.deinterlace),
                "restart did not preserve deinterlace policy");
        service.release(noDeinterlace.token);
        service.release(noDeinterlaceRestart.token);
        Files.write(paths.runtimeDir.resolve("fail-active-once"),
                new byte[]{1});
        MimDirectSessionService.Session staleActiveRecovered = service.create(
                source.toString(), "transcode", 0L, "off", true);
        command = new String(Files.readAllBytes(
                paths.runtimeDir.resolve("last-direct-args.txt")), "UTF-8");
        check(!command.contains("-activefile\n"),
                "stale active hint did not retry as completed media: " + command);
        check(staleActiveRecovered.json().contains("\"active\":true"),
                staleActiveRecovered.json());
        check(staleActiveRecovered.json().contains("\"activeFileApplied\":false"),
                "stale-active recovery was not observable: " +
                        staleActiveRecovered.json());
        service.release(staleActiveRecovered.token);
        boolean rejected = false;
        try { service.create(home.resolve("not-library.ts").toString(), "copy", 0L); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "non-library source was accepted");
        service.close();
        System.out.println("MimDirectSessionServiceTest PASS");
    }
}
