package org.opensagetv.vibe.ffmpeg;

import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Owns optional Android MIM Direct playback sessions.
 *
 * <p>The stock SageTV MiniClient connection remains the authority for the UI,
 * watched state, and commands.  This service owns only the bounded media
 * representation.  It invokes the separately installed MIM executable and
 * exposes completed MPEG-TS HLS artifacts through the LAN-scoped plugin
 * HTTP listener.  Source paths must resolve to a SageTV MediaFile through the
 * injected authorizer; callers cannot use this API as an arbitrary file
 * server or command launcher.</p>
 */
final class MimDirectSessionService implements Closeable {
    static final int CONTRACT_VERSION = 1;
    static final int MAX_SESSIONS = 4;
    static final int PLAYLIST_SEGMENTS = 90;
    static final long RESTART_HANDOFF_GRACE_MS = 15_000L;
    static final long LIVE_EDGE_PREROLL_MS = 4_000L;
    private static final long UNLISTED_SEGMENT_GRACE_MS = 15_000L;
    private static final long ABANDONED_SESSION_MS = 6L * 60L * 60L * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RuntimePaths paths;
    private final SourceAuthorizer authorizer;
    private final CaptionSideChannelService captions;
    private final MimRuntime runtime;
    private final Map<String, Session> sessions = new LinkedHashMap<String, Session>();
    private final Map<String, Session> retiredSessions =
            new LinkedHashMap<String, Session>();
    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "vibe-mim-direct-cleanup");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private volatile boolean enabled;

    interface SourceAuthorizer { boolean isAllowed(Path source); }

    MimDirectSessionService(RuntimePaths paths, SourceAuthorizer authorizer) {
        this(paths, authorizer, null);
    }

    MimDirectSessionService(RuntimePaths paths, SourceAuthorizer authorizer,
                            CaptionSideChannelService captions) {
        this.paths = paths;
        this.authorizer = authorizer;
        this.captions = captions;
        this.runtime = new MimRuntime(paths);
    }

    synchronized void start(boolean requested) throws IOException {
        stopAll();
        enabled = requested && Files.isRegularFile(paths.mimExecutable)
                && Boolean.TRUE.equals(runtime.capabilities(true).json.get("ownedDirectStreams"));
        if (enabled) Files.createDirectories(paths.directMediaDir);
        cleanupStaleDirectories();
    }

    synchronized String capabilitiesJson() {
        return "{\"contractVersion\":" + CONTRACT_VERSION +
                ",\"available\":" + enabled +
                ",\"modes\":[\"copy\",\"transcode\"]" +
                ",\"deinterlacePolicies\":[\"auto\",\"on\",\"off\"]" +
                ",\"container\":\"hls-mpegts\"" +
                ",\"activeSessions\":" + sessions.size() + "}";
    }

    Session create(String sourceValue, String mode, long startMs) throws IOException {
        return create(sourceValue, mode, startMs, "auto");
    }

    Session create(String sourceValue, String mode, long startMs,
                   String deinterlaceValue) throws IOException {
        return create(sourceValue, mode, startMs, deinterlaceValue, false);
    }

    Session create(String sourceValue, String mode, long startMs,
                   String deinterlaceValue, boolean active) throws IOException {
        if (!enabled) throw new IllegalStateException("mim_direct_disabled");
        String selected = mode == null ? "" : mode.trim().toLowerCase();
        if (!"copy".equals(selected) && !"transcode".equals(selected))
            throw new IllegalArgumentException("invalid_direct_mode");
        String deinterlace = normalizeDeinterlace(deinterlaceValue);
        if (startMs < 0L || startMs > 14L * 86400000L)
            throw new IllegalArgumentException("invalid_start_ms");
        Path source;
        try { source = new File(sourceValue == null ? "" : sourceValue).getCanonicalFile().toPath(); }
        catch (IOException invalid) { throw new IllegalArgumentException("invalid_source_path"); }
        if (!Files.isRegularFile(source) || !Files.isReadable(source))
            throw new IllegalArgumentException("source_not_readable");
        if (authorizer == null || !authorizer.isAllowed(source))
            throw new IllegalArgumentException("source_not_in_sagetv_library");

        String captionReservation = "";
        if ("transcode".equals(selected) && captions != null) {
            if (!captions.isOperational())
                throw new IllegalStateException("direct_caption_tap_unavailable");
            captionReservation = captions.reserve();
            if (captionReservation == null)
                throw new IllegalStateException("direct_caption_slot_unavailable");
        }
        final long effectiveStartMs = boundedStartMs(source, startMs);
        final Session session;
        try {
            synchronized (this) {
                reapFinished();
                if (sessions.size() >= MAX_SESSIONS)
                    throw new IllegalStateException("direct_session_limit");
                String token = randomToken();
                Path directory = paths.directMediaDir.resolve(token).toAbsolutePath().normalize();
                if (!directory.startsWith(paths.directMediaDir.toAbsolutePath().normalize()))
                    throw new IOException("unsafe_direct_session_path");
                Files.createDirectories(directory);
                session = new Session(token, source, selected, deinterlace,
                        active, startMs, effectiveStartMs, directory,
                        captionReservation);
                sessions.put(token, session);
            }
        } catch (IOException failure) {
            cancelCaptionReservation(captionReservation);
            throw failure;
        } catch (RuntimeException failure) {
            cancelCaptionReservation(captionReservation);
            throw failure;
        }
        try {
            session.launch();
            try {
                session.awaitPlaylist(15000L);
            } catch (IOException firstStartupFailure) {
                // SageTV's timeshift bit is advisory. During a live-program
                // transition it can still describe the just-closed recording
                // as active. MIM intentionally exits an -activefile job with
                // no output in that state. Retry that one proven condition
                // once as a completed file instead of abandoning the owned
                // transport and falling all the way back to MiniClient Pull.
                if (!session.retryCompletedAfterStaleActiveHint())
                    throw firstStartupFailure;
                session.awaitPlaylist(15000L);
            }
            session.claimCaption(3000L);
            return session;
        } catch (IOException failure) {
            release(session.token);
            throw failure;
        }
    }

    /**
     * Bound an owned seek to a playable point in the media that currently
     * exists. This is especially important for growing recordings: MIM can
     * successfully recover from an out-of-range {@code -ss}, but the client
     * must be told the effective offset or SageTV's timeline remains at the
     * impossible requested value. Keep a small preroll so the replacement HLS
     * representation contains enough A/V to open and continue growing.
     */
    private long boundedStartMs(Path source, long requestedStartMs) {
        if (requestedStartMs <= 0L || !Files.isRegularFile(paths.ffprobeExecutable))
            return requestedStartMs;
        Process probe = null;
        try {
            probe = new ProcessBuilder(paths.ffprobeExecutable.toString(),
                    "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    source.toString()).redirectErrorStream(true).start();
            // A duration-only probe emits only a few bytes. Wait first so the
            // timeout also bounds a stuck probe instead of blocking forever
            // while reading its still-open stdout pipe.
            if (!probe.waitFor(5000L, TimeUnit.MILLISECONDS)) {
                probe.destroyForcibly();
                return requestedStartMs;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream input = probe.getInputStream();
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) >= 0 && output.size() < 4096)
                output.write(buffer, 0, Math.min(count, 4096 - output.size()));
            input.close();
            if (probe.exitValue() != 0) return requestedStartMs;
            String[] lines = new String(output.toByteArray(), StandardCharsets.UTF_8)
                    .trim().split("\\R");
            for (String line : lines) {
                try {
                    double seconds = Double.parseDouble(line.trim());
                    if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0.0)
                        continue;
                    long durationMs = Math.max(0L, Math.round(seconds * 1000.0));
                    if (requestedStartMs >= durationMs)
                        return Math.max(0L, durationMs - LIVE_EDGE_PREROLL_MS);
                    return requestedStartMs;
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) {
            return requestedStartMs;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return requestedStartMs;
        } finally {
            if (probe != null && probe.isAlive()) probe.destroyForcibly();
        }
        return requestedStartMs;
    }

    synchronized Session get(String token) { return sessions.get(token); }

    synchronized Session getMedia(String token) {
        Session active = sessions.get(token);
        return active == null ? retiredSessions.get(token) : active;
    }

    synchronized boolean release(String token) {
        Session session = sessions.remove(token);
        if (session == null) session = retiredSessions.remove(token);
        if (session == null) return false;
        session.stop();
        deleteTree(session.directory);
        return true;
    }

    Session restart(String token, long startMs) throws IOException {
        return restart(token, startMs, null);
    }

    Session restart(String token, long startMs, String deinterlaceValue) throws IOException {
        Session current;
        synchronized (this) { current = sessions.get(token); }
        if (current == null) throw new IllegalArgumentException("unknown_or_finished_session");
        String replacementPolicy = deinterlaceValue == null ||
                deinterlaceValue.trim().length() == 0
                ? current.deinterlace : normalizeDeinterlace(deinterlaceValue);
        Session replacement = create(current.source.toString(), current.mode,
                startMs, replacementPolicy, current.active);
        retireForHandoff(token);
        return replacement;
    }

    private void retireForHandoff(final String token) {
        final Session retired;
        synchronized (this) {
            retired = sessions.remove(token);
            if (retired == null) return;
            retired.stop();
            retired.state = "handoff";
            retiredSessions.put(token, retired);
        }
        cleanupExecutor.schedule(new Runnable() {
            public void run() {
                synchronized (MimDirectSessionService.this) {
                    if (retiredSessions.get(token) != retired) return;
                    retiredSessions.remove(token);
                }
                deleteTree(retired.directory);
            }
        }, RESTART_HANDOFF_GRACE_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void stopAll() {
        for (Session session : new ArrayList<Session>(sessions.values())) {
            session.stop(); deleteTree(session.directory);
        }
        for (Session session : new ArrayList<Session>(retiredSessions.values())) {
            session.stop(); deleteTree(session.directory);
        }
        sessions.clear();
        retiredSessions.clear();
    }

    public synchronized void close() { stopAll(); enabled = false; }

    private synchronized void reapFinished() {
        List<String> finished = new ArrayList<String>();
        for (Session session : sessions.values())
            if (System.currentTimeMillis() - session.createdAtMs > ABANDONED_SESSION_MS)
                finished.add(session.token);
        for (String token : finished) release(token);
    }

    private void cleanupStaleDirectories() throws IOException {
        if (!Files.isDirectory(paths.directMediaDir)) return;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(paths.directMediaDir)) {
            for (Path entry : entries) deleteTree(entry);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try {
            if (Files.isDirectory(root)) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
                    for (Path child : children) deleteTree(child);
                }
            }
            Files.deleteIfExists(root);
        } catch (IOException ignored) { }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte value : bytes) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    static String normalizeDeinterlace(String value) {
        String selected = value == null ? "" : value.trim().toLowerCase();
        if (selected.length() == 0) return "auto";
        if (!"auto".equals(selected) && !"on".equals(selected) &&
                !"off".equals(selected))
            throw new IllegalArgumentException("invalid_deinterlace_policy");
        return selected;
    }

    private void cancelCaptionReservation(String token) {
        if (captions == null || token == null || token.length() == 0) return;
        try { captions.teardown(token); }
        catch (RuntimeException ignored) { }
    }

    final class Session {
        final String token;
        final Path source;
        final String mode;
        final String deinterlace;
        final boolean active;
        final long requestedStartMs;
        final long startMs;
        final Path directory;
        final Path playlist;
        final String captionReservationToken;
        final long createdAtMs = System.currentTimeMillis();
        volatile Process process;
        volatile long finishedAtMs;
        volatile int exitCode = Integer.MIN_VALUE;
        volatile String state = "starting";
        volatile String error = "";
        volatile String captionSessionToken = "";
        volatile boolean activeFileApplied;

        Session(String token, Path source, String mode, String deinterlace,
                boolean active, long requestedStartMs, long startMs, Path directory,
                String captionReservationToken) {
            this.token = token; this.source = source; this.mode = mode;
            this.deinterlace = deinterlace;
            this.active = active;
            this.requestedStartMs = requestedStartMs;
            this.startMs = startMs; this.directory = directory;
            this.captionReservationToken = captionReservationToken == null
                    ? "" : captionReservationToken;
            this.playlist = directory.resolve("stream.m3u8");
        }

        void launch() throws IOException {
            launch(active);
        }

        private void launch(boolean useActiveFile) throws IOException {
            List<String> command = command(useActiveFile);
            Path stderr = directory.resolve("mim-stderr.log");
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(paths.sageHome.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("mim-stdout.log").toFile()));
            builder.redirectError(ProcessBuilder.Redirect.appendTo(stderr.toFile()));
            final Process launchedProcess = builder.start();
            process = launchedProcess;
            activeFileApplied = useActiveFile;
            exitCode = Integer.MIN_VALUE;
            finishedAtMs = 0L;
            error = "";
            state = "running";
            Thread monitor = new Thread(new Runnable() {
                public void run() {
                    int launchedExitCode = Integer.MIN_VALUE;
                    String launchedError = "";
                    try {
                        while (!launchedProcess.waitFor(5L, TimeUnit.SECONDS))
                            cleanupUnlistedSegments();
                        launchedExitCode = launchedProcess.exitValue();
                        cleanupUnlistedSegments();
                    }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        launchedError = "monitor_interrupted";
                    }
                    synchronized (Session.this) {
                        // A stale-active retry can install a replacement
                        // process before this monitor publishes its result.
                        // Never let the retired process overwrite the active
                        // attempt's observable lifecycle state.
                        if (process != launchedProcess) return;
                        exitCode = launchedExitCode;
                        if (launchedError.length() != 0) error = launchedError;
                        finishedAtMs = System.currentTimeMillis();
                        state = exitCode == 0 ? "complete" : "failed";
                        if (exitCode != 0 && error.length() == 0)
                            error = "mim_exit_" + exitCode;
                    }
                }
            }, "vibe-mim-direct-" + token.substring(0, 8));
            monitor.setDaemon(true);
            monitor.start();
        }

        private List<String> command(boolean useActiveFile) {
            List<String> command = new ArrayList<String>();
            Collections.addAll(command, paths.mimExecutable.toString(), "-hide_banner",
                    "-loglevel", "warning", "-sagetvdirect", "-re");
            // MIM's active-file contract enables bounded probing, growing-file
            // following, and the safer live decode policy. Without it, a
            // newly tuned recording can reach its current edge during GPU
            // startup and leave behind a short, undecodable H.264 fragment.
            if (useActiveFile) command.add("-activefile");
            if ("transcode".equals(mode)) {
                command.add("-sagetvdeinterlace");
                command.add(deinterlace);
            }
            if (!captionReservationToken.isEmpty()) command.add("-stdinctrl");
            if (startMs > 0L) {
                command.add("-ss"); command.add(String.format(java.util.Locale.US, "%.3f", startMs / 1000.0));
            }
            Collections.addAll(command, "-i", source.toString(),
                    "-map", "0:v:0?", "-map", "0:a?", "-map", "0:s?");
            if ("copy".equals(mode)) {
                Collections.addAll(command, "-c:v", "copy", "-c:a", "copy", "-c:s", "copy");
            } else {
                Collections.addAll(command, "-c:v", "libx264", "-preset", "veryfast",
                        "-b:v", "6000k", "-maxrate", "8000k", "-bufsize", "12000k",
                        // Direct Transcode is an interactive HLS transport.
                        // Keep the GOP bounded so the first complete segment
                        // has a decodable keyframe within roughly one second
                        // for common 25/29.97/30 fps broadcast sources. MIM
                        // preserves -g when it maps this request to a hardware
                        // encoder; legacy Fixed and Direct Copy are untouched.
                        "-g", "30",
                        "-a53cc", "1", "-c:a", "ac3", "-b:a", "192k", "-c:s", "copy");
            }
            /*
             * FFmpeg's HLS muxer always routes mapped subtitle streams through
             * its WebVTT child muxer. That rejects DVB bitmap and DVB Teletext
             * copy streams even when the requested HLS segment type is MPEG-TS.
             * The segment muxer writes the same live M3U8 plus real MPEG-TS
             * segments while retaining those broadcast subtitle PIDs. This is
             * intentionally a Direct-session-only change; established SageTV
             * Fixed commands and the MIM compatibility policy are untouched.
             */
            Collections.addAll(command, "-f", "segment", "-segment_time", "2",
                    "-segment_format", "mpegts", "-segment_list_type", "m3u8",
                    "-segment_list_flags", "+live", "-segment_list_size",
                    Integer.toString(PLAYLIST_SEGMENTS), "-segment_list",
                    playlist.toString(), "-y",
                    directory.resolve("seg_%06d.ts").toString());
            return command;
        }

        /**
         * Retry only MIM's clean, media-less active-file exit. A running job,
         * a nonzero child failure, or a completed playable segment is not a
         * stale SageTV hint and must retain the original startup failure.
         */
        private boolean retryCompletedAfterStaleActiveHint() throws IOException {
            Process attempted = process;
            if (!active || !activeFileApplied || attempted == null ||
                    attempted.isAlive()) return false;
            int attemptedExit;
            try { attemptedExit = attempted.exitValue(); }
            catch (IllegalThreadStateException stillRunning) { return false; }
            if (attemptedExit != 0 || playlistReady()) return false;
            clearStartupArtifacts();
            state = "retrying_completed_after_stale_active_hint";
            launch(false);
            return true;
        }

        private void clearStartupArtifacts() throws IOException {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) {
                    Path name = entry.getFileName();
                    String value = name == null ? "" : name.toString();
                    if ("stream.m3u8".equals(value) ||
                            "mim-stdout.log".equals(value) ||
                            "mim-stderr.log".equals(value) ||
                            value.matches("seg_[0-9]{6}\\.ts"))
                        Files.deleteIfExists(entry);
                }
            }
        }

        /**
         * Retire only segments older than the published window. An unlisted
         * newer file can still be the segment muxer's current open output.
         * Copy input can pause longer than the retention grace (for example
         * around a seek/keyframe); age alone must never unlink that writer.
         * Use the monotonically numbered playlist boundary rather than OS
         * open-file inspection so the same rule holds on Linux and Windows.
         */
        void cleanupUnlistedSegments() {
            if (!Files.isRegularFile(playlist)) return;
            final Set<String> referenced = new HashSet<String>();
            try {
                String firstPublished = null;
                for (String line : Files.readAllLines(playlist, StandardCharsets.UTF_8)) {
                    String value = line == null ? "" : line.trim();
                    if (value.matches("seg_[0-9]{6}\\.ts")) {
                        referenced.add(value);
                        if (firstPublished == null || value.compareTo(firstPublished) < 0)
                            firstPublished = value;
                    }
                }
                // A transient empty/partial playlist offers no safe retirement
                // boundary. Teardown still removes the entire owned directory.
                if (firstPublished == null) return;
                long cutoff = System.currentTimeMillis() - UNLISTED_SEGMENT_GRACE_MS;
                try (DirectoryStream<Path> entries =
                             Files.newDirectoryStream(directory, "seg_*.ts")) {
                    for (Path entry : entries) {
                        Path name = entry.getFileName();
                        if (name == null || !name.toString().matches("seg_[0-9]{6}\\.ts") ||
                                referenced.contains(name.toString()) ||
                                name.toString().compareTo(firstPublished) >= 0) continue;
                        FileTime modified = Files.getLastModifiedTime(entry);
                        if (modified.toMillis() <= cutoff) Files.deleteIfExists(entry);
                    }
                }
            } catch (IOException ignored) {
                // Playback remains authoritative. A later monitor pass or the
                // session teardown will retry/complete cleanup.
            }
        }

        void awaitPlaylist(long timeoutMs) throws IOException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                // FFmpeg creates the M3U8 before its first MPEG-TS segment is
                // publishable. A nonempty, header-only playlist is not a
                // playable session and Media3 correctly rejects it as a
                // malformed HLS container. Do not expose the media URL until
                // at least one safe listed segment exists and contains data.
                if (playlistReady()) {
                    state = "ready"; return;
                }
                Process active = process;
                if (active != null && !active.isAlive())
                    throw new IOException("MIM Direct ended before producing media (exit " + exitCode + ")");
                try { Thread.sleep(50L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IOException("MIM Direct startup interrupted");
                }
            }
            throw new IOException("MIM Direct playlist startup timed out");
        }

        private boolean playlistReady() {
            if (!Files.isRegularFile(playlist) || playlistSize() <= 0L) return false;
            try {
                for (String line : Files.readAllLines(playlist, StandardCharsets.UTF_8)) {
                    String value = line == null ? "" : line.trim();
                    if (!value.matches("seg_[0-9]{6}\\.ts")) continue;
                    Path segment = directory.resolve(value).normalize();
                    if (segment.startsWith(directory) && Files.isRegularFile(segment) &&
                            Files.size(segment) > 0L && segmentPlayable(segment))
                        return true;
                }
            } catch (IOException incompleteWrite) {
                // The writer may be replacing the live manifest. Retry until
                // the bounded startup deadline instead of exposing it early.
            }
            return false;
        }

        private boolean segmentPlayable(Path segment) {
            // Copy sessions retain the source codecs and can include audio-only
            // media. For Transcode, however, the contract promises H.264 video.
            // Prove that FFprobe can read nonzero dimensions before exposing
            // the URL. This rejects the small TS fragment produced when a GPU
            // encoder fails during its first frame even if MIM contains the
            // child failure as a clean wrapper exit.
            if (!"transcode".equals(mode)) return true;
            if (!Files.isRegularFile(paths.ffprobeExecutable)) return false;
            Process probe = null;
            try {
                probe = new ProcessBuilder(paths.ffprobeExecutable.toString(),
                        "-v", "error", "-select_streams", "v:0",
                        "-show_entries", "stream=width,height",
                        "-of", "csv=p=0", segment.toString())
                        .redirectErrorStream(true).start();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                InputStream input = probe.getInputStream();
                byte[] buffer = new byte[512];
                int count;
                while ((count = input.read(buffer)) >= 0 && output.size() < 4096)
                    output.write(buffer, 0, Math.min(count, 4096 - output.size()));
                input.close();
                if (!probe.waitFor(2000L, TimeUnit.MILLISECONDS)) {
                    probe.destroyForcibly();
                    return false;
                }
                if (probe.exitValue() != 0) return false;
                String dimensions = new String(output.toByteArray(),
                        StandardCharsets.UTF_8).trim();
                // MPEG-TS FFprobe output can list the selected stream once
                // under its program and once in the global stream table,
                // separated by a blank line. Any proven nonzero-dimension
                // video line is sufficient; requiring the entire output to
                // be one record rejects valid broadcast TS segments.
                for (String line : dimensions.split("\\R")) {
                    if (line.trim().matches("[1-9][0-9]*,[1-9][0-9]*"))
                        return true;
                }
                return false;
            } catch (IOException invalidMedia) {
                return false;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                if (probe != null && probe.isAlive()) probe.destroyForcibly();
            }
        }

        void claimCaption(long timeoutMs) throws IOException {
            if (captionReservationToken.isEmpty()) return;
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                String claimed = captions == null ? null
                        : captions.claim(captionReservationToken);
                if (claimed != null) {
                    captionSessionToken = claimed;
                    return;
                }
                try { Thread.sleep(50L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Direct caption claim interrupted");
                }
            }
            throw new IOException("Direct caption tap startup timed out");
        }

        private long playlistSize() {
            try { return Files.size(playlist); } catch (IOException ignored) { return 0L; }
        }

        void stop() {
            String captionToken = captionSessionToken.length() == 0
                    ? captionReservationToken : captionSessionToken;
            captionSessionToken = "";
            if (captions != null && captionToken.length() != 0) {
                try { captions.teardown(captionToken); }
                catch (RuntimeException ignored) { }
            }
            Process active = process;
            if (active != null && active.isAlive()) {
                try {
                    // MIM owns FFmpeg's control pipe. Ask it to stop normally
                    // first so it can terminate the child, publish final
                    // status, and release its caption-slot claim. Destroying
                    // the process first skips all three native cleanup paths.
                    active.getOutputStream().write("STOP\n".getBytes(StandardCharsets.US_ASCII));
                    active.getOutputStream().flush();
                    if (!active.waitFor(2000L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        active.destroy();
                        if (!active.waitFor(1000L, java.util.concurrent.TimeUnit.MILLISECONDS))
                            active.destroyForcibly();
                    }
                } catch (IOException controlFailure) {
                    active.destroy();
                    try {
                        if (!active.waitFor(1000L, java.util.concurrent.TimeUnit.MILLISECONDS))
                            active.destroyForcibly();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        active.destroyForcibly();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); active.destroyForcibly();
                }
            }
            if (captions != null && captionReservationToken.length() != 0)
                captions.releaseTransport(captionReservationToken);
            state = "released";
        }

        String json() {
            return "{\"contractVersion\":" + CONTRACT_VERSION +
                    ",\"sessionToken\":\"" + jsonEscape(token) + "\"" +
                    ",\"mode\":\"" + jsonEscape(mode) + "\"" +
                    ",\"deinterlace\":\"" + jsonEscape(deinterlace) + "\"" +
                    ",\"active\":" + active +
                    ",\"activeFileApplied\":" + activeFileApplied +
                    ",\"state\":\"" + jsonEscape(state) + "\"" +
                    ",\"requestedStartMs\":" + requestedStartMs +
                    ",\"startMs\":" + startMs +
                    ",\"mediaUrl\":\"/v1/direct/media/" + jsonEscape(token) + "/stream.m3u8\"" +
                    ",\"captionSessionToken\":\"" +
                    jsonEscape(captionSessionToken) + "\"" +
                    ",\"createdAtMs\":" + createdAtMs +
                    ",\"finishedAtMs\":" + finishedAtMs +
                    ",\"exitCode\":" + exitCode +
                    ",\"execution\":" + executionJson() +
                    ",\"error\":\"" + jsonEscape(error) + "\"}";
        }

        private String executionJson() {
            Map<String, Object> job = matchingJob(runtime.status(false).json);
            if (job == null) {
                return "{\"state\":\"pending\",\"path\":\"unknown\"," +
                        "\"backend\":\"\",\"encoder\":\"\"," +
                        "\"hardwareDecode\":false,\"hardwareEncode\":false}";
            }
            boolean hardwareDecode = Boolean.TRUE.equals(job.get("hardwareDecode"));
            boolean hardwareEncode = Boolean.TRUE.equals(job.get("hardwareEncode"));
            String backend = string(job.get("backend"));
            String path;
            if ("copy".equals(backend)) path = "copy";
            else if (hardwareDecode && hardwareEncode) path = "full_gpu";
            else if (hardwareEncode) path = "mixed";
            else path = "software";
            return "{\"state\":\"" + jsonEscape(string(job.get("state"))) + "\"" +
                    ",\"path\":\"" + path + "\"" +
                    ",\"backend\":\"" + jsonEscape(backend) + "\"" +
                    ",\"encoder\":\"" + jsonEscape(string(job.get("encoder"))) + "\"" +
                    ",\"hardwareDecode\":" + hardwareDecode +
                    ",\"hardwareEncode\":" + hardwareEncode + "}";
        }

        private Map<String, Object> matchingJob(Map<String, Object> status) {
            Map<String, Object> best = null;
            long bestStarted = Long.MIN_VALUE;
            Object active = status.get("activeJobs");
            if (active instanceof List) {
                for (Object candidate : (List<?>) active) {
                    Map<String, Object> match = matchingMap(candidate);
                    long started = number(match == null ? null : match.get("startedEpochMs"));
                    if (match != null && started > bestStarted) {
                        best = match; bestStarted = started;
                    }
                }
            }
            for (String name : new String[]{"lastJob", "lastTranscodeJob"}) {
                Map<String, Object> match = matchingMap(status.get(name));
                long started = number(match == null ? null : match.get("startedEpochMs"));
                if (match != null && started > bestStarted) {
                    best = match; bestStarted = started;
                }
            }
            return best;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> matchingMap(Object value) {
            if (!(value instanceof Map)) return null;
            Map<String, Object> job = (Map<String, Object>) value;
            if (!source.toString().equals(string(job.get("input")))) return null;
            // Direct media is an HLS playlist backed by the FFmpeg segment
            // muxer. Older prototypes reported `hls`; the commissioned MIM
            // correctly reports its actual output muxer as `segment`. Accept
            // both so execution proof does not remain `pending` while a real
            // owned segment job is active.
            String outputFormat = string(job.get("outputFormat"));
            if (!"segment".equalsIgnoreCase(outputFormat) &&
                    !"hls".equalsIgnoreCase(outputFormat)) return null;
            long started = number(job.get("startedEpochMs"));
            return started >= createdAtMs - 5000L ? job : null;
        }

        private long number(Object value) {
            return value instanceof Number ? ((Number) value).longValue() : Long.MIN_VALUE;
        }

        private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    }

    static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
