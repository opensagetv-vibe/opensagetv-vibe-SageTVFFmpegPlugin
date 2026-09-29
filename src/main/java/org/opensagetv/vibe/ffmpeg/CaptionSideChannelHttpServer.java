package org.opensagetv.vibe.ffmpeg;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** LAN-scoped API for the bounded caption and owned-media services. */
final class CaptionSideChannelHttpServer {
    private final String bindAddress;
    private final int port;
    private final CaptionSideChannelService service;
    private final MimDirectSessionService direct;
    private HttpServer server;
    private ExecutorService executor;

    CaptionSideChannelHttpServer(String bindAddress, int port,
                                 CaptionSideChannelService service,
                                 MimDirectSessionService direct) {
        if (bindAddress == null || bindAddress.trim().length() == 0)
            throw new IllegalArgumentException("Caption API bind address is empty");
        if (port < 1024 || port > 65535)
            throw new IllegalArgumentException("Caption API port is outside 1024-65535");
        this.bindAddress = bindAddress;
        this.port = port;
        this.service = service;
        this.direct = direct;
    }

    synchronized void start() throws IOException {
        if (server != null) return;
        InetAddress address = InetAddress.getByName(bindAddress);
        server = HttpServer.create(new InetSocketAddress(address, port), 16);
        server.createContext("/v1/capabilities", new CapabilitiesHandler());
        server.createContext("/v1/sessions/reserve", new ReserveHandler());
        server.createContext("/v1/sessions/claim", new ClaimHandler());
        server.createContext("/v1/sessions/status", new StatusHandler());
        server.createContext("/v1/sessions/teardown", new TeardownHandler());
        server.createContext("/v1/captions", new CaptionsHandler());
        server.createContext("/v1/direct/start", new DirectStartHandler());
        server.createContext("/v1/direct/status", new DirectStatusHandler());
        server.createContext("/v1/direct/restart", new DirectRestartHandler());
        server.createContext("/v1/direct/teardown", new DirectTeardownHandler());
        server.createContext("/v1/direct/media", new DirectMediaHandler());
        server.createContext("/v1/dvd/status", new DvdStatusHandler());
        executor = Executors.newFixedThreadPool(8, new ThreadFactory() {
            private int sequence;
            public synchronized Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "vibe-caption-http-" + (++sequence));
                thread.setDaemon(true);
                return thread;
            }
        });
        server.setExecutor(executor);
        server.start();
    }

    private final class ReserveHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed"));
                return;
            }
            String reservationToken = service.reserve();
            if (reservationToken == null) {
                send(exchange, 409, error("no_caption_slot_available"));
                return;
            }
            send(exchange, 200, "{\"contractVersion\":1,\"reservationToken\":\"" +
                    json(reservationToken) + "\",\"expiresInMs\":60000}");
        }
    }

    synchronized void stop() {
        if (server != null) server.stop(1);
        server = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private final class CapabilitiesHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed"));
                return;
            }
            String captions = service.capabilitiesJson();
            String combined = captions.substring(0, captions.length() - 1) +
                    ",\"mimDirect\":" + direct.capabilitiesJson() + "}";
            send(exchange, 200, combined);
        }
    }

    private final class DirectStartHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed")); return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                MimDirectSessionService.Session session = direct.create(
                        required(query, "source"), required(query, "mode"),
                        number(query.get("startMs"), 0L, 0L, 14L * 86400000L),
                        query.get("deinterlace"));
                send(exchange, 200, session.json());
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            } catch (IllegalStateException unavailable) {
                send(exchange, 409, error(unavailable.getMessage()));
            } catch (IOException failure) {
                send(exchange, 502, error("mim_direct_start_failed"));
            }
        }
    }

    private final class DirectStatusHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed")); return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                MimDirectSessionService.Session session = direct.get(required(query, "token"));
                if (session == null) send(exchange, 404, error("unknown_or_finished_session"));
                else send(exchange, 200, session.json());
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private final class DirectRestartHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed")); return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                MimDirectSessionService.Session session = direct.restart(
                        required(query, "token"),
                        number(query.get("startMs"), 0L, 0L, 14L * 86400000L),
                        query.get("deinterlace"));
                send(exchange, 200, session.json());
            } catch (IllegalArgumentException invalid) {
                send(exchange, 404, error(invalid.getMessage()));
            } catch (IllegalStateException unavailable) {
                send(exchange, 409, error(unavailable.getMessage()));
            } catch (IOException failure) {
                send(exchange, 502, error("mim_direct_restart_failed"));
            }
        }
    }

    private final class DirectTeardownHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed")); return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                if (!direct.release(required(query, "token")))
                    send(exchange, 404, error("unknown_or_finished_session"));
                else send(exchange, 200, "{\"ok\":true,\"state\":\"released\"}");
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private final class DirectMediaHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed")); return;
            }
            String prefix = "/v1/direct/media/";
            String requestPath = exchange.getRequestURI().getPath();
            String suffix = requestPath.startsWith(prefix)
                    ? requestPath.substring(prefix.length()) : "";
            String[] parts = suffix.split("/", -1);
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}") ||
                    !("stream.m3u8".equals(parts[1]) ||
                            parts[1].matches("seg_[0-9]{6}\\.ts"))) {
                send(exchange, 404, error("unknown_media")); return;
            }
            MimDirectSessionService.Session session = direct.getMedia(parts[0]);
            if (session == null) { send(exchange, 404, error("unknown_media")); return; }
            Path file = session.directory.resolve(parts[1]).normalize();
            if (!file.startsWith(session.directory) || !Files.isRegularFile(file)) {
                send(exchange, 404, error("media_not_ready")); return;
            }
            sendFile(exchange, file, parts[1].endsWith(".m3u8")
                    ? "application/vnd.apple.mpegurl" : "video/mp2t");
        }
    }

    private final class DvdStatusHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed")); return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                String mode = query.get("mode");
                if (mode != null && !("copy".equals(mode) || "transcode".equals(mode))) {
                    send(exchange, 400, error("invalid_dvd_mode")); return;
                }
                send(exchange, 200, MimDVDStreamTransformProvider.statusJson(mode));
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private final class ClaimHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed"));
                return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                String sessionToken = service.claim(required(query, "reservationToken"));
                if (sessionToken == null) {
                    send(exchange, 409, error("fixed_session_not_ready_or_already_claimed"));
                    return;
                }
                send(exchange, 200, "{\"contractVersion\":1,\"sessionToken\":\"" +
                        json(sessionToken) + "\"}");
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private final class CaptionsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed"));
                return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                String token = required(query, "token");
                long cursor = number(query.get("cursor"), 0L, 0L, Long.MAX_VALUE);
                long untilMs = number(query.get("untilMs"), 0L, 0L, 7L * 86400000L);
                int limit = (int) number(query.get("limit"), 256L, 1L, 1024L);
                String result = service.read(token, cursor, untilMs, limit);
                if (result == null) send(exchange, 404, error("unknown_or_finished_session"));
                else send(exchange, 200, result);
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private final class StatusHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed"));
                return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                String result = service.sessionStatus(required(query, "token"));
                if (result == null) send(exchange, 404, error("unknown_or_finished_session"));
                else send(exchange, 200, result);
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private final class TeardownHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, error("method_not_allowed"));
                return;
            }
            try {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                if (!service.teardown(required(query, "token")))
                    send(exchange, 404, error("unknown_or_finished_session"));
                else send(exchange, 200, "{\"ok\":true,\"state\":\"released\"}");
            } catch (IllegalArgumentException invalid) {
                send(exchange, 400, error(invalid.getMessage()));
            }
        }
    }

    private static Map<String, String> parseQuery(String raw) throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (raw == null || raw.length() == 0) return values;
        for (String part : raw.split("&")) {
            int separator = part.indexOf('=');
            String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), "UTF-8");
            String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), "UTF-8");
            if (key.length() == 0 || values.containsKey(key))
                throw new IllegalArgumentException("invalid_or_duplicate_parameter");
            values.put(key, value);
        }
        return values;
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.length() == 0) throw new IllegalArgumentException("missing_" + name);
        return value;
    }

    private static long number(String value, long fallback, long minimum, long maximum) {
        if (value == null || value.length() == 0) return fallback;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException("number_out_of_range");
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid_number");
        }
    }

    private static String error(String message) {
        return "{\"ok\":false,\"error\":\"" + json(message == null ? "request_failed" : message) + "\"}";
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] payload = (json + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, payload.length);
        OutputStream output = exchange.getResponseBody();
        try { output.write(payload); }
        finally { output.close(); }
    }

    private static void sendFile(HttpExchange exchange, Path file,
                                 String contentType) throws IOException {
        long length = Files.size(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, length);
        InputStream input = Files.newInputStream(file, StandardOpenOption.READ);
        OutputStream output = exchange.getResponseBody();
        try {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        } finally {
            try { input.close(); } finally { output.close(); }
        }
    }

    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
