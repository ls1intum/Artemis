package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemirisMemoryDataDTO(List<MemirisMemoryDTO> memories, List<MemirisLearningDTO> learnings, List<MemirisMemoryConnectionDTO> connections) {
}
