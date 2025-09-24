package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryConnectionDTO(String id, String connectionType, List<PyrisMemoryDTO> memories, String description, double weight) {
}
