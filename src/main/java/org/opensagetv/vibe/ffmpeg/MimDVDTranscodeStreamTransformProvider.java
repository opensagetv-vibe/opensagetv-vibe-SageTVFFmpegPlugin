package org.opensagetv.vibe.ffmpeg;

/** MIM-owned DVD transform selected by Fixed Direct Transcode. */
public final class MimDVDTranscodeStreamTransformProvider
        extends MimDVDStreamTransformProvider {
    public MimDVDTranscodeStreamTransformProvider() {
        super(TRANSCODE_TRANSPORT_ID, MODE_TRANSCODE, "mpegts");
    }
}
