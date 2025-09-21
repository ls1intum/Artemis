package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryWithRelationsDTO(PyrisMemoryDTO memory, List<PyrisLearningDTO> learnings, List<PyrisMemoryConnectionDTO> connections) {
}
