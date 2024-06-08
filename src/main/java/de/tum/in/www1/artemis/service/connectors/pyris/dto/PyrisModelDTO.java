package de.tum.in.www1.artemis.service.connectors.pyris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisModelDTO(String id, String name, String description) {
}
