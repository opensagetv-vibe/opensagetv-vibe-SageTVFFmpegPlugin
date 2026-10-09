package org.opensagetv.vibe.ffmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class DirectWatchRestoreCoordinatorTest {
    static final class Api implements DirectWatchSnapshot.Api {
        final DirectWatchSnapshot source;
        final List<String> events=new ArrayList<String>();
        Object current;
        boolean present=true, throwWatch;
        Runnable afterWatch, afterPause;
        long target=-1;
        Api(DirectWatchSnapshot source) { this.source=source; }
        public Object global(String name,Object... args) {
            if (name.equals("GetUIContextNames")) return present ? new String[]{source.context} : new String[]{"local"};
            if (name.equals("GetMediaFileID")) return args[0] ==source.mediaFile ? source.mediaFileId : 999L;
            throw new AssertionError("Unexpected global " + name);
        }
        public Object ui(String context,String name,Object... args) throws Exception {
            check(context.equals(source.context),"exact context only");
            if (name.equals("GetCurrentMediaFile")) return current;
            events.add(name);
            if (name.equals("Watch")) {
                if (throwWatch) throw new Exception("watch unavailable");
                check(args[0] ==source.mediaFile,"typed original source");
                current=args[0];
                if (afterWatch !=null) afterWatch.run();
                return null;
            }
            if (name.equals("Pause")) { if (afterPause !=null) afterPause.run(); return null; }
            if (name.equals("Seek")) { target=((Number)args[0]).longValue(); return null; }
            throw new AssertionError("No decoder-owned state/clock query: " + name);
        }
    }
    private static void check(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    private static DirectWatchSnapshot source(boolean playing) {
        return DirectWatchSnapshot.captureIntent(new DirectWatchSnapshotTest.IntentApi(),
                "444942585142",new File("/known.ts"),42_000,playing);
    }
    public static void main(String[] args) {
        DirectWatchSnapshot source=source(false);
        DirectWatchRecoveryTickets tickets=new DirectWatchRecoveryTickets(() ->1000L);
        String handle=tickets.reserve(source,1);
        Api api=new Api(source);
        DirectWatchRestoreCoordinator coordinator=new DirectWatchRestoreCoordinator(api,tickets);
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,1)
                ==DirectWatchRestoreCoordinator.Result.WATCH_REQUESTED,"watch staged");
        check(api.events.toString().equals("[Watch]"),"async Watch cannot safely Pause before readiness");
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,1)
                ==DirectWatchRestoreCoordinator.Result.STALE_TICKET,"no duplicate Watch");
        check(coordinator.requestSeekAfterReady(handle,source.context,source.mediaFileId,1)
                ==DirectWatchRestoreCoordinator.Result.SEEK_REQUESTED,"seek staged after readiness");
        check(api.events.toString().equals("[Watch, Pause, Seek]"),"pause intent applied once after readiness");
        check(api.target ==source.absoluteMediaTimeMs && api.target !=source.rawMediaTimeMs,
                "original absolute coordinate used");
        check(tickets.activeCount() ==0,"completed capacity released");
        check(coordinator.requestSeekAfterReady(handle,source.context,source.mediaFileId,1)
                ==DirectWatchRestoreCoordinator.Result.STALE_TICKET,"no duplicate Seek");

        source=source(true); tickets=new DirectWatchRecoveryTickets(() ->1000L);
        handle=tickets.reserve(source,2); api=new Api(source);
        coordinator=new DirectWatchRestoreCoordinator(api,tickets);
        api.current=source.mediaFile;
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,2)
                ==DirectWatchRestoreCoordinator.Result.OCCUPIED_CONTEXT && api.events.isEmpty(),
                "same-file user Watch must not be overwritten");
        api.current=null; api.present=false;
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,2)
                ==DirectWatchRestoreCoordinator.Result.NOT_READY && api.events.isEmpty(),"no UI fallback");
        api.present=true;
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,2)
                ==DirectWatchRestoreCoordinator.Result.WATCH_REQUESTED,"fresh context can proceed");
        api.current=new Object();
        check(coordinator.requestSeekAfterReady(handle,source.context,source.mediaFileId,2)
                ==DirectWatchRestoreCoordinator.Result.OCCUPIED_CONTEXT && api.target ==-1,
                "changed media cancels old Seek");

        final DirectWatchSnapshot canceled=source(false);
        final DirectWatchRecoveryTickets cancelStore=new DirectWatchRecoveryTickets(() ->1000L);
        final String cancelHandle=cancelStore.reserve(canceled,3);
        api=new Api(canceled);
        api.afterWatch=() ->cancelStore.cancel(cancelHandle,canceled.context,canceled.mediaFileId,3);
        coordinator=new DirectWatchRestoreCoordinator(api,cancelStore);
        check(coordinator.requestWatch(cancelHandle,canceled.context,canceled.mediaFileId,3)
                ==DirectWatchRestoreCoordinator.Result.CANCELED,"cancel after Watch respected");
        check(api.events.toString().equals("[Watch]"),"cancel stops Pause/Seek");

        final DirectWatchRecoveryTickets pauseStore=new DirectWatchRecoveryTickets(() ->1000L);
        final String pauseHandle=pauseStore.reserve(canceled,5);
        api=new Api(canceled);
        coordinator=new DirectWatchRestoreCoordinator(api,pauseStore);
        check(coordinator.requestWatch(pauseHandle,canceled.context,canceled.mediaFileId,5)
                ==DirectWatchRestoreCoordinator.Result.WATCH_REQUESTED,"paused restore Watch queued");
        api.afterPause=() ->pauseStore.cancel(pauseHandle,canceled.context,canceled.mediaFileId,5);
        check(coordinator.requestSeekAfterReady(pauseHandle,canceled.context,canceled.mediaFileId,5)
                ==DirectWatchRestoreCoordinator.Result.CANCELED,"cancel at Pause prevents stale Seek");
        check(api.events.toString().equals("[Watch, Pause]") && api.target ==-1,"no post-cancel Seek");

        tickets=new DirectWatchRecoveryTickets(() ->1000L);
        handle=tickets.reserve(canceled,6); api=new Api(canceled);
        coordinator=new DirectWatchRestoreCoordinator(api,tickets);
        coordinator.requestWatch(handle,canceled.context,canceled.mediaFileId,6);
        final Api changedDuringPause=api;
        api.afterPause=() ->changedDuringPause.current=new Object();
        check(coordinator.requestSeekAfterReady(handle,canceled.context,canceled.mediaFileId,6)
                ==DirectWatchRestoreCoordinator.Result.OCCUPIED_CONTEXT,"source change at Pause rejects old Seek");
        check(api.target ==-1 && tickets.activeCount() ==0,"changed user Watch left untouched");

        source=source(true); tickets=new DirectWatchRecoveryTickets(() ->1000L);
        handle=tickets.reserve(source,4); api=new Api(source); api.throwWatch=true;
        coordinator=new DirectWatchRestoreCoordinator(api,tickets);
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,4)
                ==DirectWatchRestoreCoordinator.Result.API_FAILURE,"API failure visible");
        check(coordinator.requestWatch(handle,source.context,source.mediaFileId,4)
                ==DirectWatchRestoreCoordinator.Result.STALE_TICKET && api.events.size() ==1,
                "failed ordered API never replayed implicitly");
        System.out.println("PASS: staged restore identity/coordinate/cancel/duplicate/API-failure guards");
    }
}
