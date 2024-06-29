package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

/**
 * DTO for lecture unit information.
 *
 * @param id           the id of the lecture unit
 * @param lectureId    the id of the lecture
 * @param lectureTitle the title of the lecture
 * @param name         the name of the lecture unit
 * @param releaseDate  the release date of the lecture unit
 * @param type         the type of the lecture unit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitInformationDTO(long id, long lectureId, String lectureTitle, String name, ZonedDateTime releaseDate, Class<? extends LectureUnit> type) {
}
