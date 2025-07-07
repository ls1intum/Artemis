package de.tum.cit.aet.artemis.lecture.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TumLiveService {

    private final Logger log = LoggerFactory.getLogger(TumLiveService.class);

    // Uncomment this when the API becomes available
    // private final RestClient restClient;
    //
    // public TumLiveService(RestClient.Builder restClientBuilder) {
    // this.restClient = restClientBuilder.baseUrl("https://tum.live/api/v2").build();
    // }

    /**
     * Attempts to extract the stream ID from a TUM Live URL and return the associated HLS playlist URL.
     * Currently mocked. Replace with real API call once available.
     */
    public Optional<String> getTumLivePlaylistLink(String videoUrl) {
        log.info("Mocking playlist URL for TUM Live video: {}", videoUrl);

        String streamId = extractStreamId(videoUrl);
        log.info("Extracted stream ID: {}", streamId);
        if (streamId == null) {
            log.warn("Could not extract stream ID from URL: {}", videoUrl);
            return Optional.empty();
        }

        // 🔁 MOCKED response for development/testing
        String mockedPlaylistUrl = "https://edge.live.rbg.tum.de/vod/WiSe24ItP_2025_02_05_15_10COMB.mp4/playlist.m3u8?jwt=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NTE4NzIzMjYsIlVzZXJJRCI6MjIzNSwiUGxheWxpc3QiOiJodHRwczovL2VkZ2UubGl2ZS5yYmcudHVtLmRlL3ZvZC9XaVNlMjRJdFBfMjAyNV8wMl8wNV8xNV8xMENPTUIubXA0L3BsYXlsaXN0Lm0zdTgiLCJEb3dubG9hZCI6ZmFsc2UsIlN0cmVhbUlEIjoiNTU5MjEiLCJDb3Vyc2VJRCI6IjE0ODMifQ.TmRaJD6Y88d6PPUqK_qNranbOFNy-ziXlGsyIWMq35ZNIh2zyAH1CSXWRfDFbblyyGoB8Ilnd-3h3aA6IptZOw_Hupx6SWTEXWv2jsRtGeGKdccx_N6_e3RpK2BBLQTf4-KntUz5R85w2nM69owAp1GE9frrqE76aK0jEsdXFVpW2hb59Uk3fCAD5WH4yPNBIvKRHq3bmrVgKymvejuPqXgQFVG9Grre7VJCuECCr1w8pMwzGLMOXwOa2FRBJyK3AZ2Lw_L_WqIy13UYYQ-6Tey-YVZ_akj-mxr1-hHKeLGcskclk864vEIW_eFcdFDLAcwAx-j5wQz7vTQhn6vnEw";

        return Optional.of(mockedPlaylistUrl);

        /*
         * // ✅ Uncomment this when real API is available
         * try {
         * String playlistUrl = restClient.get()
         * .uri("/streams/" + streamId + "/playlist")
         * .retrieve()
         * .body(String.class);
         * return Optional.ofNullable(playlistUrl);
         * } catch (RestClientException e) {
         * log.warn("TUM Live API failed for stream ID {}: {}", streamId, e.getMessage(), e);
         * return Optional.empty();
         * }
         */
    }

    /**
     * Extracts the numeric stream ID from any TUM Live video URL.
     */
    private String extractStreamId(String videoUrl) {
        try {
            URI uri = new URI(videoUrl);
            String[] segments = uri.getPath().split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (segments[i].matches("\\d+")) {
                    return segments[i];
                }
            }
        }
        catch (URISyntaxException e) {
            log.error("Malformed URL: {}", videoUrl, e);
        }
        return null;
    }
}
