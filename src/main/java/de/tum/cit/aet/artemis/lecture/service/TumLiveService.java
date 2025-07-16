package de.tum.cit.aet.artemis.lecture.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.tum.cit.aet.artemis.lecture.dto.TumLivePlaylistDTO;

@Service
public class TumLiveService {

    private static final Logger log = LoggerFactory.getLogger(TumLiveService.class);

    private static final Pattern TUM_LIVE_PATTERN = Pattern.compile("/w/([^/]+)/([0-9]+)");

    private final RestClient restClient;

    public TumLiveService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://tum.live/api/v2").build();
    }

    /**
     * Given a TUM Live public video URL, extracts courseSlug and streamId,
     * fetches the playlist URL from the TUM Live API.
     */
    public Optional<String> getTumLivePlaylistLink(String videoUrl) {
        if (!videoUrl.contains("tum.live") && !videoUrl.contains("rbg.tum.de")) {
            log.debug("Not a TUM Live link: {}", videoUrl);
            return Optional.empty();
        }

        StreamInfo info = extractCourseSlugAndStreamId(videoUrl);
        if (info == null) {
            log.warn("Could not extract courseSlug and streamId from URL: {}", videoUrl);
            return Optional.empty();
        }

        try {
            TumLivePlaylistDTO response = restClient.get().uri("/streams/{courseSlug}/{streamId}", info.courseSlug(), info.streamId()).retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response.getPlaylistUrl() != null) {
                log.info("Retrieved playlist URL for stream {}: {}", info.streamId(), response.getPlaylistUrl());
                return Optional.of(response.getPlaylistUrl());
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
     * Extracts courseSlug and streamId from TUM Live public video URLs.
     */
    private StreamInfo extractCourseSlugAndStreamId(String videoUrl) {
        try {
            String path = new URI(videoUrl).getPath(); // e.g. /w/WiSe24ItP/55921
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
