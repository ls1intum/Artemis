package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.ZonedDateTime;

public record PyrisLectureUnitDTO(long id, long lectureId, ZonedDateTime releaseDate, String name, int attachmentVersion) {
}
