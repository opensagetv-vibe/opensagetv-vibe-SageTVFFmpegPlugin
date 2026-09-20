package org.opensagetv.vibe.ffmpeg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal dependency-free JSON parser for MIM status/capabilities output. */
final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) { this.text = text == null ? "" : text; }

    static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        Object value = p.readValue();
        p.skipWs();
        if (p.pos != p.text.length()) throw new IllegalArgumentException("Trailing JSON at " + p.pos);
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String text) {
        Object value = parse(text);
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private Object readValue() {
        skipWs();
        if (pos >= text.length()) throw fail("Unexpected end");
        char c = text.charAt(pos);
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (c == 't' && accept("true")) return Boolean.TRUE;
        if (c == 'f' && accept("false")) return Boolean.FALSE;
        if (c == 'n' && accept("null")) return null;
        return readNumber();
    }

    private Map<String, Object> readObject() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        expect('{'); skipWs();
        if (peek('}')) { pos++; return out; }
        for (;;) {
            skipWs(); String key = readString(); skipWs(); expect(':');
            out.put(key, readValue()); skipWs();
            if (peek('}')) { pos++; return out; }
            expect(',');
        }
    }

    private List<Object> readArray() {
        ArrayList<Object> out = new ArrayList<Object>();
        expect('['); skipWs();
        if (peek(']')) { pos++; return out; }
        for (;;) {
            out.add(readValue()); skipWs();
            if (peek(']')) { pos++; return out; }
            expect(',');
        }
    }

    private String readString() {
        expect('"'); StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (pos >= text.length()) throw fail("Bad escape");
            char e = text.charAt(pos++);
            switch (e) {
                case '"': out.append('"'); break; case '\\': out.append('\\'); break;
                case '/': out.append('/'); break; case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break; case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break; case 't': out.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length()) throw fail("Bad unicode escape");
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); pos += 4; break;
                default: throw fail("Unknown escape");
            }
        }
        throw fail("Unterminated string");
    }

    private Number readNumber() {
        int start = pos;
        if (peek('-')) pos++;
        while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
        boolean decimal = false;
        if (peek('.')) { decimal = true; pos++; while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++; }
        if (peek('e') || peek('E')) { decimal = true; pos++; if (peek('+') || peek('-')) pos++; while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++; }
        String n = text.substring(start, pos);
        if (n.length() == 0 || n.equals("-")) throw fail("Expected number");
        return decimal ? Double.valueOf(n) : Long.valueOf(n);
    }

    private boolean accept(String token) {
        if (text.regionMatches(pos, token, 0, token.length())) { pos += token.length(); return true; }
        return false;
    }
    private void expect(char c) { skipWs(); if (!peek(c)) throw fail("Expected " + c); pos++; }
    private boolean peek(char c) { return pos < text.length() && text.charAt(pos) == c; }
    private void skipWs() { while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++; }
    private IllegalArgumentException fail(String msg) { return new IllegalArgumentException(msg + " at " + pos); }
}
