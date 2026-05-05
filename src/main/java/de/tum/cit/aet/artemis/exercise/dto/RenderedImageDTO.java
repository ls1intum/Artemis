package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RenderedImageDTO(String contentType, String base64, String filename) implements Serializable {
}
