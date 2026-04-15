package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Pure URL parsing for YouTube video URLs. No network calls, no Spring wiring
 * beyond being a bean, and no {@code @Conditional} — YouTube URL recognition
 * is unconditional.
 */
@Service
@Lazy
public class YouTubeUrlService {

    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com");

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private Optional<URI> parseHttpUri(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return Optional.empty();
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            if (!lowerScheme.equals("http") && !lowerScheme.equals("https")) {
                return Optional.empty();
            }
            if (uri.getHost() == null) {
                return Optional.empty();
            }
            // Reject URLs with user-info (e.g. https://user:pass@youtube.com/...) — common phishing shape
            if (uri.getUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(uri);
        }
        catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Check whether the URL's host is a known YouTube host (ignoring the path/query).
     *
     * @param url the URL to inspect
     * @return true when the URL parses as an HTTP(S) URL whose host is a known YouTube host.
     *         Host-only: a YouTube host with a malformed path/query still returns true here.
     */
    public boolean hasYouTubeHost(String url) {
        return parseHttpUri(url).map(u -> YOUTUBE_HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT))).orElse(false);
    }

    /**
     * Extract the 11-character YouTube video ID from a URL, or empty if the URL
     * doesn't match any supported YouTube URL shape.
     *
     * @param url the URL to parse
     * @return the 11-character YouTube video ID, or empty if the URL is not a recognized YouTube video URL
     */
    public Optional<String> extractYouTubeVideoId(String url) {
        Optional<URI> parsed = parseHttpUri(url);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        URI uri = parsed.get();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!YOUTUBE_HOSTS.contains(host)) {
            return Optional.empty();
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getQuery() == null ? "" : uri.getQuery();

        Optional<String> candidate;
        if (host.equals("youtu.be")) {
            candidate = firstPathSegment(path);
        }
        else if (path.startsWith("/embed/") || path.startsWith("/live/") || path.startsWith("/shorts/")) {
            candidate = firstPathSegmentAfterPrefix(path);
        }
        else if (path.equals("/watch") || path.equals("/watch/")) {
            candidate = extractQueryParam(query, "v");
        }
        else {
            return Optional.empty();
        }
        return candidate.filter(id -> VIDEO_ID_PATTERN.matcher(id).matches());
    }

    public boolean isYouTubeUrl(String url) {
        return extractYouTubeVideoId(url).isPresent();
    }

    private Optional<String> firstPathSegment(String path) {
        if (path == null || path.length() < 2) {
            return Optional.empty();
        }
        String trimmed = path.substring(1);
        int slash = trimmed.indexOf('/');
        String seg = slash < 0 ? trimmed : trimmed.substring(0, slash);
        return seg.isBlank() ? Optional.empty() : Optional.of(seg);
    }

    private Optional<String> firstPathSegmentAfterPrefix(String path) {
        int slash = path.indexOf('/', 1);
        if (slash < 0 || slash + 1 >= path.length()) {
            return Optional.empty();
        }
        String rest = path.substring(slash + 1);
        int nextSlash = rest.indexOf('/');
        String seg = nextSlash < 0 ? rest : rest.substring(0, nextSlash);
        return seg.isBlank() ? Optional.empty() : Optional.of(seg);
    }

    private Optional<String> extractQueryParam(String query, String key) {
        if (query.isBlank()) {
            return Optional.empty();
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            if (k.equals(key) && !v.isBlank()) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }
}
