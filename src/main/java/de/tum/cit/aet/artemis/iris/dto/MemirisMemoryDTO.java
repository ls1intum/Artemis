package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MemirisMemoryDTO(String id, String title, String content, List<String> learnings, List<String> connections, boolean slept_on, boolean deleted) {
}
