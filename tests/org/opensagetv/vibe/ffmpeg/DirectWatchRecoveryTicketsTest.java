package org.opensagetv.vibe.ffmpeg;

import java.util.concurrent.atomic.AtomicLong;

/** No sockets or SageTV mutations; deterministic ticket ownership/expiry gates. */
public final class DirectWatchRecoveryTicketsTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static DirectWatchSnapshot snapshot() {
        return DirectWatchSnapshot.capture(new DirectWatchSnapshotTest.Fake(),"444942585142");
    }
    public static void main(String[] args) {
        AtomicLong clock=new AtomicLong(1000L);
        DirectWatchRecoveryTickets store=new DirectWatchRecoveryTickets(clock::get);
        DirectWatchSnapshot source=snapshot();
        String handle=store.reserve(source,1L);
        check(handle != null && handle.matches("[0-9a-f]{32}"), "opaque bounded handle");
        check(store.reserve(source,1L) ==null && store.reserve(source,0L) ==null,
                "duplicate/older intent cannot supersede");
        check(store.claim(handle,"525155544e52",source.mediaFileId,1) ==null, "wrong client rejected");
        check(store.claim(handle,source.context,source.mediaFileId+1,1) ==null, "wrong source rejected");
        check(store.claim(handle,source.context,source.mediaFileId,2) ==null, "wrong intent rejected");
        check(store.activeCount() ==1, "invalid claims do not consume valid custody");
        check(store.claim(handle,source.context,source.mediaFileId,1) ==source, "typed source retained");
        check(store.claim(handle,source.context,source.mediaFileId,1) ==null, "single use");
        check(store.isClaimCurrent(handle,source.context,source.mediaFileId,1),"claim remains cancellable");
        check(store.cancel(handle,source.context,source.mediaFileId,1),"cancel after custody claim");
        check(!store.isClaimCurrent(handle,source.context,source.mediaFileId,1),"canceled claim no longer current");

        handle=store.reserve(source,2);
        String newer=store.reserve(source,3);
        check(store.activeCount() ==1 && store.claim(handle,source.context,source.mediaFileId,2) ==null,
                "new intent retires previous ticket");
        check(!store.cancel(newer,"other",source.mediaFileId,3), "wrong client cannot cancel");
        check(store.cancel(newer,source.context,source.mediaFileId,3), "owner cancellation succeeds");
        check(store.claim(newer,source.context,source.mediaFileId,3) ==null, "canceled cannot claim");

        String claimed=store.reserve(source,4);
        check(store.claim(claimed,source.context,source.mediaFileId,4) !=null,"claim before supersession");
        String replacement=store.reserve(source,5);
        check(!store.isClaimCurrent(claimed,source.context,source.mediaFileId,4),"new intent invalidates claimed lease");
        check(!store.complete(claimed,source.context,source.mediaFileId,4),"old completion cannot consume replacement");
        check(store.claim(replacement,source.context,source.mediaFileId,5) !=null,"replacement claim");
        check(store.complete(replacement,source.context,source.mediaFileId,5) && store.activeCount() ==0,
                "completed claim frees bounded capacity");

        handle=store.reserve(source,4);
        clock.addAndGet(DirectWatchRecoveryTickets.TTL_MS-1);
        check(store.activeCount() ==1, "valid just before expiry");
        clock.incrementAndGet();
        check(store.claim(handle,source.context,source.mediaFileId,4) ==null, "expiry boundary rejected");
        handle=store.reserve(source,5);
        clock.decrementAndGet();
        check(store.claim(handle,source.context,source.mediaFileId,5) ==null, "clock regression fail-closed");
        check(store.reserve(null,1) ==null && store.reserve(source,-1) ==null, "invalid reservation rejected");

        // Distinct contexts are represented through the same read-only capture API.
        // This fake still verifies that ui calls target precisely its owner.
        for (int i=0;i<DirectWatchRecoveryTickets.MAX_TICKETS;i++) {
            final String owner=String.format("%012x",i+1);
            DirectWatchSnapshot.Api api=new DirectWatchSnapshot.Api() {
                public Object global(String name,Object... values) {
                    return "GetUIContextNames".equals(name) ? new String[]{owner} : 1L;
                }
                public Object ui(String context,String name,Object... values) {
                    check(owner.equals(context),"exact context");
                    if ("IsMediaPlayerFullyLoaded".equals(name) || "IsPlaying".equals(name)) return true;
                    if ("IsCurrentMediaFileDVD".equals(name)) return false;
                    if ("GetCurrentMediaFile".equals(name)) return owner;
                    return 10L;
                }
            };
            check(store.reserve(DirectWatchSnapshot.capture(api,owner),i) !=null,"available slot");
        }
        check(store.activeCount() ==DirectWatchRecoveryTickets.MAX_TICKETS,
                "bounded number of source snapshots");
        check(store.reserve(source,10) ==null, "capacity never evicts another client");
        clock.addAndGet(DirectWatchRecoveryTickets.TTL_MS);
        check(store.activeCount() ==0 && store.reserve(source,11) !=null,"expired capacity reclaimed");
        System.out.println("PASS: DirectWatchRecoveryTickets owner/source/latest intent/cancel/expiry/capacity");
    }
}
