package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.DataExportState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportDTO(Long id, DataExportState dataExportState, ZonedDateTime createdDate, ZonedDateTime nextRequestDate) {
}
