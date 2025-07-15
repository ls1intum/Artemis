package de.tum.cit.aet.artemis.lecture.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class TumLiveService {

    private static final Logger log = LoggerFactory.getLogger(TumLiveService.class);

    private final RestClient restClient;

    public TumLiveService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://tum.live/api/v2").build();
    }

    /**
     * Given a TUM Live URL, fetches the associated HLS playlist URL.
     */
    public Optional<String> getTumLivePlaylistLink(String videoUrl) {
        if (!videoUrl.contains("tum.live") && !videoUrl.contains("rbg.tum.de")) {
            log.debug("Not a TUM Live link: {}", videoUrl);
            return Optional.empty();
        }

        String streamId = extractStreamId(videoUrl);
        if (streamId == null) {
            log.warn("Could not extract stream ID from URL: {}", videoUrl);
            return Optional.empty();
        }

        // MOCK for stream 55921
        if ("55921".equals(streamId)) {
            String mockUrl = "https://edge.live.rbg.tum.de/vod/WiSe24ItP_2025_02_05_15_10COMB.mp4/playlist.m3u8?jwt=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NTI2MDY0MzMsIlVzZXJJRCI6MjIzNSwiUGxheWxpc3QiOiJodHRwczovL2VkZ2UubGl2ZS5yYmcudHVtLmRlL3ZvZC9XaVNlMjRJdFBfMjAyNV8wMl8wNV8xNV8xMENPTUIubXA0L3BsYXlsaXN0Lm0zdTgiLCJEb3dubG9hZCI6ZmFsc2UsIlN0cmVhbUlEIjoiNTU5MjEiLCJDb3Vyc2VJRCI6IjE0ODMifQ.P_8DExq6e94lgY6pfKPyKXHcmLTG9KqZItBulg7nsxHgX0-H8OiDbD4oNLB-GT1Iagorfjg_h3uV3bgNTKToISbMPsKotIYeyIoMZYabWEG0fG3JGW6wEOslH62QZAQQrZkOlui66PKWKh8Yvg4xaYwd-TkZ8Q9C1SbG7RMN_FoOstnwCtCymLlQndba_5AiVKfM9tRMQnUOSNVY71cb6f7ovQWA3q0uHsd2G05XpW6fjPkn-gglZNry40-nc71qB5b-LMy7gOjxRrb74FjVbrEK31jDZDaIRJeVoxDvGwKfkPTA_zEpedK05pKc1L7p1AVjv_aiVLJCqasheIsdAA";
            log.info("🔁 Using mocked playlist URL for stream {}: {}", streamId, mockUrl);
            return Optional.of(mockUrl);
        }

        try {
            Map<String, Object> response = restClient.get().uri("/streams/{streamId}/playlist", streamId).retrieve().body(new ParameterizedTypeReference<>() {
            });

            Object playlistUrl = response.get("playlistUrl");

            if (playlistUrl instanceof String url) {
                log.info("Retrieved playlist URL for stream {}: {}", streamId, url);
                return Optional.of(url);
            }
            else {
                log.warn("No 'playlistUrl' found in API response for stream {}", streamId);
                return Optional.empty();
            }

        }
        catch (RestClientException e) {
            log.error("TUM Live API call failed for stream {}: {}", streamId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Extracts the numeric stream ID from a TUM Live video URL.
     */
    private String extractStreamId(String videoUrl) {
        try {
            String path = new URI(videoUrl).getPath();
            Matcher matcher = Pattern.compile("/(\\d+)(?:/|$)").matcher(path);
            return matcher.find() ? matcher.group(1) : null;
        }
        catch (URISyntaxException e) {
            log.error("Malformed TUM Live URL: {}", videoUrl, e);
            return null;
        }
    }
}
