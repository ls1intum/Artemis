package de.tum.cit.aet.artemis.assessment.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * DTO for displaying ratings in the instructor dashboard rating list.
 * Contains only the data needed for the rating list view in a flat structure.
 * This DTO is created directly in the JPA query using a constructor expression
 * for optimal database-to-client data transfer.
 *
 * @param id              the rating ID
 * @param rating          the rating value (1-5)
 * @param assessmentType  the type of assessment
 * @param assessorLogin   the assessor's login
 * @param assessorName    the assessor's name
 * @param resultId        the result ID (for navigation to assessment view)
 * @param submissionId    the submission ID (for navigation to assessment view)
 * @param participationId the participation ID (for navigation)
 * @param exerciseId      the exercise ID (for navigation)
 * @param exerciseTitle   the exercise title
 * @param exerciseType    the exercise type (e.g., TEXT, MODELING, PROGRAMMING)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RatingListItemDTO(Long id, Integer rating, AssessmentType assessmentType, String assessorLogin, String assessorName, Long resultId, Long submissionId,
        Long participationId, Long exerciseId, String exerciseTitle, ExerciseType exerciseType) {

    public RatingListItemDTO(Long id, Integer rating, AssessmentType assessmentType, String assessorLogin, String assessorName, Long resultId, Long submissionId,
            Long participationId, Long exerciseId, String exerciseTitle, Class<?> exerciseType) {
        this(id, rating, assessmentType, assessorLogin, assessorName, resultId, submissionId, participationId, exerciseId, exerciseTitle,
                ExerciseType.getExerciseTypeFromClass(exerciseType.asSubclass(Exercise.class)));
    }
}
