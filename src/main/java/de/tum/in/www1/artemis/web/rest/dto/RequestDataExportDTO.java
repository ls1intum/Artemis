package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.enumeration.DataExportState;

public record RequestDataExportDTO(long id, DataExportState dataExportState, ZonedDateTime createdDate) {
}
