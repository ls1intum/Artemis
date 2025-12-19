package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for sending lecture unit data to Pyris.
 *
 * @param lectureUnitId     the lecture unit ID
 * @param courseId          the course ID this unit belongs to
 * @param lectureId         the lecture ID this unit belongs to
 * @param releaseDate       the release date of the lecture unit
 * @param name              the name of the lecture unit
 * @param attachmentVersion the version of the attachment, if applicable
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureUnitDTO(Long lectureUnitId, Long courseId, Long lectureId, Instant releaseDate, String name, Integer attachmentVersion) {
}
