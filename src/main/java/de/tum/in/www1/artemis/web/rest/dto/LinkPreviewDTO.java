package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Information of a link
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LinkPreviewDTO(String title, String description, String image, String url) {
}
