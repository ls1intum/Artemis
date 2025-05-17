package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for representing a single slide in the page order.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SlideOrderDTO(String slideId, int order) {
}
