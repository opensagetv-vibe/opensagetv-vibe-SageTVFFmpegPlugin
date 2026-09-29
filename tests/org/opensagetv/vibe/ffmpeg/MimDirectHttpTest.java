package org.opensagetv.vibe.ffmpeg;

import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MimDirectHttpTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static int freePort() throws Exception {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private static Response http(String method, int port, String path,
                                 String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setRequestMethod(method);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if ("POST".equals(method)) connection.setDoOutput(true);
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 400
                ? connection.getErrorStream() : connection.getInputStream();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        if (stream != null) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            stream.close();
        }
        connection.disconnect();
        return new Response(status, new String(bytes.toByteArray(), "UTF-8"));
    }

    private static String field(String json, String name) {
        String marker = "\"" + name + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing " + name + " in " + json);
        start += marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) throw new AssertionError("Unterminated " + name + " in " + json);
        return json.substring(start, end);
    }

    public static void main(String[] args) throws Exception {
        Path home = java.nio.file.Paths.get(args[0]).toAbsolutePath();
        System.setProperty("vibe.ffmpeg.sageHome", home.toString());
        RuntimePaths paths = RuntimePaths.detect();
        Files.createDirectories(paths.runtimeDir);
        String script = "#!/bin/sh\n" +
                "if [ \"$1\" = \"--mim-status\" ]; then printf '{\"activeJobs\":[]}\\n'; exit 0; fi\n" +
                "playlist=''\nprevious=''\nfor arg in \"$@\"; do " +
                "if [ \"$previous\" = \"-segment_list\" ]; then playlist=\"$arg\"; fi; " +
                "previous=\"$arg\"; done\n" +
                "dir=$(dirname \"$playlist\")\n" +
                "dd if=/dev/zero of=\"$dir/seg_000000.ts\" bs=188 count=8 2>/dev/null\n" +
                "printf '#EXTM3U\\n#EXT-X-VERSION:3\\n#EXTINF:2.0,\\nseg_000000.ts\\n' > \"$playlist\"\n" +
                "sleep 2\n";
        Files.write(paths.mimExecutable, script.getBytes("UTF-8"));
        paths.mimExecutable.toFile().setExecutable(true, false);
        Files.write(paths.ffprobeExecutable,
                "#!/bin/sh\nprintf '1920,1080\\n'\n".getBytes("UTF-8"));
        paths.ffprobeExecutable.toFile().setExecutable(true, false);
        final Path source = home.resolve("fixture with spaces.ts");
        Files.write(source, new byte[]{0x47, 0x40, 0, 0x10});

        CaptionSideChannelService captions = new CaptionSideChannelService(paths);
        captions.start(false, freePort(), 1, 2, 64);
        MimDirectSessionService direct = new MimDirectSessionService(paths,
                new MimDirectSessionService.SourceAuthorizer() {
                    public boolean isAllowed(Path candidate) { return candidate.equals(source); }
                });
        direct.start(true);
        int apiPort = freePort();
        CaptionSideChannelHttpServer server = new CaptionSideChannelHttpServer(
                "127.0.0.1", apiPort, captions, direct);
        server.start();
        try {
            Response capabilities = http("GET", apiPort, "/v1/capabilities", null);
            check(capabilities.status == 200 && capabilities.body.contains("\"mimDirect\"") &&
                    capabilities.body.contains("\"available\":true"), capabilities.body);
            Response dvdStatus = http("GET", apiPort,
                    "/v1/dvd/status?mode=transcode", null);
            check(dvdStatus.status == 200 && dvdStatus.body.contains("\"state\":\"idle\"") &&
                    dvdStatus.body.contains("\"executionPath\":\"unknown\""),
                    dvdStatus.body);
            check(http("GET", apiPort, "/v1/dvd/status?mode=invalid", null).status == 400,
                    "invalid DVD status mode was accepted");
            String query = "?source=" + URLEncoder.encode(source.toString(), "UTF-8") +
                    "&mode=copy&active=true&startMs=0";
            Response started = http("POST", apiPort, "/v1/direct/start" + query, null);
            check(started.status == 200 && started.body.contains("\"state\":\"ready\"") &&
                    started.body.contains("\"active\":true"),
                    started.body);
            String token = field(started.body, "sessionToken");
            String mediaUrl = field(started.body, "mediaUrl");
            Response playlist = http("GET", apiPort, mediaUrl, null);
            check(playlist.status == 200 && playlist.body.contains("seg_000000.ts"), playlist.body);
            check(http("GET", apiPort, "/v1/direct/media/" + token +
                    "/seg_000000.ts", null).status == 200, "direct segment was not served");
            check(http("GET", apiPort, "/v1/direct/status?token=" + token,
                    null).status == 200, "direct status was not available on the LAN API");
            Response restarted = http("POST", apiPort, "/v1/direct/restart?token=" + token +
                    "&startMs=42000", null);
            check(restarted.status == 200 && restarted.body.contains("\"startMs\":42000"),
                    restarted.body);
            String restartedToken = field(restarted.body, "sessionToken");
            check(http("GET", apiPort, mediaUrl, null).status == 200,
                    "restart removed the previous media URL before player handoff");
            check(!restartedToken.equals(token), "restart reused the exposed media token");
            check(http("GET", apiPort, "/v1/direct/status?token=" + token,
                    null).status == 404, "old direct session remained visible");
            check(http("POST", apiPort, "/v1/direct/teardown?token=" + restartedToken,
                    null).status == 200, "direct teardown failed");
            check(http("GET", apiPort, "/v1/direct/status?token=" + restartedToken,
                    null).status == 404, "released direct session remained visible");
        } finally {
            server.stop();
            direct.close();
            captions.close();
        }
        System.out.println("MimDirectHttpTest PASS");
    }

    private static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
}
