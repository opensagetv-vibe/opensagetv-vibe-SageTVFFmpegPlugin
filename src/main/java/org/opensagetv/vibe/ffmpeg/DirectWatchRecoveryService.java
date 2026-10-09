package org.opensagetv.vibe.ffmpeg;

import java.io.File;
import java.util.function.LongSupplier;

/**
 * Optional, bounded public-API recovery boundary. No normal MCP dependency.
 *
 * <p>Only the exact UI's current, library-resolved, single-segment non-DVD
 * MediaFile can be reserved. Paths and typed source objects stay server-local.
 * A reservation is not permission to replace another Watch: the coordinator
 * requires an empty fresh context and revalidates source/intent at each stage.
 * The client must serialize latest intent/cancel and supply independently
 * witnessed replacement readiness before requesting the final stage.</p>
 *
 * <p>Production negotiation is deliberately not enabled by constructing this
 * class. HTTP wiring and end-to-end physical failure gates must precede any
 * MPEG-2 source-acceptance contract. A missing/older service retains Fixed.</p>
 */
final class DirectWatchRecoveryService {
    static final int CONTRACT_VERSION=1;
    private final DirectWatchSnapshot.Api api;
    private final DirectWatchRecoveryTickets tickets;
    private final DirectWatchRestoreCoordinator coordinator;
    private boolean closed;

    static final class Reservation {
        final String handle;
        final String context;
        final long mediaFileId;
        final long intent;
        Reservation(String handle,DirectWatchSnapshot snapshot,long intent) {
            this.handle=handle; this.context=snapshot.context;
            this.mediaFileId=snapshot.mediaFileId; this.intent=intent;
        }
    }

    DirectWatchRecoveryService(DirectWatchSnapshot.Api api,LongSupplier monotonicMs) {
        if (api ==null) throw new IllegalArgumentException("recovery_api_required");
        this.api=api;
        this.tickets=new DirectWatchRecoveryTickets(monotonicMs);
        this.coordinator=new DirectWatchRestoreCoordinator(api,tickets);
    }

    /** No decoder-state queries: safe to call while Core awaits OPENURL/SEEK. */
    synchronized Reservation reserve(String clientId,String source,long relativeMs,
                                     boolean playing,long intent) {
        if (closed || source ==null || source.isEmpty() || source.length()>4096
                || source.indexOf('\0')>=0 || source.indexOf('\r')>=0 || source.indexOf('\n')>=0
                || intent<0) return null;
        DirectWatchSnapshot snapshot=DirectWatchSnapshot.captureIntent(
                api,clientId,new File(source),relativeMs,playing);
        String handle=tickets.reserve(snapshot,intent);
        return handle ==null ? null : new Reservation(handle,snapshot,intent);
    }

    synchronized DirectWatchRestoreCoordinator.Result watch(String handle,String context,
                                                             long mediaId,long intent) {
        return closed ? DirectWatchRestoreCoordinator.Result.CANCELED
                : coordinator.requestWatch(handle,context,mediaId,intent);
    }

    synchronized DirectWatchRestoreCoordinator.Result seekAfterReady(String handle,String context,
                                                                      long mediaId,long intent) {
        return closed ? DirectWatchRestoreCoordinator.Result.CANCELED
                : coordinator.requestSeekAfterReady(handle,context,mediaId,intent);
    }

    synchronized boolean cancel(String handle,String context,long mediaId,long intent) {
        return tickets.cancel(handle,context,mediaId,intent);
    }

    synchronized int activeCount() { return tickets.activeCount(); }
    synchronized boolean available() {
        return !closed && tickets.activeCount()<DirectWatchRecoveryTickets.MAX_TICKETS;
    }

    /** HTTP/plugin lifecycle retirement revokes both pending and claimed leases. */
    synchronized void close() { closed=true; tickets.clear(); }
}
