package de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisConsistencyCheckDTO(String text) {
}
