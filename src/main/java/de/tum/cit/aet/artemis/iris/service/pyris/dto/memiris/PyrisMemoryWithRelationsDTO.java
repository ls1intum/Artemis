package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryWithRelationsDTO(@NotNull PyrisMemoryDTO memory, @NotNull List<PyrisLearningDTO> learnings, @NotNull List<PyrisMemoryConnectionDTO> connections) {
}
