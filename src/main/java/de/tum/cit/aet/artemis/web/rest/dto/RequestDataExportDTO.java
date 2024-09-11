package de.tum.cit.aet.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.enumeration.DataExportState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RequestDataExportDTO(long id, DataExportState dataExportState, ZonedDateTime createdDate) {
}
