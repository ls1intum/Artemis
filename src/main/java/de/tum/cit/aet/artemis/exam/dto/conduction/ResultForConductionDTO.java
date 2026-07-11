package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;

/**
 * Slim projection of a {@link Result} as nested inside a submission in the conduction / summary payload.
 * <p>
 * It carries only the score-summary scalars the entity wire exposes there (no {@code feedbacks}, no {@code submission}
 * or {@code participation} back-references). Deliberately not reusing the shared {@code ResultDTO}: that DTO nests a
 * {@code ParticipationDTO} whose {@code of(...)} calls {@code getCourseViaExerciseGroupOrCourseMember()}, which NPEs on
 * the masked exam graph (the exam is nulled for students). Keeping this projection back-reference-free avoids that trap.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultForConductionDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, boolean rated, Integer testCaseCount, Integer passedTestCaseCount,
        Integer codeIssueCount, long exerciseId, AssessmentType assessmentType) {

    /**
     * Converts a Result into a ResultForConductionDTO.
     *
     * @param result the result to convert
     * @return the converted DTO, or null if the result is null
     */
    public static ResultForConductionDTO of(Result result) {
        if (result == null) {
            return null;
        }
        return new ResultForConductionDTO(result.getId(), result.getCompletionDate(), result.isSuccessful(), result.getScore(), result.isRated(), result.getTestCaseCount(),
                result.getPassedTestCaseCount(), result.getCodeIssueCount(), result.getExerciseId(), result.getAssessmentType());
    }
}
