package de.tum.cit.aet.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DataExportState;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DataExportDTO(Long id, DataExportState dataExportState, ZonedDateTime createdDate, ZonedDateTime nextRequestDate) {
}
