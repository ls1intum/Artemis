package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface LectureUnitDTO extends CompetencyLinksHolderDTO {

    Long id();

    ZonedDateTime releaseDate();

    Set<CompetencyLinkDTO> competencyLinks();

    /**
     * Maps a polymorphic {@link LectureUnit} entity to its matching {@link LectureUnitDTO} subtype.
     *
     * @param lectureUnit the lecture unit to map
     * @return the concrete DTO carrying the {@code type} discriminator the client uses to narrow the unit
     */
    static LectureUnitDTO of(LectureUnit lectureUnit) {
        return switch (lectureUnit) {
            case TextUnit textUnit -> TextUnitDTO.of(textUnit);
            case OnlineUnit onlineUnit -> OnlineUnitDTO.of(onlineUnit);
            case ExerciseUnit exerciseUnit -> ExerciseUnitDTO.of(exerciseUnit);
            case AttachmentVideoUnit attachmentVideoUnit -> AttachmentVideoUnitDTO.of(attachmentVideoUnit);
            default -> throw new IllegalArgumentException("Unsupported lecture unit type: " + lectureUnit.getClass().getSimpleName());
        };
    }
}
