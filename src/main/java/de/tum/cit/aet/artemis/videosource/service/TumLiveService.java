package de.tum.cit.aet.artemis.videosource.service;

import java.net.URI;
import java.net.URISyntaxException;
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

    /**
     * Known TUM Live web-frontend hostnames. Used by {@link #extractStreamRef(String)} to reject
     * URLs that carry a matching path but are not actually TUM Live URLs.
     */
    private static final Set<String> TUM_LIVE_HOSTS = Set.of("tum.live", "live.rbg.tum.de");

    private final RestClient restClient;

    public TumLiveService(RestClient.Builder restClientBuilder, @Value("${artemis.tum-live.api-base-url:#{null}}") String tumLiveApiBaseUrl) {
        if (tumLiveApiBaseUrl == null || tumLiveApiBaseUrl.isBlank()) {
            log.warn(
                    "TUM Live API base URL is not configured. TUM Live integration will be disabled and transcription generation will not work. Please set 'artemis.tum-live.api-base-url' in your configuration.");
            this.restClient = null;
        }
        else {
            log.info("TUM Live API base URL is set to '{}'", tumLiveApiBaseUrl);
            this.restClient = restClientBuilder.baseUrl(tumLiveApiBaseUrl).build();
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
            log.warn("TUM Live API client is not configured. Cannot fetch requested playlist URL for video: {}", videoUrl);
            return Optional.empty();
        }

        StreamInfo info = extractCourseSlugAndStreamId(videoUrl);
        if (info == null) {
            log.warn("Could not extract courseSlug and streamId from URL: {}", videoUrl);
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
     * Extracts the stream identity ({@code slug} + {@code streamId}) from a TUM Live public video URL.
     * <p>
     * Parses the {@code /w/{slug}/{streamId}} path segment using the same regex already used by
     * {@link #getTumLivePlaylistLink(String)}, adding a host check to reject URLs from unrelated domains
     * that happen to share the same path structure. Known TUM Live hostnames are {@code tum.live} and
     * {@code live.rbg.tum.de}.
     * <p>
     * This method is intentionally static-context-free and does not require the RestClient to be
     * configured — it works even when TUM Live integration is disabled. Downstream callers (Task A4,
     * A7) can use it to obtain the {@code streamId} needed by EP2 ({@code GetPlaybackToken}) and the
     * {@code slug} needed for the "Powered by TUM Live" watch-page link.
     *
     * @param videoUrl the raw video URL from a lecture unit's {@code source} field
     * @return a {@link GocastStreamRef} with the parsed slug and stream ID, or {@link Optional#empty()}
     *         if the URL is not a recognised TUM Live video URL
     */
    public Optional<GocastStreamRef> extractStreamRef(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(videoUrl);
            String host = uri.getHost();
            if (host == null || !TUM_LIVE_HOSTS.contains(host)) {
                return Optional.empty();
            }
            String path = uri.getPath();
            Matcher matcher = TUM_LIVE_PATTERN.matcher(path);
            if (matcher.find()) {
                String slug = matcher.group(1);
                long streamId = Long.parseLong(matcher.group(2));
                return Optional.of(new GocastStreamRef(slug, streamId));
            }
        }
        catch (URISyntaxException | NumberFormatException e) {
            log.debug("Could not parse TUM Live stream ref from URL: {}", videoUrl, e);
        }
        return Optional.empty();
    }

    /**
     * Extracts courseSlug and streamId from TUM Live public video URLs.
     */
    private StreamInfo extractCourseSlugAndStreamId(String videoUrl) {
        try {
            String path = new URI(videoUrl).getPath();
            Matcher matcher = TUM_LIVE_PATTERN.matcher(path);
            if (matcher.find()) {
                return new StreamInfo(matcher.group(1), matcher.group(2));
            }
        }
        catch (URISyntaxException e) {
            log.error("Malformed TUM Live URL: {}", videoUrl, e);
        }
        return null;
    }

    /**
     * Internal helper class to hold extracted stream info.
     */
    private record StreamInfo(String courseSlug, String streamId) {
    }
}
