package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryConnectionDTO(@NotNull String id, @NotNull String connectionType, @NotNull List<PyrisMemoryDTO> memories, @NotNull String description,
        @NotNull double weight) {
}
