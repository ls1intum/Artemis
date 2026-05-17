package de.tum.cit.aet.artemis.atlas.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitType;

/**
 * DTO for lecture unit information.
 *
 * @param id           the id of the lecture unit
 * @param lectureId    the id of the lecture
 * @param lectureTitle the title of the lecture
 * @param name         the name of the lecture unit
 * @param releaseDate  the release date of the lecture unit
 * @param type         the type of the lecture unit (stable discriminator, not the internal entity class)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitInformationDTO(long id, long lectureId, String lectureTitle, String name, ZonedDateTime releaseDate, LectureUnitType type) {

    /**
     * Constructor used by the JPA {@code new ...DTO(...)} query expression: the JPQL {@code TYPE(lectureUnit)}
     * function yields the entity class, which is mapped here to the stable {@link LectureUnitType} discriminator so
     * the wire format never exposes the internal Java class name.
     *
     * @param type the concrete lecture unit entity class (from {@code TYPE(lectureUnit)})
     */
    public LectureUnitInformationDTO(long id, long lectureId, String lectureTitle, String name, ZonedDateTime releaseDate, Class<? extends LectureUnit> type) {
        this(id, lectureId, lectureTitle, name, releaseDate, LectureUnitType.fromEntityClass(type));
    }

    /**
     * Creates a LectureUnitInformationDTO from a LectureUnit.
     *
     * @param lectureUnit the LectureUnit to create the DTO from
     * @return the created DTO
     */
    public static LectureUnitInformationDTO of(LectureUnit lectureUnit) {
        return new LectureUnitInformationDTO(lectureUnit.getId(), lectureUnit.getLecture().getId(), lectureUnit.getLecture().getTitle(), lectureUnit.getName(),
                lectureUnit.getReleaseDate(), LectureUnitType.fromEntityClass(lectureUnit.getClass()));
    }
}
