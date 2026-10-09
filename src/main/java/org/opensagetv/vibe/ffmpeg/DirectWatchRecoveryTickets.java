package org.opensagetv.vibe.ffmpeg;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Bounded, one-use server-local snapshot custody for MIM-DIRECT-005.
 *
 * <p>No API calls, producer creation, HTTP exposure or replay occurs here.
 * A successful claim is custody transfer, not proof of restored playback.
 * The eventual restore coordinator must revalidate cancellation/latest intent
 * immediately before each public API mutation and observe actual playback.</p>
 */
final class DirectWatchRecoveryTickets {
    static final int MAX_TICKETS=4;
    static final long TTL_MS=120_000L;
    private final LongSupplier monotonicMs;
    private final Map<String, Ticket> tickets=new LinkedHashMap<String, Ticket>();

    private static final class Ticket {
        final DirectWatchSnapshot snapshot;
        final long intentSequence;
        final long createdMs;
        boolean claimed;
        boolean watchRequested;
        boolean seekRequested;
        Ticket(DirectWatchSnapshot snapshot, long intent, long createdMs) {
            this.snapshot=snapshot; this.intentSequence=intent; this.createdMs=createdMs;
        }
    }

    DirectWatchRecoveryTickets(LongSupplier monotonicMs) {
        if (monotonicMs == null) throw new IllegalArgumentException("monotonic_clock_required");
        this.monotonicMs=monotonicMs;
    }

    /** Supersede only an older intent for this exact context, never another client. */
    synchronized String reserve(DirectWatchSnapshot snapshot, long intentSequence) {
        if (snapshot == null || intentSequence < 0) return null;
        long now=monotonicMs.getAsLong();
        expire(now);
        String previous=null;
        for (Map.Entry<String, Ticket> entry : tickets.entrySet()) {
            Ticket ticket=entry.getValue();
            if (!ticket.snapshot.context.equals(snapshot.context)) continue;
            if (intentSequence <= ticket.intentSequence) return null;
            previous=entry.getKey();
        }
        if (previous == null && tickets.size() >= MAX_TICKETS) return null;
        if (previous != null) tickets.remove(previous);
        String handle;
        do { handle=UUID.randomUUID().toString().replace("-", ""); }
        while (tickets.containsKey(handle));
        tickets.put(handle, new Ticket(snapshot,intentSequence,now));
        return handle;
    }

    /** Wrong owner/source/intent cannot consume a different client's snapshot. */
    synchronized DirectWatchSnapshot claim(String handle, String exactContext,
                                            long mediaFileId, long intentSequence) {
        expire(monotonicMs.getAsLong());
        Ticket ticket=tickets.get(handle);
        if (!matches(ticket,exactContext,mediaFileId,intentSequence) || ticket.claimed) return null;
        // Keep bounded lease metadata until completion/cancel/expiry. Removing
        // it here would make cancellation and newer-intent validation after
        // custody transfer impossible while public Watch/Seek is being staged.
        ticket.claimed=true;
        return ticket.snapshot;
    }

    synchronized boolean isClaimCurrent(String handle, String exactContext,
                                        long mediaFileId, long intentSequence) {
        expire(monotonicMs.getAsLong());
        Ticket ticket=tickets.get(handle);
        return matches(ticket,exactContext,mediaFileId,intentSequence) && ticket.claimed;
    }

    synchronized boolean complete(String handle, String exactContext,
                                  long mediaFileId, long intentSequence) {
        if (!isClaimCurrent(handle,exactContext,mediaFileId,intentSequence)) return false;
        tickets.remove(handle);
        return true;
    }

    synchronized DirectWatchSnapshot pending(String handle, String context,long id,long intent) {
        expire(monotonicMs.getAsLong());
        Ticket ticket=tickets.get(handle);
        return matches(ticket,context,id,intent) && !ticket.claimed ? ticket.snapshot : null;
    }

    synchronized DirectWatchSnapshot claimed(String handle, String context,long id,long intent) {
        return isClaimCurrent(handle,context,id,intent) ? tickets.get(handle).snapshot : null;
    }

    synchronized boolean beginWatch(String handle,String context,long id,long intent) {
        if (!isClaimCurrent(handle,context,id,intent)) return false;
        Ticket ticket=tickets.get(handle);
        if (ticket.watchRequested) return false;
        ticket.watchRequested=true;
        return true;
    }

    synchronized boolean beginSeek(String handle,String context,long id,long intent) {
        if (!isClaimCurrent(handle,context,id,intent)) return false;
        Ticket ticket=tickets.get(handle);
        if (!ticket.watchRequested || ticket.seekRequested) return false;
        ticket.seekRequested=true;
        return true;
    }

    synchronized boolean cancel(String handle, String exactContext,
                                long mediaFileId, long intentSequence) {
        expire(monotonicMs.getAsLong());
        Ticket ticket=tickets.get(handle);
        if (!matches(ticket,exactContext,mediaFileId,intentSequence)) return false;
        tickets.remove(handle);
        return true;
    }

    synchronized int activeCount() {
        expire(monotonicMs.getAsLong());
        return tickets.size();
    }

    synchronized void clear() { tickets.clear(); }

    private static boolean matches(Ticket ticket, String exactContext, long id, long intent) {
        return ticket != null && ticket.snapshot.context.equals(exactContext)
                && ticket.snapshot.mediaFileId ==id && ticket.intentSequence ==intent;
    }

    private void expire(long now) {
        Iterator<Ticket> iterator=tickets.values().iterator();
        while (iterator.hasNext()) {
            long age=now-iterator.next().createdMs;
            // A clock regression is not permission to extend a recovery lease.
            if (age < 0 || age >= TTL_MS) iterator.remove();
        }
    }
}
