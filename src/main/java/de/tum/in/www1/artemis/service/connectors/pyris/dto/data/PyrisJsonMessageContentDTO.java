package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisJsonMessageContentDTO(@JsonRawValue String jsonContent) implements PyrisMessageContentBaseDTO {
}
