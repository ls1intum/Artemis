package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemirisMemoryConnectionDTO(String id, String connectionType, List<String> memories, String description, double weight) {
}
