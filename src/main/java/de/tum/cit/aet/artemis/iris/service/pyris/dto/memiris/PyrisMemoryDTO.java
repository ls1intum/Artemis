package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryDTO(@NotNull String id, @NotNull String title, @NotNull String content, @NotNull boolean sleptOn, @NotNull boolean deleted) {
}
