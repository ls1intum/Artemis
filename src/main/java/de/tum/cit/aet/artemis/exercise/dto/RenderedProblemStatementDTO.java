package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RenderedProblemStatementDTO(String html, String contentHash, String rendererVersion, @Nullable String interactiveScript) implements Serializable {
}
