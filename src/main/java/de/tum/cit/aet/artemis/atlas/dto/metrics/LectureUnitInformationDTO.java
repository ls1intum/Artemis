package de.tum.cit.aet.artemis.atlas.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

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

    /**
     * Creates a LectureUnitInformationDTO from a LectureUnit.
     *
     * @param lectureUnit the LectureUnit to create the DTO from
     * @return the created DTO
     */
    public static <L extends LectureUnit> LectureUnitInformationDTO of(L lectureUnit) {
        return new LectureUnitInformationDTO(lectureUnit.getId(), lectureUnit.getLecture().getId(), lectureUnit.getLecture().getTitle(), lectureUnit.getName(),
                lectureUnit.getReleaseDate(), lectureUnit.getClass());
    }
}
