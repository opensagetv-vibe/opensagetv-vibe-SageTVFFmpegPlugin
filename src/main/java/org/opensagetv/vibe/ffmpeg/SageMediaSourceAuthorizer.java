package org.opensagetv.vibe.ffmpeg;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import sage.SageTV;

/** Restricts MIM Direct sources to files already known to stock SageTV. */
final class SageMediaSourceAuthorizer implements MimDirectSessionService.SourceAuthorizer {
    public boolean isAllowed(Path source) {
        if (source == null) return false;
        try {
            return SageTV.api("GetMediaFileForFilePath", new Object[]{source.toFile()}) != null;
        } catch (InvocationTargetException failure) {
            return false;
        }
    }
}
