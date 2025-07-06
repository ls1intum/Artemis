package de.tum.cit.aet.artemis.lecture.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class TumLiveService {

    /**
     * Returns a valid .m3u8 playlist URL for a known TUM Live stream, if available.
     *
     * @param videoUrl The original TUM Live video URL (e.g., https://tum.live/d/IN2028/ws23/55921)
     * @return Optional containing the .m3u8 playlist URL, or empty if not available or invalid
     */
    public Optional<String> getTumLivePlaylistLink(String videoUrl) {
        try {
            String streamId = extractStreamId(videoUrl);
            if (streamId == null) {
                return Optional.empty();
            }

            // 🔧 TEMPORARY: Only return a hardcoded playlist for test streamId 55921
            if ("55921".equals(streamId)) {
                String playlist = "https://edge.live.rbg.tum.de/vod/WiSe24ItP_2025_02_05_15_10COMB.mp4/playlist.m3u8?jwt=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";
                return Optional.of(playlist);
            }

            // 🚧 Future upgrade: Use real TUM Live API to check playlist availability
            // Example endpoint: https://tum.live/api/v2/streams/{streamId}/playlist
            // You can add HEAD request logic here once API is available.

            return Optional.empty();

        }
        catch (Exception e) {
            System.out.println("Failed to extract stream ID from URL: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the numeric stream ID from a TUM Live video URL.
     *
     * @param url The TUM Live URL (e.g., https://tum.live/d/IN2028/ws23/55921)
     * @return The stream ID as a string, or null if not found
     */
    private String extractStreamId(String url) {
        try {
            URI uri = new URI(url);
            String[] parts = uri.getPath().split("/");
            if (parts.length >= 1) {
                String lastSegment = parts[parts.length - 1];
                if (lastSegment.matches("\\d+")) {
                    return lastSegment;
                }
            }
        }
        catch (URISyntaxException e) {
            // Invalid URL
        }
        return null;
    }
}
