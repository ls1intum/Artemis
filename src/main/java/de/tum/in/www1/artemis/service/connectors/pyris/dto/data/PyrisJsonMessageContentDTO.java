package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record PyrisJsonMessageContentDTO(@JsonRawValue String jsonContent) implements PyrisMessageContentDTO {
}
