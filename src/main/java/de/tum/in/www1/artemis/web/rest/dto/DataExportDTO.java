package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.enumeration.DataExportState;

public record DataExportDTO(Long id, DataExportState dataExportState) {
}
