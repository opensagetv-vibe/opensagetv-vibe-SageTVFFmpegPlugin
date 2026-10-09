package org.opensagetv.vibe.ffmpeg;

/**
 * Staged public-API restore, not yet exposed/enabled by MIM negotiation.
 *
 * <p>The caller must first establish a fresh ordinary-Fixed client connection
 * and serialize its latest-intent/cancellation events. Watch acceptance and a
 * queued Seek are not physical playback PASS. The second stage is called only
 * after independent readiness evidence, never by polling decoder-owned APIs
 * inside a synchronous MiniClient OPENURL/SEEK handler.</p>
 */
final class DirectWatchRestoreCoordinator {
    enum Result { WATCH_REQUESTED, SEEK_REQUESTED, NOT_READY, OCCUPIED_CONTEXT,
                  STALE_TICKET, CANCELED, API_FAILURE }
    private final DirectWatchSnapshot.Api api;
    private final DirectWatchRecoveryTickets tickets;

    DirectWatchRestoreCoordinator(DirectWatchSnapshot.Api api,DirectWatchRecoveryTickets tickets) {
        if (api ==null || tickets ==null) throw new IllegalArgumentException("recovery_dependencies_required");
        this.api=api; this.tickets=tickets;
    }

    synchronized Result requestWatch(String handle,String context,long mediaId,long intent) {
        DirectWatchSnapshot snapshot=tickets.pending(handle,context,mediaId,intent);
        if (snapshot ==null) return Result.STALE_TICKET;
        try {
            if (!DirectWatchSnapshot.exactContextAvailable(api,context)) return Result.NOT_READY;
            // Never overwrite a still-loaded old session or a user's new Watch,
            // including a new Watch of the very same file. The required fresh
            // connection must have no current MediaFile before this request.
            if (api.ui(context,"GetCurrentMediaFile") !=null) return Result.OCCUPIED_CONTEXT;
            if (tickets.claim(handle,context,mediaId,intent) ==null
                    || !tickets.beginWatch(handle,context,mediaId,intent)) return Result.STALE_TICKET;
            api.ui(context,"Watch",snapshot.mediaFile);
            if (!tickets.isClaimCurrent(handle,context,mediaId,intent)) return Result.CANCELED;
            // Stock Watch launches AsyncWatch and apiUI returns its task marker,
            // not loaded playback. Pause here could run before that job installs
            // the player and be discarded. Preserve pause intent at the ready
            // stage instead; never inspect/private-wait on Catbert's task marker.
            return Result.WATCH_REQUESTED;
        } catch (Exception failed) {
            // An API failure is not permission to repeat an ordered Watch.
            tickets.cancel(handle,context,mediaId,intent);
            return Result.API_FAILURE;
        }
    }

    synchronized Result requestSeekAfterReady(String handle,String context,long mediaId,long intent) {
        DirectWatchSnapshot snapshot=tickets.claimed(handle,context,mediaId,intent);
        if (snapshot ==null) return Result.STALE_TICKET;
        try {
            if (!DirectWatchSnapshot.exactContextAvailable(api,context)) return Result.NOT_READY;
            Object current=api.ui(context,"GetCurrentMediaFile");
            if (current ==null) return Result.NOT_READY;
            Object actual=api.global("GetMediaFileID",current);
            if (!(actual instanceof Number) || ((Number)actual).longValue() !=mediaId) {
                tickets.cancel(handle,context,mediaId,intent);
                return Result.OCCUPIED_CONTEXT;
            }
            if (!tickets.beginSeek(handle,context,mediaId,intent)) return Result.STALE_TICKET;
            if (!snapshot.playing) {
                // Now the caller has independently witnessed the replacement
                // player. Queue exactly one Pause before Seek, so its queued
                // TIME_SET preserves pause rather than resuming accidentally.
                // A failed Pause consumes the stage: repeated Pause frame-steps.
                api.ui(context,"Pause");
                if (!tickets.isClaimCurrent(handle,context,mediaId,intent)) return Result.CANCELED;
                current=api.ui(context,"GetCurrentMediaFile");
                actual=current ==null ? null : api.global("GetMediaFileID",current);
                if (!DirectWatchSnapshot.exactContextAvailable(api,context)
                        || !(actual instanceof Number) || ((Number)actual).longValue() !=mediaId) {
                    tickets.cancel(handle,context,mediaId,intent);
                    return Result.OCCUPIED_CONTEXT;
                }
            }
            // Non-DVD public Seek expects the original absolute coordinate.
            // Neither the file-relative raw clock nor the literal offset is
            // substituted here. Actual landing remains an independent gate.
            api.ui(context,"Seek",Long.valueOf(snapshot.absoluteMediaTimeMs));
            if (!tickets.isClaimCurrent(handle,context,mediaId,intent)) return Result.CANCELED;
            tickets.complete(handle,context,mediaId,intent);
            return Result.SEEK_REQUESTED;
        } catch (Exception failed) {
            tickets.cancel(handle,context,mediaId,intent);
            return Result.API_FAILURE;
        }
    }
}
