package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for transferring minimal slide information needed for unhide scheduling.
 * Contains only the slide ID and hidden timestamp.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SlideUnhideDTO(Long id, ZonedDateTime hidden) {
}
