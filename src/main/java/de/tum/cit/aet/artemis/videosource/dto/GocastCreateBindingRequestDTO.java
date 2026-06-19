package de.tum.cit.aet.artemis.videosource.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for creating a new gocast course binding.
 * <p>
 * Sent as the request body to {@code POST api/videosource/courses/{courseId}/binding}.
 *
 * @param gocastCourseId   the numeric gocast course id to bind to the Artemis course
 * @param gocastCourseSlug the slug of the gocast course (used for watch-page links)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GocastCreateBindingRequestDTO(long gocastCourseId, String gocastCourseSlug) {
}
