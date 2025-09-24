package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemirisMemoryWithRelationsDTO(@NotNull String id, @NotNull String title, @NotNull String content, @NotNull boolean sleptOn, @NotNull boolean deleted,
        @NotNull List<MemirisLearningDTO> learnings, @NotNull List<MemirisMemoryConnectionDTO> connections) {
}
