package de.tum.cit.aet.artemis.admin.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.DataExportState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RequestDataExportDTO(long id, DataExportState dataExportState, ZonedDateTime createdDate) {
}
