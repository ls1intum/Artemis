package de.tum.cit.aet.artemis.nebula.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqRewritingResponseDTO(@NotNull String rewrittenText) {
}
