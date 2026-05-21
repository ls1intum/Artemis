package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseLinkDTO(@NotNull CourseCompetencyDTO courseCompetencyDTO, Double weight, Long courseId) {

    /**
     * Creates a DTO from a CompetencyExerciseLink entity.
     *
     * @param competencyExerciseLink CompetencyExerciseLink entity to convert
     * @return a new CompetencyExerciseLinkDTO with data from the entity
     */
    public static CompetencyExerciseLinkDTO of(CompetencyExerciseLink competencyExerciseLink) {
        if (competencyExerciseLink == null) {
            throw new BadRequestAlertException("No competency link was provided.", "CompetencyExerciseLink", "isNull");
        }
        if (competencyExerciseLink.getCompetency() == null) {
            throw new BadRequestAlertException("The competency link must reference a competency.", "CompetencyExerciseLink", "competencyMissing");
        }
        if (competencyExerciseLink.getCompetency().getCourse() == null) {
            throw new BadRequestAlertException("The competency referenced by this link is not associated with a course.", "CompetencyExerciseLink", "courseMissing");
        }
        return new CompetencyExerciseLinkDTO(CourseCompetencyDTO.of(competencyExerciseLink.getCompetency()), competencyExerciseLink.getWeight(),
                competencyExerciseLink.getCompetency().getCourse().getId());
    }
}
