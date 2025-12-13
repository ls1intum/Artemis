package de.tum.cit.aet.artemis.lecture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for TUM Live video upload request from Artemis.
 *
 * @param tumUsername     the TUM username for authentication
 * @param tumPassword     the TUM password for authentication
 * @param tumLiveCourseId the TUM Live course ID to upload to
 * @param title           the video title
 * @param description     the video description
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadRequestDTO(@NotBlank String tumUsername, @NotBlank String tumPassword, @NotNull Long tumLiveCourseId, @NotBlank String title, String description) {
}
