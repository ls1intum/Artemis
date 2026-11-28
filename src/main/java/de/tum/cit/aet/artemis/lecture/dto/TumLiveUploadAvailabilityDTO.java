package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether editors can upload a recording for the course.
 *
 * @param available whether the course has an active TUM.Live connection
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TumLiveUploadAvailabilityDTO(boolean available) {
}
