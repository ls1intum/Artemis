package de.tum.cit.aet.artemis.core.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AcceptExternalLLMUsageDTO(@NotNull boolean accepted) {
}
