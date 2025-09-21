package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryDTO(String id, String title, String content, boolean sleptOn, boolean deleted) {
}
