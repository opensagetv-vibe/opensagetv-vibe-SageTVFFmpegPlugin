package org.opensagetv.vibe.ffmpeg;

/** MIM-owned DVD stream-copy/remux transport selected by Fixed Direct Copy. */
public final class MimDVDCopyStreamTransformProvider
        extends MimDVDStreamTransformProvider {
    public MimDVDCopyStreamTransformProvider() {
        super(COPY_TRANSPORT_ID, MODE_COPY, "mpegps");
    }
}
