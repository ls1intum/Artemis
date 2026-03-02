package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyLectureUnitLinkResponseDTO(double weight, LectureUnitForCompetencyDTO lectureUnit) {

    @Nullable
    public static CompetencyLectureUnitLinkResponseDTO of(@Nullable CompetencyLectureUnitLink link) {
        if (link == null) {
            return null;
        }
        return new CompetencyLectureUnitLinkResponseDTO(link.getWeight(), LectureUnitForCompetencyDTO.of(link.getLectureUnit()));
    }
}
