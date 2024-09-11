package de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTextMessageContentDTO(String textContent) implements PyrisMessageContentBaseDTO {
}
