package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO;
import de.tum.cit.aet.artemis.videosource.config.TumLiveEnabled;

@Service
@Lazy
@Conditional(TumLiveEnabled.class)
public class TumLiveService {

    private static final Logger log = LoggerFactory.getLogger(TumLiveService.class);

    private static final Pattern TUM_LIVE_PATTERN = Pattern.compile("/w/([^/]+)/([0-9]+)");

    /** Hosts that serve TUM Live watch pages, the same list the lecture unit form accepts. */
    private static final Set<String> TUM_LIVE_HOSTS = Set.of("live.rbg.tum.de", "tum.live");

    private final RestClient restClient;

    /** The host of the configured TUM Live API, accepted as a watch page host too (a self-hosted or test instance). */
    private final String apiHost;

    public TumLiveService(RestClient.Builder restClientBuilder, @Value("${artemis.tum-live.api-base-url:#{null}}") String tumLiveApiBaseUrl) {
        if (tumLiveApiBaseUrl == null || tumLiveApiBaseUrl.isBlank()) {
            log.warn(
                    "TUM Live API base URL is not configured. TUM Live integration will be disabled and transcription generation will not work. Please set 'artemis.tum-live.api-base-url' in your configuration.");
            this.restClient = null;
            this.apiHost = null;
        }
        else {
            log.info("TUM Live API base URL is set to '{}'", tumLiveApiBaseUrl);
            this.restClient = restClientBuilder.baseUrl(tumLiveApiBaseUrl).build();
            this.apiHost = hostOf(tumLiveApiBaseUrl);
        }
    }

    /**
     * Given a TUM Live public video URL, extracts courseSlug and streamId,
     * then fetches the playlist URL from the TUM Live API.
     *
     * @param videoUrl the public TUM Live video URL to resolve
     * @return an optional playlist URL if found from the TUM Live API, or empty if not found or the URL is invalid
     */
    public Optional<String> getTumLivePlaylistLink(String videoUrl) {
        if (restClient == null) {
            log.warn("TUM Live API client is not configured. Cannot fetch requested playlist URL.");
            return Optional.empty();
        }

        StreamInfo info = extractCourseSlugAndStreamId(videoUrl);
        if (info == null) {
            log.warn("Could not extract courseSlug and streamId from the given video URL.");
            return Optional.empty();
        }

        try {
            TumLivePlaylistDTO response = restClient.get().uri("/streams/{courseSlug}/{streamId}", info.courseSlug(), info.streamId()).retrieve().body(TumLivePlaylistDTO.class);

            if (response != null && response.stream() != null && response.stream().playlistUrl() != null) {
                return Optional.of(response.stream().playlistUrl());
            }
            else {
                log.warn("No 'playlistUrl' found in API response for stream {}", info.streamId());
                return Optional.empty();
            }

        }
        catch (RestClientException e) {
            log.error("TUM Live API call failed for stream {}: {}", info.streamId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Whether the URL is a TUM Live watch page: a TUM Live host and the path {@code /w/<courseSlug>/<streamId>}. Checks only the URL, without calling the TUM
     * Live API, so it also holds while that API is unreachable.
     *
     * @param videoUrl the video URL to check
     * @return true if the URL names a TUM Live stream
     */
    public boolean isTumLiveUrl(String videoUrl) {
        return extractCourseSlugAndStreamId(videoUrl) != null;
    }

    /**
     * Extracts courseSlug and streamId from TUM Live public video URLs. A watch page path on any other host is not a TUM Live video.
     */
    private StreamInfo extractCourseSlugAndStreamId(String videoUrl) {
        try {
            URI uri = new URI(videoUrl);
            String path = uri.getPath();
            if (path == null || !isTumLiveHost(uri.getHost())) {
                // An opaque URI such as "mailto:..." has no path to match
                return null;
            }
            Matcher matcher = TUM_LIVE_PATTERN.matcher(path);
            if (matcher.find()) {
                return new StreamInfo(matcher.group(1), matcher.group(2));
            }
        }
        catch (URISyntaxException e) {
            log.warn("Malformed TUM Live URL: {} at index {}", e.getReason(), e.getIndex());
        }
        return null;
    }

    private boolean isTumLiveHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals(apiHost) || TUM_LIVE_HOSTS.stream().anyMatch(known -> normalized.equals(known) || normalized.endsWith("." + known));
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host != null ? host.toLowerCase(Locale.ROOT) : null;
        }
        catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Internal helper class to hold extracted stream info.
     */
    private record StreamInfo(String courseSlug, String streamId) {
    }
}
