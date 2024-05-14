package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.ZonedDateTime;
import java.util.List;

public record PyrisLectureDTO(long id, String title, String description, ZonedDateTime startDate, ZonedDateTime endDate, List<PyrisLectureUnitDTO> units) {
}
