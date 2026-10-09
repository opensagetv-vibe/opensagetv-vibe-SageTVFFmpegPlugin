package org.opensagetv.vibe.ffmpeg;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional stock-SageTV caption transport. The service binds loopback UDP
 * listeners before advertising atomic MIM slots. A claimed slot receives only
 * the original compressed video copy, extracts A/53 triples, and retains a
 * strictly bounded timed-record queue. No media payload is exposed by its API.
 */
final class CaptionSideChannelService implements Closeable {
    static final int CONTRACT_VERSION = 1;
    private static final long RESERVATION_TIMEOUT_MS = 60000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RuntimePaths paths;
    private final Map<String, Session> sessions = new HashMap<String, Session>();
    private final List<Slot> slots = new ArrayList<Slot>();
    private Path statusDirectory;
    private Path captionPoolDirectory;
    private volatile boolean running;
    private volatile String state = "disabled";
    private volatile String error = "";

    CaptionSideChannelService(RuntimePaths paths) {
        this.paths = paths;
        statusDirectory = paths.statusDir;
        captionPoolDirectory = paths.captionPoolDir;
    }

    synchronized void start(boolean enabled, int portBase, int slotCount,
                            int maxRecords, int maxBytes) {
        start(enabled, portBase, slotCount, maxRecords, maxBytes,
                paths.statusDir, paths.captionPoolDir);
    }

    synchronized void start(boolean enabled, int portBase, int slotCount,
                            int maxRecords, int maxBytes, Path statusDir, Path poolDir) {
        stopQuietly();
        statusDirectory = statusDir.toAbsolutePath().normalize();
        captionPoolDirectory = poolDir.toAbsolutePath().normalize();
        if (!enabled) {
            state = "disabled";
            error = "";
            return;
        }
        int safeBase = Math.max(1024, Math.min(65000, portBase));
        int safeSlots = Math.max(1, Math.min(8, slotCount));
        int safeRecords = Math.max(64, Math.min(32768, maxRecords));
        int safeBytes = Math.max(16384, Math.min(4 * 1024 * 1024, maxBytes));
        running = true;
        error = "";
        try {
            Files.createDirectories(captionPoolDirectory);
            cleanupStalePoolFiles();
            for (int index = 0; index < safeSlots; index++) {
                Slot slot = new Slot(safeBase + index, safeRecords, safeBytes);
                try {
                    slot.start();
                    slots.add(slot);
                } catch (IOException bindFailure) {
                    error = compact(bindFailure);
                }
            }
            state = slots.isEmpty() ? "error" : (error.length() == 0 ? "ready" : "degraded");
        } catch (IOException failure) {
            error = compact(failure);
            state = "error";
            running = false;
        }
    }

    synchronized void stopQuietly() {
        running = false;
        for (Slot slot : new ArrayList<Slot>(slots)) {
            try { slot.close(); } catch (IOException ignored) { }
        }
        slots.clear();
        sessions.clear();
    }

    public synchronized void close() { stopQuietly(); state = "disabled"; }

    synchronized void fail(Throwable failure) {
        stopQuietly();
        error = compact(failure);
        state = "error";
    }

    synchronized boolean isOperational() {
        return running && !slots.isEmpty() && ("ready".equals(state) || "degraded".equals(state));
    }

    synchronized String capabilitiesJson() {
        long sourceDatagrams = 0L;
        long retainedRecords = 0L;
        long latestPtsMs = -1L;
        for (Session session : sessions.values()) {
            synchronized (session) {
                sourceDatagrams += session.sourceDatagrams;
                retainedRecords += session.records.size();
                if (!session.records.isEmpty())
                    latestPtsMs = Math.max(latestPtsMs, session.records.getLast().ptsMs);
            }
        }
        return "{\"contractVersion\":" + CONTRACT_VERSION +
                ",\"state\":\"" + json(state) + "\",\"listeners\":" + slots.size() +
                ",\"activeSessions\":" + sessions.size() +
                ",\"sourceDatagrams\":" + sourceDatagrams +
                ",\"retainedRecords\":" + retainedRecords +
                ",\"latestPtsMs\":" + latestPtsMs +
                ",\"reservationAvailable\":" + reservationAvailable() +
                ",\"error\":\"" + json(error) + "\"}";
    }

    synchronized String reserve() throws IOException {
        // Advertising at most one unclaimed slot at a time makes the next MIM
        // process and the requesting Vibe client an unambiguous pair. Active
        // jobs can still use the remaining bound listeners concurrently.
        for (Slot slot : slots) if (slot.hasPendingReservation()) return null;
        for (Slot slot : slots) {
            String token = slot.reserve();
            if (token != null) return token;
        }
        return null;
    }

    synchronized String read(String token, long cursor, long untilMs, int limit) {
        if (token == null || token.length() < 24 || cursor < 0 || untilMs < 0)
            throw new IllegalArgumentException("Invalid caption session request");
        Session session = sessions.get(token);
        if (session == null) return null;
        return session.read(cursor, untilMs, Math.max(1, Math.min(1024, limit)));
    }

    synchronized String claim(String reservationToken) {
        if (reservationToken == null || reservationToken.length() < 24)
            throw new IllegalArgumentException("Invalid caption reservation token");
        Session session = sessions.get(reservationToken);
        if (session == null || session.claimedByClient) return null;
        session.claimedByClient = true;
        return session.token;
    }

    synchronized String sessionStatus(String token) {
        if (token == null || token.length() < 24) throw new IllegalArgumentException("Invalid caption session token");
        Session session = sessions.get(token);
        return session == null ? null : session.status();
    }

    synchronized boolean teardown(String token) {
        if (token == null || token.length() < 24) throw new IllegalArgumentException("Invalid caption session token");
        Session session = sessions.remove(token);
        if (session != null) {
            session.closeClient();
            return true;
        }
        // A client may disconnect before MIM claims its advertised slot. Let
        // that authenticated client cancel the pending reservation instead of
        // blocking the next connection until the bounded expiry elapses.
        for (Slot slot : slots) {
            try {
                if (slot.cancelPending(token)) return true;
            } catch (IOException failure) {
                recordError(failure);
                return false;
            }
        }
        return false;
    }

    /**
     * Releases the transport reservation after its owning MIM process has
     * stopped. Normal MIM shutdown removes the claim itself; this bounded
     * cleanup also covers a forced process termination, where native cleanup
     * handlers cannot run and an otherwise-dead claim would block every later
     * Direct session until the server restarted.
     */
    synchronized void releaseTransport(String reservationToken) {
        if (reservationToken == null || reservationToken.length() < 24) return;
        for (Slot slot : slots) {
            try {
                if (slot.releaseTransport(reservationToken)) return;
            } catch (IOException failure) {
                recordError(failure);
                return;
            }
        }
    }

    private synchronized void activate(Session session) { sessions.put(session.token, session); }
    private synchronized void deactivate(Session session) {
        if (session != null && sessions.get(session.token) == session) sessions.remove(session.token);
    }

    private boolean reservationAvailable() {
        for (Slot slot : slots) if (slot.hasPendingReservation()) return false;
        for (Slot slot : slots) if (slot.isIdle()) return true;
        return false;
    }

    private void cleanupStalePoolFiles() throws IOException {
        if (!Files.isDirectory(captionPoolDirectory)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(captionPoolDirectory, "*.slot")) {
            for (Path path : entries) {
                String name = path.getFileName().toString();
                if (name.startsWith("available-")) Files.deleteIfExists(path);
                else if (name.startsWith("job-")) {
                    long pid = jobPid(name);
                    if (pid <= 0 || !Files.isRegularFile(statusDirectory.resolve("job-" + pid + ".json")))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    private final class Slot implements Closeable, Runnable {
        final int port;
        final int maxRecords;
        final int maxBytes;
        volatile DatagramSocket socket;
        volatile Thread thread;
        volatile String token;
        volatile Path available;
        volatile Path claim;
        volatile Session session;
        volatile long reservedAtMs;

        Slot(int port, int maxRecords, int maxBytes) {
            this.port = port;
            this.maxRecords = maxRecords;
            this.maxBytes = maxBytes;
        }

        void start() throws IOException {
            DatagramSocket bound = new DatagramSocket(null);
            bound.setReuseAddress(false);
            // MIM's copied-video tap can burst much faster than playback even
            // when the HLS output is paced. Linux's default ~208 KiB UDP
            // receive queue can discard TS packets during those bursts,
            // silently removing individual CEA-608 character/control pairs.
            // Request a bounded queue before binding; the OS may cap it.
            bound.setReceiveBufferSize(4 * 1024 * 1024);
            bound.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            System.out.println("[SageTVFFmpegPlugin] caption tap UDP receive buffer=" +
                    bound.getReceiveBufferSize() + " port=" + port);
            bound.setSoTimeout(200);
            socket = bound;
            Thread worker = new Thread(this, "vibe-caption-slot-" + port);
            worker.setDaemon(true);
            thread = worker;
            worker.start();
        }

        public void run() {
            byte[] bytes = new byte[65535];
            while (running && socket != null && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                    socket.receive(packet);
                    refreshClaim();
                    Session active = session;
                    if (active != null) active.push(packet.getData(), packet.getOffset(), packet.getLength());
                } catch (SocketTimeoutException timeout) {
                    try { refreshClaim(); } catch (IOException failure) { recordError(failure); }
                } catch (IOException failure) {
                    if (running && socket != null && !socket.isClosed()) recordError(failure);
                } catch (RuntimeException failure) {
                    recordError(failure);
                }
            }
            finishSession();
        }

        synchronized boolean hasPendingReservation() {
            return token != null && claim == null && session == null;
        }

        synchronized boolean isIdle() {
            return token == null && claim == null && session == null;
        }

        synchronized String reserve() throws IOException {
            if (!isIdle()) return null;
            advertise();
            return token;
        }

        synchronized boolean cancelPending(String expectedToken) throws IOException {
            if (expectedToken == null || !expectedToken.equals(token)
                    || claim != null || session != null)
                return false;
            clearReservation();
            return true;
        }

        synchronized boolean releaseTransport(String expectedToken) throws IOException {
            if (expectedToken == null || !expectedToken.equals(token)) return false;
            Path claimed = claim;
            if (claimed != null) Files.deleteIfExists(claimed);
            finishSession();
            clearReservation();
            return true;
        }

        private void refreshClaim() throws IOException {
            // All cross-object locking is service -> slot. HTTP reservation
            // calls use the same order, avoiding an API/receiver deadlock.
            synchronized (CaptionSideChannelService.this) {
                synchronized (this) {
                    Path current = claim;
                    if (current != null && Files.isRegularFile(current)) return;
                    if (current != null) {
                        finishSession();
                        clearReservation();
                        return;
                    }
                    if (token == null) return;
                    String suffix = "-" + port + "-" + token + ".slot";
                    try (DirectoryStream<Path> entries = Files.newDirectoryStream(captionPoolDirectory,
                            "job-*-" + port + "-" + token + ".slot")) {
                        for (Path path : entries) {
                            String name = path.getFileName().toString();
                            if (!name.endsWith(suffix)) continue;
                            long pid = jobPid(name);
                            if (pid <= 0) continue;
                            claim = path;
                            Session created = new Session(token, pid, maxRecords, maxBytes);
                            session = created;
                            activate(created);
                            return;
                        }
                    }
                    if (System.currentTimeMillis() - reservedAtMs >= RESERVATION_TIMEOUT_MS)
                        clearReservation();
                }
            }
        }

        private synchronized void advertise() throws IOException {
            token = randomToken();
            reservedAtMs = System.currentTimeMillis();
            available = captionPoolDirectory.resolve("available-" + port + "-" + token + ".slot");
            Files.write(available,
                    ("contractVersion=" + CONTRACT_VERSION + "\nport=" + port + "\n").getBytes(StandardCharsets.US_ASCII),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        private void finishSession() {
            Session active = session;
            session = null;
            claim = null;
            if (active != null) {
                try { active.parser.finish(); } catch (IOException failure) { recordError(failure); }
                deactivate(active);
            }
        }

        private synchronized void clearReservation() throws IOException {
            Path advertised = available;
            available = null;
            if (advertised != null) Files.deleteIfExists(advertised);
            token = null;
            reservedAtMs = 0L;
        }

        public void close() throws IOException {
            DatagramSocket activeSocket = socket;
            socket = null;
            if (activeSocket != null) activeSocket.close();
            Thread worker = thread;
            if (worker != null && worker != Thread.currentThread()) {
                try { worker.join(1500L); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            clearReservation();
            finishSession();
        }
    }

    static final class Session {
        final String token;
        final long mimPid;
        final long startedAtMs = System.currentTimeMillis();
        final int maxRecords;
        final int maxBytes;
        final ArrayDeque<Record> records = new ArrayDeque<Record>();
        final A53CaptionParser parser;
        long nextSequence = 1;
        long dropped;
        int retainedBytes;
        long sourceDatagrams;
        long sourceBytes;
        boolean claimedByClient;
        boolean clientClosed;

        Session(String token, long mimPid, int maxRecords, int maxBytes) {
            this.token = token;
            this.mimPid = mimPid;
            this.maxRecords = maxRecords;
            this.maxBytes = maxBytes;
            parser = new A53CaptionParser(new A53CaptionParser.Sink() {
                public void packet(long ptsMs, byte[] triples) { offer(ptsMs, triples); }
            });
        }

        synchronized void push(byte[] bytes, int offset, int count) throws IOException {
            if (clientClosed) return;
            sourceDatagrams++;
            sourceBytes += count;
            parser.push(bytes, offset, count);
        }

        synchronized void offer(long ptsMs, byte[] triples) {
            if (clientClosed) return;
            if (triples == null || triples.length == 0 || triples.length > 93 || triples.length % 3 != 0)
                return;
            byte[] copy = triples.clone();
            while (!records.isEmpty() &&
                    (records.size() >= maxRecords || retainedBytes + copy.length > maxBytes)) {
                retainedBytes -= records.removeFirst().data.length;
                dropped++;
            }
            if (copy.length > maxBytes) { dropped++; return; }
            Record record = new Record(nextSequence++, ptsMs, copy);
            records.addLast(record);
            retainedBytes += copy.length;
        }

        synchronized String read(long cursor, long untilMs, int limit) {
            long oldest = records.isEmpty() ? nextSequence : records.getFirst().sequence;
            boolean reset = cursor > 0 && cursor < oldest - 1;
            long effectiveCursor = reset ? oldest - 1 : cursor;
            StringBuilder output = new StringBuilder("{\"contractVersion\":1,\"packets\":[");
            int emitted = 0;
            long nextCursor = effectiveCursor;
            for (Record record : records) {
                if (record.sequence <= effectiveCursor) continue;
                // MPEG video arrives in decode order, so B-frame presentation
                // timestamps need not increase with record sequence. Never
                // advance the cursor past an earlier, not-yet-due record:
                // doing so permanently loses its CEA-608 control/text bytes.
                if (record.ptsMs > untilMs + 1000L) break;
                if (emitted++ >= limit) break;
                if (emitted > 1) output.append(',');
                output.append('[').append(record.sequence).append(',').append(record.ptsMs)
                      .append(",\"").append(hex(record.data)).append("\"]");
                nextCursor = record.sequence;
            }
            return output.append("],\"cursor\":").append(nextCursor)
                    .append(",\"reset\":").append(reset)
                    .append(",\"dropped\":").append(dropped)
                    .append(",\"source\":\"original compressed video before GPU decode\"}")
                    .toString();
        }

        synchronized String status() {
            long firstPts = records.isEmpty() ? -1L : records.getFirst().ptsMs;
            long latestPts = records.isEmpty() ? -1L : records.getLast().ptsMs;
            return "{\"contractVersion\":1,\"state\":\"active\",\"startedAtMs\":" +
                    startedAtMs + ",\"firstPtsMs\":" + firstPts +
                    ",\"latestPtsMs\":" + latestPts + ",\"nextCursor\":" + nextSequence +
                    ",\"sourceDatagrams\":" + sourceDatagrams +
                    ",\"sourceBytes\":" + sourceBytes +
                    ",\"retainedRecords\":" + records.size() +
                    ",\"retainedBytes\":" + retainedBytes +
                    ",\"dropped\":" + dropped + "}";
        }

        synchronized void closeClient() {
            clientClosed = true;
            records.clear();
            retainedBytes = 0;
        }
    }

    static final class Record {
        final long sequence;
        final long ptsMs;
        final byte[] data;
        Record(long sequence, long ptsMs, byte[] data) {
            this.sequence = sequence;
            this.ptsMs = ptsMs;
            this.data = data;
        }
    }

    private synchronized void recordError(Throwable failure) {
        error = compact(failure);
        if (!"error".equals(state)) state = "degraded";
    }

    private static long jobPid(String name) {
        if (name == null || !name.startsWith("job-")) return -1;
        int separator = name.indexOf('-', 4);
        if (separator < 0) return -1;
        try { return Long.parseLong(name.substring(4, separator)); }
        catch (NumberFormatException invalid) { return -1; }
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return hex(bytes);
    }

    private static String hex(byte[] bytes) {
        final char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = digits[value >>> 4];
            output[index * 2 + 1] = digits[value & 15];
        }
        return new String(output);
    }

    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String compact(Throwable failure) {
        if (failure == null) return "unknown";
        String value = failure.getMessage();
        if (value == null || value.trim().length() == 0) value = failure.getClass().getSimpleName();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}
