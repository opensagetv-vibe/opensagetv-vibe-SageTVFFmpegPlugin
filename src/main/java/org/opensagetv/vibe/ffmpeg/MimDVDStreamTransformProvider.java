package org.opensagetv.vibe.ffmpeg;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import sage.DVDStreamTransform;
import sage.DVDStreamTransformProvider;
import sage.DVDStreamTransformRequest;

/**
 * Optional DVD transform provider for updated SageTV Core builds.
 *
 * <p>This class is deliberately separate from {@link SageTVFFmpegPlugin}.
 * Stock SageTV does not contain the optional SPI and therefore never loads
 * this service class; the normal Standard plugin remains stock compatible.</p>
 */
public class MimDVDStreamTransformProvider implements DVDStreamTransformProvider {
    public static final String TRANSPORT_ID = "dvd_mpegts_v1";
    public static final String COPY_TRANSPORT_ID = "dvd_mim_copy_v1";
    public static final String TRANSCODE_TRANSPORT_ID = "dvd_mim_transcode_v1";
    static final String MODE_COPY = "copy";
    static final String MODE_TRANSCODE = "transcode";
    private static final long PROBE_CACHE_MILLIS = 30000L;
    private static volatile long lastProbeTime;
    private static volatile boolean lastAvailable;
    private static final Object STATUS_LOCK = new Object();
    private static final AtomicLong STATUS_SEQUENCE = new AtomicLong();
    private static final Map<Long, SessionStatus> SESSION_STATUS =
            new java.util.LinkedHashMap<Long, SessionStatus>();
    private final String transportId;
    private final String mode;
    private final String outputFormat;

    /** Legacy negotiated transport retained for already-deployed Vibe clients. */
    public MimDVDStreamTransformProvider() {
        this(TRANSPORT_ID, MODE_TRANSCODE, "mpegts");
    }

    protected MimDVDStreamTransformProvider(String transportId, String mode,
                                             String outputFormat) {
        this.transportId = transportId;
        this.mode = mode;
        this.outputFormat = outputFormat;
    }

    public String getTransportId() { return transportId; }
    public String getOutputFormat() { return outputFormat; }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastProbeTime < PROBE_CACHE_MILLIS) return lastAvailable;
        synchronized (MimDVDStreamTransformProvider.class) {
            now = System.currentTimeMillis();
            if (now - lastProbeTime < PROBE_CACHE_MILLIS) return lastAvailable;
            RuntimePaths paths = RuntimePaths.detect();
            MimRuntime runtime = new MimRuntime(paths);
            String health = runtime.health();
            if (!"healthy".equals(health)) {
                lastAvailable = false;
                lastProbeTime = now;
                log("DVD transform unavailable: " + health);
                return false;
            }
            MimRuntime.Snapshot capabilities = runtime.capabilities(true);
            Object advertised = capabilities.json.get("dvdStreamTransform");
            lastAvailable = capabilities.ok() && Boolean.TRUE.equals(advertised);
            lastProbeTime = now;
            if (!lastAvailable && capabilities.error != null)
                log("DVD transform unavailable: " + capabilities.error);
            return lastAvailable;
        }
    }

    public DVDStreamTransform open(DVDStreamTransformRequest request) throws IOException {
        if (request == null || !transportId.equals(request.getTransportId()))
            throw new IOException("unsupported DVD transform transport");
        RuntimePaths paths = RuntimePaths.detect();
        if (!java.nio.file.Files.isRegularFile(paths.mimExecutable))
            throw new IOException("MIM executable is missing: " + paths.mimExecutable);

        List<String> command = new ArrayList<String>();
        command.add(paths.mimExecutable.toString());
        command.add("-sagetvdiscstream");
        command.add("-f"); command.add("mpeg");
        command.add("-i"); command.add("-");
        command.add("-map"); command.add("0:v:0");
        command.add("-map"); command.add("0:a?");
        command.add("-map"); command.add("0:s?");
        if (MODE_COPY.equals(mode)) {
            command.add("-c:v"); command.add("copy");
            command.add("-c:a"); command.add("copy");
            command.add("-c:s"); command.add("copy");
            // Keep the authored MPEG-PS/SPU representation intact. The MIM
            // owns and remuxes the finite VM segment, but Direct Copy must not
            // decode or encode the DVD's video or audio.  FFmpeg's generic
            // "mpeg" muxer writes MPEG-1 PES headers; feeding those bytes to a
            // DVD/VOB extractor makes the MPEG-1 PTS bytes look like the
            // MPEG-2 PES flags/header length and produces false timestamps and
            // audio types after a menu-to-title transition.  The "vob" muxer
            // writes the DVD-compatible MPEG-2 program-stream representation
            // while still performing a pure stream copy.
            command.add("-f"); command.add("vob");
        } else {
            command.add("-vcodec"); command.add("mpeg4");
            command.add("-b:v"); command.add(request.getVideoBitrate());
            command.add("-acodec"); command.add("copy");
            command.add("-c:s"); command.add("dvbsub");
            command.add("-f"); command.add("mpegts");
        }
        command.add("-");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(paths.runtimeDir.toFile());
        builder.environment().put("SAGETV_FFMPEG_MIM_INI", paths.ini.toString());
        log("starting optional DVD transform transport " + transportId +
                " mode=" + mode);
        SessionStatus status = registerStatus(mode, transportId);
        try {
            return new Session(builder.start(), status);
        } catch (IOException failure) {
            status.state = "start_failed";
            throw failure;
        }
    }

    /** Bounded, credential-free proof for the LAN-scoped commissioning API. */
    static String statusJson(String requestedMode) {
        String selected = requestedMode == null ? "" : requestedMode.trim().toLowerCase();
        SessionStatus status = null;
        synchronized (STATUS_LOCK) {
            for (SessionStatus candidate : SESSION_STATUS.values()) {
                if ((selected.length() == 0 || selected.equals(candidate.mode)) &&
                        (status == null || candidate.startedAtMs > status.startedAtMs))
                    status = candidate;
            }
        }
        if (status == null)
            return "{\"state\":\"idle\",\"mode\":\"" + json(selected) +
                    "\",\"executionPath\":\"unknown\",\"bytesIn\":0,\"bytesOut\":0}";
        String path = MODE_COPY.equals(status.mode) ? "copy"
                : resolveTranscodePath(status.startedAtMs);
        return "{\"state\":\"" + json(status.state) + "\"" +
                ",\"mode\":\"" + json(status.mode) + "\"" +
                ",\"transportId\":\"" + json(status.transportId) + "\"" +
                ",\"executionPath\":\"" + json(path) + "\"" +
                ",\"bytesIn\":" + status.bytesIn +
                ",\"bytesOut\":" + status.bytesOut +
                ",\"startedAtMs\":" + status.startedAtMs + "}";
    }

    private static SessionStatus registerStatus(String mode, String transportId) {
        SessionStatus status = new SessionStatus(STATUS_SEQUENCE.incrementAndGet(),
                mode, transportId, System.currentTimeMillis());
        synchronized (STATUS_LOCK) {
            SESSION_STATUS.put(status.sequence, status);
            while (SESSION_STATUS.size() > 32)
                SESSION_STATUS.remove(SESSION_STATUS.keySet().iterator().next());
        }
        return status;
    }

    private static String resolveTranscodePath(long startedAtMs) {
        Map<String, Object> snapshot = new MimRuntime(RuntimePaths.detect())
                .status(false).json;
        Map<String, Object> best = null;
        long bestStarted = Long.MIN_VALUE;
        Object active = snapshot.get("activeJobs");
        if (active instanceof List) {
            for (Object value : (List<?>) active) {
                Map<String, Object> job = statusJob(value, startedAtMs);
                long jobStarted = number(job == null ? null : job.get("startedEpochMs"));
                if (job != null && jobStarted > bestStarted) {
                    best = job; bestStarted = jobStarted;
                }
            }
        }
        for (String name : new String[]{"lastJob", "lastTranscodeJob"}) {
            Map<String, Object> job = statusJob(snapshot.get(name), startedAtMs);
            long jobStarted = number(job == null ? null : job.get("startedEpochMs"));
            if (job != null && jobStarted > bestStarted) {
                best = job; bestStarted = jobStarted;
            }
        }
        if (best == null) return "unknown";
        boolean hardwareDecode = Boolean.TRUE.equals(best.get("hardwareDecode"));
        boolean hardwareEncode = Boolean.TRUE.equals(best.get("hardwareEncode"));
        String backend = String.valueOf(best.get("backend"));
        if ("copy".equals(backend)) return "copy";
        if (hardwareDecode && hardwareEncode) return "full_gpu";
        if (hardwareEncode) return "mixed";
        return "software";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> statusJob(Object value, long startedAtMs) {
        if (!(value instanceof Map)) return null;
        Map<String, Object> job = (Map<String, Object>) value;
        long jobStarted = number(job.get("startedEpochMs"));
        String format = String.valueOf(job.get("outputFormat"));
        if (jobStarted < startedAtMs - 5000L ||
                !("mpegts".equalsIgnoreCase(format) || "mpeg".equalsIgnoreCase(format)))
            return null;
        return job;
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.MIN_VALUE;
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static final class SessionStatus {
        final long sequence;
        final String mode;
        final String transportId;
        final long startedAtMs;
        volatile String state = "active";
        volatile long bytesIn;
        volatile long bytesOut;

        SessionStatus(long sequence, String mode, String transportId, long startedAtMs) {
            this.sequence = sequence;
            this.mode = mode;
            this.transportId = transportId;
            this.startedAtMs = startedAtMs;
        }
    }

    static boolean capabilitiesAdvertiseTransform(Map<String, Object> capabilities) {
        return capabilities != null && Boolean.TRUE.equals(capabilities.get("dvdStreamTransform"));
    }

    private static void log(String message) {
        System.out.println("[SageTVFFmpegPlugin] " + message);
    }

    private static final class Session implements DVDStreamTransform {
        private static final int OUTPUT_CHUNK_SIZE = 32768;
        private static final int OUTPUT_QUEUE_CHUNKS = 128;
        private final Process process;
        private final SessionStatus status;
        private final OutputStream input;
        private final InputStream output;
        private final ArrayBlockingQueue<byte[]> outputQueue =
                new ArrayBlockingQueue<byte[]>(OUTPUT_QUEUE_CHUNKS);
        private volatile IOException outputFailure;
        private volatile boolean outputEnded;
        private volatile boolean inputClosed;

        Session(Process process, SessionStatus status) {
            this.process = process;
            this.status = status;
            input = process.getOutputStream();
            output = process.getInputStream();
            startOutputReader();
            startErrorReader(process.getErrorStream());
        }

        public void write(byte[] data, int offset, int length) throws IOException {
            if (outputFailure != null) throw outputFailure;
            if (inputClosed) throw new IOException("DVD transform input is already closed");
            input.write(data, offset, length);
            status.bytesIn += length;
        }

        public byte[] pollOutput(long timeoutMillis) throws IOException {
            if (outputFailure != null) throw outputFailure;
            try {
                return timeoutMillis <= 0 ? outputQueue.poll()
                        : outputQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted waiting for DVD transform output", e);
            }
        }

        public boolean isOutputEnded() { return outputEnded && outputQueue.isEmpty(); }

        public void closeInput() {
            if (inputClosed) return;
            inputClosed = true;
            try { input.close(); } catch (IOException ignored) {}
        }

        public void close() {
            closeInput();
            try { output.close(); } catch (IOException ignored) {}
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            outputQueue.clear();
            status.state = "released";
        }

        private void startOutputReader() {
            Thread reader = new Thread(new Runnable() {
                public void run() {
                    byte[] buffer = new byte[OUTPUT_CHUNK_SIZE];
                    try {
                        int read;
                        while ((read = output.read(buffer)) >= 0) {
                            if (read == 0) continue;
                            byte[] chunk = new byte[read];
                            System.arraycopy(buffer, 0, chunk, 0, read);
                            outputQueue.put(chunk);
                            status.bytesOut += read;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (IOException e) {
                        if (process.isAlive()) outputFailure = e;
                    } finally {
                        outputEnded = true;
                        if (!"released".equals(status.state))
                            status.state = outputFailure == null ? "complete" : "failed";
                    }
                }
            }, "DVD-Transform-Output");
            reader.setDaemon(true);
            reader.start();
        }

        private void startErrorReader(final InputStream errors) {
            Thread reader = new Thread(new Runnable() {
                public void run() {
                    byte[] buffer = new byte[4096];
                    try {
                        int read;
                        while ((read = errors.read(buffer)) >= 0) {
                            if (read > 0)
                                log(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        }
                    } catch (IOException ignored) {
                    } finally {
                        try { errors.close(); } catch (IOException ignored) {}
                    }
                }
            }, "DVD-Transform-Errors");
            reader.setDaemon(true);
            reader.start();
        }
    }
}
