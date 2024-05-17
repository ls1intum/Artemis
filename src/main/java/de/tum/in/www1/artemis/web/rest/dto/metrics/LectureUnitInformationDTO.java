package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

/**
 * DTO for lecture unit information.
 *
 * @param id          the id of the lecture unit
 * @param name        the name of the lecture unit
 * @param releaseDate the release date of the lecture unit
 * @param type        the type of the lecture unit
 */
public record LectureUnitInformationDTO(long id, String name, ZonedDateTime releaseDate, Class<? extends LectureUnit> type) {
}
