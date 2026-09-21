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
public final class MimDVDStreamTransformProvider implements DVDStreamTransformProvider {
    public static final String TRANSPORT_ID = "dvd_mpegts_v1";
    private static final long PROBE_CACHE_MILLIS = 30000L;
    private static volatile long lastProbeTime;
    private static volatile boolean lastAvailable;

    public String getTransportId() { return TRANSPORT_ID; }
    public String getOutputFormat() { return "mpegts"; }

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
        if (request == null || !TRANSPORT_ID.equals(request.getTransportId()))
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
        command.add("-vcodec"); command.add("mpeg4");
        command.add("-b:v"); command.add(request.getVideoBitrate());
        command.add("-acodec"); command.add("copy");
        command.add("-c:s"); command.add("dvbsub");
        command.add("-f"); command.add("mpegts");
        command.add("-");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(paths.runtimeDir.toFile());
        builder.environment().put("SAGETV_FFMPEG_MIM_INI", paths.ini.toString());
        log("starting optional DVD transform transport " + TRANSPORT_ID);
        return new Session(builder.start());
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
        private final OutputStream input;
        private final InputStream output;
        private final ArrayBlockingQueue<byte[]> outputQueue =
                new ArrayBlockingQueue<byte[]>(OUTPUT_QUEUE_CHUNKS);
        private volatile IOException outputFailure;
        private volatile boolean outputEnded;
        private volatile boolean inputClosed;

        Session(Process process) {
            this.process = process;
            input = process.getOutputStream();
            output = process.getInputStream();
            startOutputReader();
            startErrorReader(process.getErrorStream());
        }

        public void write(byte[] data, int offset, int length) throws IOException {
            if (outputFailure != null) throw outputFailure;
            if (inputClosed) throw new IOException("DVD transform input is already closed");
            input.write(data, offset, length);
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
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (IOException e) {
                        if (process.isAlive()) outputFailure = e;
                    } finally {
                        outputEnded = true;
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
