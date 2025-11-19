package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

// added courseId since the corresponding dto on the Pyris side expects it
// corresponding dto on Pyris side expects more arguments
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitDTO(long lectureUnitId, Long courseId, Long lectureId, Instant releaseDate, String name, Integer attachmentVersion) {
}
