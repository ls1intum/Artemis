package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisImageMessageContentDTO(String imageData) implements PyrisMessageContentBaseDTO {
}
