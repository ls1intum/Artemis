package de.tum.cit.aet.artemis.tumlive.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Service for detecting and extracting video IDs from YouTube URLs.
 * Supports standard watch URLs, shortened youtu.be URLs, embed URLs, and live URLs.
 */
@Service
@Lazy
public class YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeService.class);

    /**
     * Pattern to extract YouTube video IDs (11 characters: alphanumeric, hyphens, underscores).
     * Matches:
     * - youtube.com/watch?v=VIDEO_ID (with optional other query params)
     * - youtube.com/embed/VIDEO_ID
     * - youtube.com/live/VIDEO_ID
     * - youtube.com/shorts/VIDEO_ID
     * - youtube-nocookie.com/embed/VIDEO_ID
     * - youtu.be/VIDEO_ID
     */
    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile("(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^&]*&)*v=|embed/|live/|shorts/)|youtu\\.be/)([\\w-]{11})");

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

        String normalizedUrl;
        try {
            URI uri = new URI(videoUrl);
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
            if (!normalizedHost.equals("youtube.com") && !normalizedHost.endsWith(".youtube.com") && !normalizedHost.equals("youtube-nocookie.com")
                    && !normalizedHost.endsWith(".youtube-nocookie.com") && !normalizedHost.equals("youtu.be")) {
                return Optional.empty();
            }
            // Rebuild the URL with the normalized host so the regex below matches regardless of
            // the original casing in the host component while preserving the path and query exactly.
            normalizedUrl = new URI(uri.getScheme(), uri.getUserInfo(), normalizedHost, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        }
        catch (URISyntaxException e) {
            log.debug("Could not parse URL as URI: {}", videoUrl);
            return Optional.empty();
        }

        Matcher matcher = YOUTUBE_ID_PATTERN.matcher(normalizedUrl);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }

        log.debug("URL looks like YouTube but could not extract video ID: {}", videoUrl);
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
