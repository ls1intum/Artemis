package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contains the information about image
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImageDTO(int page, float xPosition, float yPosition, int originalWidth, int originalHeight, int renderedWidth, int renderedHeight, byte[] imageInBytes) {
}
