package org.opensagetv.vibe.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Real loopback HTTP, fake public APIs; never addresses a live SageTV UI. */
public final class DirectWatchRecoveryHttpTest {
    static final class Api implements DirectWatchSnapshot.Api {
        final Object media=new Object();
        Object current=media;
        final List<String> mutations=new ArrayList<String>();
        long sought=-1;
        public Object global(String name,Object... args) {
            if (name.equals("GetUIContextNames")) return new String[]{"444942585142","other-ui"};
            if (name.equals("GetMediaFileForFilePath"))
                return new File("/known.ts").equals(args[0]) ? media : null;
            if (name.equals("GetMediaFileID")) return args[0] ==media ? 123L : 999L;
            if (name.equals("GetNumberOfSegments")) return 1;
            if (name.equals("IsDVD")) return false;
            if (name.equals("GetFileStartTime")) return 1_700_000_000_000L;
            throw new AssertionError("Unexpected global API: "+name);
        }
        public Object ui(String context,String name,Object... args) {
            check(context.equals("444942585142"),"no other UI may be controlled");
            if (name.equals("GetCurrentMediaFile")) return current;
            mutations.add(name);
            if (name.equals("Watch")) { current=args[0]; return new Object(); }
            if (name.equals("Pause")) return null;
            if (name.equals("Seek")) { sought=((Number)args[0]).longValue(); return null; }
            throw new AssertionError("No decoder-owned clock/state call: "+name);
        }
    }
    static final class Response {
        final int code; final String body;
        Response(int code,String body) { this.code=code; this.body=body; }
    }
    private static void check(boolean ok,String why) {
        if (!ok) throw new AssertionError(why);
    }
    private static int freePort() throws Exception {
        try (ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
    private static Response http(int port,String method,String path) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection();
        connection.setConnectTimeout(2000); connection.setReadTimeout(2000);
        connection.setRequestMethod(method);
        try {
            int code=connection.getResponseCode();
            InputStream stream=code>=400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if (stream !=null) try (InputStream input=stream) {
                byte[] buffer=new byte[1024]; int count;
                while ((count=input.read(buffer))>=0) bytes.write(buffer,0,count);
            }
            return new Response(code,new String(bytes.toByteArray(),"UTF-8"));
        } finally { connection.disconnect(); }
    }
    private static String token(Response response) {
        check(response.code==200,response.body);
        java.util.regex.Matcher match=java.util.regex.Pattern.compile(
                "\\\"recoveryToken\\\":\\\"([0-9a-f]{32})\\\"").matcher(response.body);
        check(match.find(),"opaque handle required"); return match.group(1);
    }
    private static String reserve(long intent) throws Exception {
        return "/v1/direct/recovery/reserve?clientId=444942585142&source="+
                URLEncoder.encode("/known.ts","UTF-8")+"&startMs=42000&playing=false&intent="+intent;
    }
    private static String binding(String handle,long intent) {
        return "?recoveryToken="+handle+"&context=444942585142&mediaFileId=123&intent="+intent;
    }
    public static void main(String[] args) throws Exception {
        Path home=Paths.get(args[0]).toAbsolutePath();
        System.setProperty("vibe.ffmpeg.sageHome",home.toString());
        RuntimePaths paths=RuntimePaths.detect();
        CaptionSideChannelService captions=new CaptionSideChannelService(paths);
        MimDirectSessionService direct=new MimDirectSessionService(paths,source -> false);
        Api api=new Api(); AtomicLong clock=new AtomicLong(1000);
        DirectWatchRecoveryService recovery=new DirectWatchRecoveryService(api,clock::get);
        int port=freePort();
        CaptionSideChannelHttpServer server=new CaptionSideChannelHttpServer("127.0.0.1",port,captions,direct,recovery);
        server.start();
        try {
            check(http(port,"GET","/v1/direct/recovery/reserve").code==405,"mutations require POST");
            check(http(port,"POST",reserve(1)+"&startMs=-1").code==400,"invalid coordinate rejected");
            check(http(port,"POST",reserve(1).replace("/reserve?","/reserve/suffix?")).code==400,"exact route required");
            check(http(port,"POST",reserve(1).replace("known.ts","unknown.ts")).code==409,"arbitrary source rejected");
            check(http(port,"POST",reserve(1).replace("clientId=444","clientId=prefix444")).code==409,"no suffix UI matching");
            Response reserved=http(port,"POST",reserve(1));
            String handle=token(reserved);
            check(!reserved.body.contains("known.ts") && !reserved.body.contains("1700000"),"source/clock private");
            check(http(port,"POST",reserve(1)).code==409,"same intent cannot replace custody");
            check(http(port,"POST","/v1/direct/recovery/watch"+binding(handle,1)).body.contains("occupied_context"),"existing Watch never overwritten");
            check(api.mutations.isEmpty(),"reservation/refusal read-only");
            api.current=null;
            check(http(port,"POST","/v1/direct/recovery/watch"+binding(handle,1).replace("mediaFileId=123","mediaFileId=999")).code==409,"wrong source cannot consume");
            Response watched=http(port,"POST","/v1/direct/recovery/watch"+binding(handle,1));
            check(watched.code==200 && watched.body.contains("watch_requested"),watched.body);
            check(api.mutations.toString().equals("[Watch]"),"no premature Pause");
            check(http(port,"POST","/v1/direct/recovery/watch"+binding(handle,1)).code==409,"Watch not replayed");
            Response seek=http(port,"POST","/v1/direct/recovery/seek"+binding(handle,1));
            check(seek.code==200 && seek.body.contains("seek_requested"),seek.body);
            check(api.sought==1_700_000_042_000L && api.mutations.toString().equals("[Watch, Pause, Seek]"),"absolute Seek and ready Pause");
            check(http(port,"POST","/v1/direct/recovery/seek"+binding(handle,1)).code==409,"Seek not replayed");
            check(recovery.activeCount()==0,"completion frees custody");

            api.current=api.media; handle=token(http(port,"POST",reserve(2)));
            check(http(port,"POST","/v1/direct/recovery/cancel"+binding(handle,2)).code==200,"owner cancel");
            api.current=null;
            check(http(port,"POST","/v1/direct/recovery/watch"+binding(handle,2)).code==409,"canceled ticket cannot Watch");
            api.current=api.media; handle=token(http(port,"POST",reserve(3)));
            clock.addAndGet(DirectWatchRecoveryTickets.TTL_MS); api.current=null;
            check(http(port,"POST","/v1/direct/recovery/watch"+binding(handle,3)).code==409,"expired ticket cannot Watch");
            api.current=api.media; token(http(port,"POST",reserve(4)));
            check(http(port,"GET","/v1/capabilities").body.contains("\"directWatchRecovery\":{\"contractVersion\":1,\"available\":true"),"versioned optional recovery contract");
        } finally { server.stop(); direct.close(); captions.close(); }
        check(recovery.activeCount()==0,"listener stop revokes all tickets");
        check(recovery.reserve("444942585142","/known.ts",0,true,5)==null,
                "late worker cannot reserve after listener retirement");
        // Existing production construction must leave these mutation routes absent.
        port=freePort(); server=new CaptionSideChannelHttpServer("127.0.0.1",port,captions,direct);
        server.start();
        try {
            check(http(port,"POST",reserve(5)).code==404,"old construction unaffected");
            check(http(port,"GET","/v1/capabilities").body.contains("\"directWatchRecovery\":{\"contractVersion\":1,\"available\":false"),"no service cannot advertise recovery");
        }
        finally { server.stop(); }
        System.out.println("PASS: recovery HTTP source/owner/intent/expiry/cancel/stages/legacy-absence");
    }
}
