package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.LocalDateTime;

public record PyrisLectureUnitDTO(long id, long lectureId, LocalDateTime releaseDate, String name, int attachmentVersion) {
}
