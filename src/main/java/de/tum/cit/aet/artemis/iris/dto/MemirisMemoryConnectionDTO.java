package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemirisMemoryConnectionDTO(@NotNull String id, @NotNull String connectionType, @NotNull List<String> memories, @NotNull String description, @NotNull double weight) {
}
