package org.opensagetv.vibe.ffmpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Bounded MPEG-TS/PES parser for A/53 CEA caption data carried by MPEG-2
 * user-data or AVC/HEVC registered SEI. It never decodes video and never
 * retains media payload after the current bounded PES has been inspected.
 */
final class A53CaptionParser {
    interface Sink { void packet(long ptsMs, byte[] triples) throws IOException; }

    private static final int TS_PACKET_SIZE = 188;
    private static final int MAX_PES_BYTES = 4 * 1024 * 1024;
    private final Sink sink;
    private byte[] carry = new byte[0];
    private final ByteArrayOutputStream pes = new ByteArrayOutputStream();
    private int videoPid = -1;
    private int lastContinuity = -1;
    private long basePts = -1;
    private long lastRawPts = -1;
    private long ptsWrap;

    A53CaptionParser(Sink sink) { this.sink = sink; }

    void push(byte[] bytes, int offset, int count) throws IOException {
        if (bytes == null || offset < 0 || count < 0 || offset + count > bytes.length)
            throw new IllegalArgumentException("Invalid MPEG-TS input range");
        byte[] joined = new byte[carry.length + count];
        System.arraycopy(carry, 0, joined, 0, carry.length);
        System.arraycopy(bytes, offset, joined, carry.length, count);
        int at = 0;
        while (at + TS_PACKET_SIZE <= joined.length) {
            if ((joined[at] & 0xff) != 0x47 ||
                    (at + TS_PACKET_SIZE * 2 <= joined.length &&
                     (joined[at + TS_PACKET_SIZE] & 0xff) != 0x47)) {
                at++;
                continue;
            }
            packet(joined, at);
            at += TS_PACKET_SIZE;
        }
        carry = Arrays.copyOfRange(joined, at, joined.length);
    }

    void finish() throws IOException { flushPes(); }

    private void packet(byte[] bytes, int offset) throws IOException {
        if ((bytes[offset + 1] & 0x80) != 0) return;
        int pid = ((bytes[offset + 1] & 0x1f) << 8) | (bytes[offset + 2] & 0xff);
        int adaptation = (bytes[offset + 3] >> 4) & 3;
        int continuity = bytes[offset + 3] & 15;
        if (adaptation == 0 || adaptation == 2) return;
        int start = offset + 4;
        if (adaptation == 3) {
            int length = bytes[start] & 0xff;
            if (length > 0 && (bytes[start + 1] & 0x80) != 0 && pid == videoPid) {
                flushPes();
                lastContinuity = -1;
            }
            start += length + 1;
        }
        if (start >= offset + TS_PACKET_SIZE) return;
        boolean payloadStart = (bytes[offset + 1] & 0x40) != 0;
        if (videoPid < 0 && payloadStart && start + 4 <= offset + TS_PACKET_SIZE &&
                bytes[start] == 0 && bytes[start + 1] == 0 && bytes[start + 2] == 1 &&
                (bytes[start + 3] & 0xf0) == 0xe0) videoPid = pid;
        if (pid != videoPid) return;
        if (lastContinuity == continuity) return;
        if (lastContinuity >= 0 && continuity != ((lastContinuity + 1) & 15)) pes.reset();
        lastContinuity = continuity;
        if (payloadStart) flushPes();
        if (pes.size() + offset + TS_PACKET_SIZE - start > MAX_PES_BYTES) {
            pes.reset();
            return;
        }
        if (pes.size() == 0 && !payloadStart) return;
        pes.write(bytes, start, offset + TS_PACKET_SIZE - start);
    }

    private void flushPes() throws IOException {
        byte[] packet = pes.toByteArray();
        pes.reset();
        if (packet.length < 14 || packet[0] != 0 || packet[1] != 0 ||
                packet[2] != 1 || (packet[7] & 0x80) == 0) return;
        int elementaryStart = 9 + (packet[8] & 0xff);
        if (elementaryStart > packet.length) return;
        long rawPts = pts(packet, 9);
        if (lastRawPts >= 0 && rawPts < lastRawPts - 0x100000000L)
            ptsWrap += 0x200000000L;
        lastRawPts = rawPts;
        long absolute = rawPts + ptsWrap;
        if (basePts < 0) basePts = absolute;
        long timeMs = Math.max(0, (absolute - basePts) / 90L);
        for (int at = elementaryStart; at + 4 < packet.length; at++) {
            if (packet[at] != 0 || packet[at + 1] != 0 || packet[at + 2] != 1) continue;
            int code = packet[at + 3] & 0xff;
            if (code == 0xb2) {
                extractGa94(packet, at + 4, packet.length, timeMs);
                continue;
            }
            int avcType = code & 31;
            int hevcType = (code >> 1) & 63;
            if (avcType != 6 && hevcType != 39 && hevcType != 40) continue;
            int head = at + 4 + (avcType == 6 ? 0 : 1);
            int end = packet.length;
            for (int scan = head; scan + 3 < packet.length; scan++) {
                if (packet[scan] == 0 && packet[scan + 1] == 0 && packet[scan + 2] == 1) {
                    end = scan;
                    break;
                }
            }
            byte[] rbsp = unescape(packet, head, end);
            int cursor = 0;
            while (cursor + 2 <= rbsp.length) {
                int type = 0;
                int size = 0;
                while (cursor < rbsp.length && (rbsp[cursor] & 0xff) == 255) {
                    type += 255;
                    cursor++;
                }
                if (cursor >= rbsp.length) break;
                type += rbsp[cursor++] & 0xff;
                while (cursor < rbsp.length && (rbsp[cursor] & 0xff) == 255) {
                    size += 255;
                    cursor++;
                }
                if (cursor >= rbsp.length) break;
                size += rbsp[cursor++] & 0xff;
                if (size < 0 || size > rbsp.length - cursor) break;
                if (type == 4 && size >= 10 && (rbsp[cursor] & 0xff) == 181 &&
                        rbsp[cursor + 1] == 0 && rbsp[cursor + 2] == 49)
                    extractGa94(rbsp, cursor + 3, cursor + size, timeMs);
                cursor += size;
            }
            at = end - 1;
        }
    }

    private void extractGa94(byte[] bytes, int start, int end, long timeMs) throws IOException {
        if (start + 7 > end || bytes[start] != 0x47 || bytes[start + 1] != 0x41 ||
                bytes[start + 2] != 0x39 || bytes[start + 3] != 0x34 ||
                bytes[start + 4] != 3) return;
        int flags = bytes[start + 5] & 0xff;
        int count = flags & 31;
        if ((flags & 64) == 0 || count == 0 || start + 7 + count * 3 > end) return;
        sink.packet(timeMs, Arrays.copyOfRange(bytes, start + 7, start + 7 + count * 3));
    }

    static long pts(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 14) << 29) |
                ((long) (bytes[offset + 1] & 255) << 22) |
                ((long) (bytes[offset + 2] & 254) << 14) |
                ((long) (bytes[offset + 3] & 255) << 7) |
                ((bytes[offset + 4] & 254) >> 1);
    }

    static byte[] unescape(byte[] bytes, int start, int end) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int zeroes = 0;
        for (int at = start; at < end; at++) {
            int value = bytes[at] & 0xff;
            if (zeroes >= 2 && value == 3) {
                zeroes = 0;
                continue;
            }
            output.write(value);
            zeroes = value == 0 ? zeroes + 1 : 0;
        }
        return output.toByteArray();
    }
}
