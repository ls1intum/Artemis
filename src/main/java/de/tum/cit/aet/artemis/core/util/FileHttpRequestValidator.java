package de.tum.cit.aet.artemis.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Utilities for validating conditional and range requests against file timestamps.
 */
public final class FileHttpRequestValidator {

    private static final Logger log = LoggerFactory.getLogger(FileHttpRequestValidator.class);

    private FileHttpRequestValidator() {
        // Utility class, no instantiation allowed
    }

    /**
     * Retrieves a file's last-modified timestamp.
     *
     * @param path file whose timestamp should be retrieved
     * @return timestamp in milliseconds, or {@code -1} when it cannot be read
     */
    public static long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (IOException e) {
            log.warn("Could not determine last modified time for file {}, skipping conditional request handling", path, e);
            return -1;
        }
    }

    /**
     * Checks whether an If-Modified-Since header matches the current file timestamp. If-None-Match takes precedence when present.
     *
     * @param requestHeaders request headers containing an optional validator
     * @param lastModified   current file timestamp in milliseconds
     * @return whether the file is not modified according to the request validator
     */
    public static boolean isNotModified(HttpHeaders requestHeaders, long lastModified) {
        if (requestHeaders.getFirst(HttpHeaders.IF_NONE_MATCH) != null) {
            return false;
        }
        try {
            long ifModifiedSince = requestHeaders.getIfModifiedSince();
            return ifModifiedSince >= 0 && lastModified / 1000 <= ifModifiedSince / 1000;
        }
        catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * Checks whether an optional If-Range date matches the current file timestamp.
     *
     * @param requestHeaders request headers containing an optional If-Range validator
     * @param lastModified   current file timestamp in milliseconds, or {@code -1} when unavailable
     * @return {@code true} when no If-Range header is present or its date matches the file timestamp
     */
    public static boolean ifRangeMatches(HttpHeaders requestHeaders, long lastModified) {
        if (requestHeaders.getFirst(HttpHeaders.IF_RANGE) == null) {
            return true;
        }
        if (lastModified < 0) {
            return false;
        }
        try {
            long ifRange = requestHeaders.getFirstDate(HttpHeaders.IF_RANGE);
            return ifRange >= 0 && lastModified / 1000 == ifRange / 1000;
        }
        catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
