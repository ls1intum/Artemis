package de.tum.cit.aet.artemis.videosource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for creating a new gocast course binding.
 * <p>
 * Sent as the request body to {@code POST api/videosource/courses/{courseId}/binding}.
 *
 * @param gocastCourseId   the numeric gocast course id to bind to the Artemis course; must be positive
 * @param gocastCourseSlug the slug of the gocast course (used for watch-page links); must not be blank
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastCreateBindingRequestDTO(@Positive(message = "gocastCourseId must be positive") long gocastCourseId,
        @NotBlank(message = "gocastCourseSlug must not be blank") String gocastCourseSlug) {
}
