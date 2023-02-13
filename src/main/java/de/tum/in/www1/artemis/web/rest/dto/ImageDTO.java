package de.tum.in.www1.artemis.web.rest.dto;

/**
 * Contains the information about image
 */
public record ImageDTO(int page, float xPosition, float yPosition, int originalWidth, int originalHeight, int renderedWidth, int renderedHeight, byte[] imageInBytes) {
}
