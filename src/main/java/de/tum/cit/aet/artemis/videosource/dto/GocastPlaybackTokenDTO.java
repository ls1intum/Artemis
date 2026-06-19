package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a signed playback token response from gocast (TUM Live) integration endpoint EP2
 * ({@code POST /integration/courses/{courseId}/streams/{streamId}/playback-token}).
 * <p>
 * JSON field names follow the proto-gateway naming convention (camelCase from proto snake_case).
 * Fields not present in the response are silently ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GocastPlaybackTokenDTO(@JsonProperty("playlistUrl") String playlistUrl, @JsonProperty("playlistUrlPres") String playlistUrlPres,
        @JsonProperty("playlistUrlCam") String playlistUrlCam, @JsonProperty("expiresIn") int expiresIn) {
}
