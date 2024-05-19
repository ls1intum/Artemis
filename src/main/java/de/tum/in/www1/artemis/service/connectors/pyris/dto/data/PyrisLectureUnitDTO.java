package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;

public record PyrisLectureUnitDTO(long id, long lectureId, Instant releaseDate, String name, int attachmentVersion) {
}
