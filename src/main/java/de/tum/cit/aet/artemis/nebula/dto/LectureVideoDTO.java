package de.tum.cit.aet.artemis.nebula.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for lecture video metadata returned to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureVideoDTO(Long lectureId, String videoId, String filename, Long sizeBytes, ZonedDateTime uploadedAt, String playlistUrl, Double durationSeconds) {
}
