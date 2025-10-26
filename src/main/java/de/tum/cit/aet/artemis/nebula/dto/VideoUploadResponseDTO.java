package de.tum.cit.aet.artemis.nebula.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing the response from Nebula Video Storage Service after uploading a video.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VideoUploadResponseDTO(@JsonProperty("video_id") String videoId, @JsonProperty("filename") String filename, @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("uploaded_at") LocalDateTime uploadedAt, @JsonProperty("playlist_url") String playlistUrl, @JsonProperty("duration_seconds") Double durationSeconds,
        @JsonProperty("message") String message) {
}
