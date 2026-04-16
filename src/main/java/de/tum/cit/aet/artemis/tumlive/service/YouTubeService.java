package de.tum.cit.aet.artemis.tumlive.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Service for detecting and extracting video IDs from YouTube URLs.
 * Supports standard watch URLs, shortened youtu.be URLs, embed URLs, and live URLs.
 */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeService.class);

    /** YouTube video IDs are exactly 11 characters: alphanumeric, hyphens, underscores. */
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("[\\w-]{11}");

    /** Path prefixes on youtube.com / youtube-nocookie.com that are immediately followed by the video ID. */
    private static final List<String> ID_PATH_PREFIXES = List.of("/embed/", "/live/", "/shorts/");

    /**
     * Extracts the YouTube video ID from a given URL.
     *
     * @param videoUrl the URL to extract the video ID from
     * @return an optional containing the 11-character video ID, or empty if the URL is not a recognized YouTube URL
     */
    public Optional<String> extractYouTubeVideoId(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = new URI(videoUrl);
        }
        catch (URISyntaxException e) {
            log.debug("Could not parse URL as URI: {}", videoUrl);
            return Optional.empty();
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return Optional.empty();
        }
        String host = uri.getHost();
        if (host == null) {
            return Optional.empty();
        }
        // URI hosts are case-insensitive per RFC 3986; normalize to lowercase before
        // matching against the allow-list so URLs like "https://YouTube.com/..." are accepted.
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (normalizedHost.equals("youtu.be")) {
            return extractIdAfterPrefix(path, "/");
        }

        boolean isYouTubeHost = normalizedHost.equals("youtube.com") || normalizedHost.endsWith(".youtube.com") || normalizedHost.equals("youtube-nocookie.com")
                || normalizedHost.endsWith(".youtube-nocookie.com");
        if (!isYouTubeHost) {
            return Optional.empty();
        }

        if (path.equals("/watch")) {
            return extractIdFromQueryParam(uri.getQuery(), "v");
        }
        for (String prefix : ID_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return extractIdAfterPrefix(path, prefix);
            }
        }

        log.debug("URL is on a YouTube host but the path is not a recognized video path: {}", videoUrl);
        return Optional.empty();
    }

    /**
     * Returns the video ID when {@code path} equals {@code prefix} followed by exactly an 11-character
     * video ID (optionally terminated by a trailing slash). Any other shape returns empty.
     */
    private static Optional<String> extractIdAfterPrefix(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return Optional.empty();
        }
        String remainder = path.substring(prefix.length());
        if (remainder.endsWith("/")) {
            remainder = remainder.substring(0, remainder.length() - 1);
        }
        if (VIDEO_ID_PATTERN.matcher(remainder).matches()) {
            return Optional.of(remainder);
        }
        return Optional.empty();
    }

    /**
     * Returns the value of the first {@code paramName} pair in a URL query if it is exactly an
     * 11-character video ID. Any other value (including longer/shorter strings or missing param) returns empty.
     */
    private static Optional<String> extractIdFromQueryParam(String query, String paramName) {
        if (query == null) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (pair.substring(0, eq).equals(paramName)) {
                String value = pair.substring(eq + 1);
                if (VIDEO_ID_PATTERN.matcher(value).matches()) {
                    return Optional.of(value);
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether the given URL is a recognized YouTube video URL.
     *
     * @param videoUrl the URL to check
     * @return true if the URL is a YouTube video URL from which a video ID can be extracted
     */
    public boolean isYouTubeUrl(String videoUrl) {
        return extractYouTubeVideoId(videoUrl).isPresent();
    }
}
