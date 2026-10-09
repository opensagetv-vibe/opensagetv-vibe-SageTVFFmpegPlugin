package org.opensagetv.vibe.ffmpeg;

import java.io.File;
import java.util.Locale;
import sage.SageTV;

/**
 * Read-only first stage of the optional MIM-DIRECT-005 recovery contract.
 *
 * <p>This is deliberately not wired to capability negotiation or an HTTP
 * endpoint. Capturing a source is not authorization to replay Watch: a later
 * bounded, canceled/latest-intent-aware ticket must prove that separately.
 * Only public stock APIs are used; the typed MediaFile stays server-local.</p>
 *
 * <p>The observed-state capture form needs an independently settled player,
 * not synchronous invocation inside
 * OPENURL/SEEK/replacement processing: stock time/state getters can cross the
 * MiniClient decoder/socket. A loading/failure snapshot cannot be assumed to
 * complete within the HTTP budget. The future startup recovery path needs
 * metadata/explicit-intent capture which avoids those decoder-owned reads;
 * {@link #captureIntent} supplies that read-only form, not a replay.</p>
 */
final class DirectWatchSnapshot {
    interface Api {
        Object global(String name, Object... args) throws Exception;
        Object ui(String context, String name, Object... args) throws Exception;
    }

    static final Api STOCK_API = new Api() {
        public Object global(String name, Object... args) throws Exception {
            return SageTV.api(name, args);
        }
        public Object ui(String context, String name, Object... args) throws Exception {
            return SageTV.apiUI(context, name, args);
        }
    };

    final String context;
    final Object mediaFile;
    final long mediaFileId;
    /** Non-DVD airing/wall-clock coordinate; not a file-relative offset. */
    final long absoluteMediaTimeMs;
    /** Relative to the current file segment; never silently substituted above. */
    final long rawMediaTimeMs;
    final boolean playing;
    /** True means client intent, not an observation of decoder position/state. */
    final boolean explicitIntent;

    private DirectWatchSnapshot(String context, Object mediaFile, long id,
                                long absoluteMs, long rawMs, boolean playing, boolean explicitIntent) {
        this.context=context; this.mediaFile=mediaFile; this.mediaFileId=id;
        this.absoluteMediaTimeMs=absoluteMs; this.rawMediaTimeMs=rawMs;
        this.playing=playing;
        this.explicitIntent=explicitIntent;
    }

    /** Return unavailable on incomplete/racing state, never select another UI. */
    static DirectWatchSnapshot capture(Api api, String clientId) {
        if (api == null) return null;
        String identity=normalize(clientId);
        if (!identity.matches("[0-9a-f]{12}")) return null;
        try {
            String context=exactContext(api.global("GetUIContextNames"), identity);
            if (context == null
                    || !Boolean.TRUE.equals(api.ui(context, "IsMediaPlayerFullyLoaded"))
                    || !Boolean.FALSE.equals(api.ui(context, "IsCurrentMediaFileDVD"))) return null;
            Object media=api.ui(context, "GetCurrentMediaFile");
            if (media == null) return null;
            long id=number(api.global("GetMediaFileID", media));
            long absolute=number(api.ui(context, "GetMediaTime"));
            long raw=number(api.ui(context, "GetRawMediaTime"));
            Object playing=api.ui(context, "IsPlaying");
            if (id <= 0 || absolute < 0 || raw < 0 || !(playing instanceof Boolean)) return null;
            // Snapshot only. These checks cannot establish an atomic replay or
            // rule out a later same-file Watch; ticket cancellation must do that.
            Object after=api.ui(context, "GetCurrentMediaFile");
            if (after == null || number(api.global("GetMediaFileID", after)) != id
                    || !context.equals(exactContext(api.global("GetUIContextNames"), identity))) return null;
            return new DirectWatchSnapshot(context, media, id, absolute, raw, (Boolean)playing, false);
        } catch (Exception unavailable) {
            return null;
        }
    }

    /**
     * Startup/replacement form: preserve explicit client intent without querying
     * decoder-owned clocks/state while Core awaits a MiniClient command reply.
     * The URL's source must also be the exact current UI MediaFile. Only single-
     * segment, non-DVD sources are accepted until segment-coordinate gates exist.
     * This neither proves playback nor permits a replay; ticket/coordinator
     * cancellation and source checks remain required.
     */
    static DirectWatchSnapshot captureIntent(Api api, String clientId, File source,
                                             long relativeMs, boolean requestedPlaying) {
        if (api == null || source == null || relativeMs < 0 || relativeMs > 14L*86400000L)
            return null;
        String identity=normalize(clientId);
        if (!identity.matches("[0-9a-f]{12}")) return null;
        try {
            String context=exactContext(api.global("GetUIContextNames"), identity);
            if (context == null) return null;
            // GetCurrentMediaFile returns VideoFrame.currFile directly. Do not
            // add IsPlaying/IsMediaPlayerFullyLoaded/GetMediaTime here: those
            // can enter MiniPlayer while its load/seek is awaiting our reply.
            Object current=api.ui(context,"GetCurrentMediaFile");
            Object resolved=api.global("GetMediaFileForFilePath",source);
            if (current == null || resolved == null) return null;
            long id=number(api.global("GetMediaFileID",current));
            if (id <= 0 || id != number(api.global("GetMediaFileID",resolved))
                    || number(api.global("GetNumberOfSegments",resolved)) !=1
                    || !Boolean.FALSE.equals(api.global("IsDVD",resolved))) return null;
            long start=number(api.global("GetFileStartTime",resolved));
            if (start < 0 || start > Long.MAX_VALUE-relativeMs) return null;
            Object after=api.ui(context,"GetCurrentMediaFile");
            if (after == null || id != number(api.global("GetMediaFileID",after))
                    || !context.equals(exactContext(api.global("GetUIContextNames"),identity))) return null;
            return new DirectWatchSnapshot(context,resolved,id,start+relativeMs,
                    relativeMs,requestedPlaying,true);
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static long number(Object value) {
        // Stock APIs return integral Number values. Null/text must not become0.
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long ? ((Number)value).longValue() : -1L;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replace(":", "").replace("-", "");
    }

    static boolean exactContextAvailable(Api api, String context) throws Exception {
        return context != null && context.equals(exactContext(
                api.global("GetUIContextNames"),normalize(context)));
    }

    private static String exactContext(Object names, String identity) {
        if (!(names instanceof Object[])) return null;
        String found=null;
        for (Object name : (Object[])names) {
            if (!(name instanceof String) || !identity.equals(normalize((String)name))) continue;
            if (found != null) return null;
            found=(String)name;
        }
        return found;
    }
}
