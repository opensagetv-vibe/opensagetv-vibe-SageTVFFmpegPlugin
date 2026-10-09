package org.opensagetv.vibe.ffmpeg;

import java.util.ArrayList;
import java.util.List;

/** Deterministic public-API snapshot tests; never connect to or mutate a server. */
public final class DirectWatchSnapshotTest {
    static final class Fake implements DirectWatchSnapshot.Api {
        final Object media=new Object();
        final List<String> calls=new ArrayList<String>();
        Object[] contexts={"local", "44:49:42:58:51:42", "525155544e52"};
        boolean loaded=true, dvd=false, playing=true, fail=false, race=false;
        int reads;
        Object raw=42_000L;
        public Object global(String name, Object... args) throws Exception {
            calls.add(name);
            if (fail) throw new Exception("unavailable");
            if ("GetUIContextNames".equals(name)) return contexts;
            if ("GetMediaFileID".equals(name)) return args[0] == media ? 123L : 456L;
            throw new AssertionError("Unexpected API " + name);
        }
        public Object ui(String context, String name, Object... args) {
            check("44:49:42:58:51:42".equals(context), "must never address another context");
            calls.add(name);
            if ("IsMediaPlayerFullyLoaded".equals(name)) return loaded;
            if ("IsCurrentMediaFileDVD".equals(name)) return dvd;
            if ("GetCurrentMediaFile".equals(name)) return race && ++reads > 1 ? new Object() : media;
            if ("GetMediaTime".equals(name)) return 1_700_000_042_000L;
            if ("GetRawMediaTime".equals(name)) return raw;
            if ("IsPlaying".equals(name)) return playing;
            throw new AssertionError("Unexpected API " + name);
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Fake api=new Fake();
        DirectWatchSnapshot snapshot=DirectWatchSnapshot.capture(api,"444942585142");
        check(snapshot != null && snapshot.mediaFile == api.media && snapshot.mediaFileId ==123,
                "typed exact-client source captured");
        check(snapshot.absoluteMediaTimeMs ==1_700_000_042_000L && snapshot.rawMediaTimeMs ==42_000,
                "absolute and raw coordinates stay distinct");
        check(snapshot.playing, "playing value preserved");
        for (String call : api.calls) check(!call.equals("Watch") && !call.equals("Seek")
                && !call.equals("Pause") && !call.equals("Stop"), "capture must be read-only");
        api.playing=false;
        check(!DirectWatchSnapshot.capture(api,"44-49-42-58-51-42").playing,
                "inactive playing value retained without autoplay");
        api.contexts=new Object[]{"local", "prefix444942585142"};
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "no suffix/other-UI fallback");
        api.contexts=new Object[]{"44:49:42:58:51:42", "444942585142"};
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "ambiguous normalized identity");
        api=new Fake(); api.loaded=false;
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "loading state rejected");
        api=new Fake(); api.dvd=true;
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "DVD has a different clock contract");
        api=new Fake(); api.race=true;
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "source replacement rejected");
        api=new Fake(); api.raw=null;
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "missing raw clock is not zero");
        api=new Fake(); api.raw="42000";
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "text clock rejected");
        api=new Fake(); api.fail=true;
        check(DirectWatchSnapshot.capture(api,"444942585142") ==null, "API failure unavailable");
        check(DirectWatchSnapshot.capture(new Fake(),"local") ==null, "non-MiniClient identity rejected");
        System.out.println("PASS: DirectWatchSnapshot exact identity, coordinates, race and read-only safety");
        testStartupIntent();
    }

    static final class IntentApi implements DirectWatchSnapshot.Api {
        final Object current=new Object();
        Object resolved=current;
        int segments=1;
        boolean dvd, race;
        int reads;
        long start=1_700_000_000_000L;
        public Object global(String name,Object... values) {
            if ("GetUIContextNames".equals(name)) return new String[]{"444942585142"};
            if ("GetMediaFileForFilePath".equals(name)) {
                check(values[0] instanceof java.io.File,"typed path only");
                return resolved;
            }
            if ("GetMediaFileID".equals(name)) return values[0] ==current ? 123L : 456L;
            if ("GetNumberOfSegments".equals(name)) return segments;
            if ("IsDVD".equals(name)) return dvd;
            if ("GetFileStartTime".equals(name)) return start;
            throw new AssertionError("Unexpected global API " + name);
        }
        public Object ui(String context,String name,Object... values) {
            check("444942585142".equals(context),"exact owner");
            // Any decoder-clock/state query fails this test immediately.
            check("GetCurrentMediaFile".equals(name),"no decoder-owned startup query");
            return race && ++reads >1 ? new Object() : current;
        }
    }
    private static void testStartupIntent() {
        java.io.File source=new java.io.File("/known-recording.ts");
        IntentApi api=new IntentApi();
        DirectWatchSnapshot intent=DirectWatchSnapshot.captureIntent(api,"444942585142",source,42000,false);
        check(intent !=null && intent.explicitIntent && !intent.playing
                && intent.rawMediaTimeMs ==42000 && intent.absoluteMediaTimeMs ==api.start+42000,
                "explicit intent distinct from decoder observation");
        check(DirectWatchSnapshot.captureIntent(api,"444942585142",source,0,true).rawMediaTimeMs ==0,
                "zero startup target preserved");
        api.resolved=new Object();
        check(DirectWatchSnapshot.captureIntent(api,"444942585142",source,0,true) ==null,
                "library source must match current watch");
        api=new IntentApi(); api.segments=2;
        check(DirectWatchSnapshot.captureIntent(api,"444942585142",source,0,true) ==null,
                "unproven multi-segment coordinate rejected");
        api=new IntentApi(); api.dvd=true;
        check(DirectWatchSnapshot.captureIntent(api,"444942585142",source,0,true) ==null,"DVD rejected");
        api=new IntentApi(); api.race=true;
        check(DirectWatchSnapshot.captureIntent(api,"444942585142",source,0,true) ==null,"source race");
        api=new IntentApi(); api.start=Long.MAX_VALUE;
        check(DirectWatchSnapshot.captureIntent(api,"444942585142",source,1,true) ==null,"overflow rejected");
        check(DirectWatchSnapshot.captureIntent(new IntentApi(),"444942585142",source,-1,true) ==null,
                "negative offset rejected");
        System.out.println("PASS: startup explicit-intent source capture avoids decoder clocks/state");
    }
}
