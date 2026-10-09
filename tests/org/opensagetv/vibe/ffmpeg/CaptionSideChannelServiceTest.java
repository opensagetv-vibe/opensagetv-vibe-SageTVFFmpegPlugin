package org.opensagetv.vibe.ffmpeg;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class CaptionSideChannelServiceTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static byte[] a53() {
        return new byte[]{0,0,1,(byte)0xb2,0x47,0x41,0x39,0x34,3,0x44,(byte)0xff,
                (byte)0xfc,(byte)0x94,0x2e,(byte)0xfc,(byte)0x94,0x20,
                (byte)0xfc,0x48,0x49,(byte)0xfc,(byte)0x94,0x2f,(byte)0xff};
    }

    private static byte[] pesPacket(long pts, int continuity) {
        byte[] packet = new byte[188];
        Arrays.fill(packet, (byte) 0xff);
        packet[0] = 0x47;
        packet[1] = 0x41;
        packet[2] = 1;
        packet[3] = (byte) (0x10 | (continuity & 15));
        byte[] header = {0,0,1,(byte)0xe0,0,0,(byte)0x80,(byte)0x80,5};
        System.arraycopy(header, 0, packet, 4, header.length);
        packet[13] = (byte) (0x21 | ((pts >> 29) & 14));
        packet[14] = (byte) (pts >> 22);
        packet[15] = (byte) (((pts >> 14) & 254) | 1);
        packet[16] = (byte) (pts >> 7);
        packet[17] = (byte) (((pts << 1) & 254) | 1);
        byte[] payload = a53();
        System.arraycopy(payload, 0, packet, 18, payload.length);
        return packet;
    }

    private static Path only(Path directory, String glob) throws Exception {
        Path found = null;
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(directory, glob)) {
            for (Path path : entries) {
                if (found != null) throw new AssertionError("Multiple slot files for " + glob);
                found = path;
            }
        }
        if (found == null) throw new AssertionError("Missing slot file for " + glob);
        return found;
    }

    private static int freePort() throws Exception {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private static Response http(String method, int port, String path, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setRequestMethod(method);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if ("POST".equals(method)) connection.setDoOutput(true);
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
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

    public static void main(String[] args) throws Exception {
        CaptionSideChannelService.Session reorderedPts =
                new CaptionSideChannelService.Session("pts-order-test", 1L, 16, 1024);
        reorderedPts.offer(2000L, new byte[]{(byte) 0xfc, 0x41, 0x42});
        reorderedPts.offer(1900L, new byte[]{(byte) 0xfc, 0x43, 0x44});
        String pending = reorderedPts.read(0L, 950L, 16);
        check(pending.contains("\"cursor\":0") && pending.contains("\"packets\":[]"),
                "future decode-order record was skipped: " + pending);
        String due = reorderedPts.read(0L, 1000L, 16);
        check(due.contains("\"cursor\":2") && due.indexOf("fc4142") < due.indexOf("fc4344"),
                "decode-order records were lost or reordered: " + due);
        Path home = java.nio.file.Paths.get(args[0]).toAbsolutePath();
        System.setProperty("vibe.ffmpeg.sageHome", home.toString());
        RuntimePaths paths = RuntimePaths.detect();
        int port = freePort();
        int apiPort = freePort();
        CaptionSideChannelService service = new CaptionSideChannelService(paths);
        service.start(true, port, 1, 2, 64);
        MimDirectSessionService direct = new MimDirectSessionService(paths,
                new MimDirectSessionService.SourceAuthorizer() {
                    public boolean isAllowed(Path source) { return true; }
                });
        direct.start(false);
        CaptionSideChannelHttpServer http = new CaptionSideChannelHttpServer(
                "127.0.0.1", apiPort, service, direct);
        http.start();
        check(service.capabilitiesJson().contains("\"state\":\"ready\""),
                service.capabilitiesJson());
        check(service.capabilitiesJson().contains("\"sourceDatagrams\":0") &&
                service.capabilitiesJson().contains("\"retainedRecords\":0"),
                service.capabilitiesJson());
        Response capabilities = http("GET", apiPort, "/v1/capabilities", null);
        check(capabilities.status == 200 && capabilities.body.contains("\"state\":\"ready\""),
                capabilities.body);
        Response reserveResponse = http("POST", apiPort, "/v1/sessions/reserve", null);
        check(reserveResponse.status == 200 && reserveResponse.body.contains("reservationToken"),
                reserveResponse.body);
        check(http("POST", apiPort, "/v1/sessions/reserve", null).status == 409,
                "second ambiguous reservation was accepted");
        Path cancellable = only(paths.captionPoolDir, "available-" + port + "-*.slot");
        String cancellableName = cancellable.getFileName().toString();
        String cancellableToken = cancellableName.substring(
                ("available-" + port + "-").length(), cancellableName.length() - 5);
        check(http("POST", apiPort, "/v1/sessions/teardown?token=" + cancellableToken,
                null).status == 200, "pending reservation teardown failed");
        check(!Files.exists(cancellable), "pending advertisement survived teardown");
        reserveResponse = http("POST", apiPort, "/v1/sessions/reserve", null);
        check(reserveResponse.status == 200,
                "slot was not reusable after pending reservation teardown");
        Path available = only(paths.captionPoolDir, "available-" + port + "-*.slot");
        String name = available.getFileName().toString();
        String token = name.substring(("available-" + port + "-").length(), name.length() - 5);
        long pid = 424242L;
        Files.createDirectories(paths.statusDir);
        Files.write(paths.statusDir.resolve("job-" + pid + ".json"), "{}".getBytes("UTF-8"));
        Path claim = paths.captionPoolDir.resolve("job-" + pid + "-" + port + "-" + token + ".slot");
        Files.move(available, claim, StandardCopyOption.ATOMIC_MOVE);

        DatagramSocket sender = new DatagramSocket();
        for (int index = 0; index < 6; index++) {
            byte[] packet = pesPacket(90000L * (index + 1), index);
            sender.send(new DatagramPacket(packet, packet.length,
                    InetAddress.getByName("127.0.0.1"), port));
            Thread.sleep(25L);
        }
        sender.close();
        Thread.sleep(250L);
        Response claimResponse = http("POST", apiPort,
                "/v1/sessions/claim?reservationToken=" + token, null);
        check(claimResponse.status == 200 && claimResponse.body.contains(token), claimResponse.body);
        check(http("POST", apiPort, "/v1/sessions/claim?reservationToken=" + token,
                null).status == 409,
                "session was claimed twice");
        Response packetsResponse = http("GET", apiPort, "/v1/captions?token=" + token +
                "&cursor=0&untilMs=10000&limit=20", null);
        check(packetsResponse.status == 200, packetsResponse.body);
        String packets = packetsResponse.body;
        check(packets != null && packets.contains("fc4849"), packets);
        check(packets.contains("\"dropped\":"), packets);
        Response statusResponse = http("GET", apiPort, "/v1/sessions/status?token=" + token,
                null);
        check(statusResponse.status == 200 && statusResponse.body.contains("\"latestPtsMs\":"),
                statusResponse.body);
        check(statusResponse.body.contains("\"sourceDatagrams\":") &&
                !statusResponse.body.contains("\"sourceDatagrams\":0"), statusResponse.body);
        check(statusResponse.body.contains("\"sourceBytes\":"), statusResponse.body);
        String aggregate = service.capabilitiesJson();
        check(!aggregate.contains("\"sourceDatagrams\":0") &&
                !aggregate.contains("\"retainedRecords\":0") &&
                aggregate.contains("\"latestPtsMs\":"), aggregate);
        Response teardownResponse = http("POST", apiPort,
                "/v1/sessions/teardown?token=" + token, null);
        check(teardownResponse.status == 200 && teardownResponse.body.contains("\"released\""),
                teardownResponse.body);
        check(http("GET", apiPort, "/v1/sessions/status?token=" + token,
                null).status == 404, "released session remained visible");

        Files.delete(claim);
        Thread.sleep(350L);
        check(service.read(token, 0, 10000, 20) == null, "torn-down session remained readable");
        check(!Files.exists(claim), "claim survived MIM teardown");
        check(service.capabilitiesJson().contains("\"reservationAvailable\":true"),
                service.capabilitiesJson());
        http.stop();
        direct.close();
        service.close();
        check(!Files.exists(available), "advertised slot survived service shutdown");
        System.out.println("CaptionSideChannelServiceTest PASS");
    }

    private static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
}
