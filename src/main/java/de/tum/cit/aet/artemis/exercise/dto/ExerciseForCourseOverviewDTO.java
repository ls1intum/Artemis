package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/**
 * Database projection containing the union of exercise fields needed by the course overview response and its score
 * calculation. Collections are loaded separately to avoid multiplying categories, participations, submissions, and
 * results into one large join result.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseForCourseOverviewDTO(ExerciseType type, Long id, String title, Double maxPoints, @Nullable Double bonusPoints, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, AssessmentType assessmentType,
        @Nullable DifficultyLevel difficulty, ExerciseMode mode, IncludedInOverallScore includedInOverallScore, @Nullable Boolean presentationScoreEnabled,
        boolean allowFeedbackRequests, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOfflineIde, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate,
        @Nullable Long variantGroupId, @Nullable String variantGroupTitle, @Nullable Double variantGroupMaxPoints, @Nullable ZonedDateTime variantGroupReleaseDate,
        @Nullable ZonedDateTime variantGroupStartDate, @Nullable ZonedDateTime variantGroupDueDate, @Nullable ZonedDateTime variantGroupAssessmentDueDate,
        @Nullable ZonedDateTime variantGroupExampleSolutionPublicationDate) {

    /**
     * JPQL constructor accepting the entity class produced by Hibernate's {@code TYPE(...)} function.
     */
    public ExerciseForCourseOverviewDTO(Class<? extends Exercise> type, Long id, String title, Double maxPoints, @Nullable Double bonusPoints, @Nullable ZonedDateTime releaseDate,
            @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, AssessmentType assessmentType,
            @Nullable DifficultyLevel difficulty, ExerciseMode mode, IncludedInOverallScore includedInOverallScore, @Nullable Boolean presentationScoreEnabled,
            boolean allowFeedbackRequests, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOfflineIde,
            @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, @Nullable Long variantGroupId, @Nullable String variantGroupTitle,
            @Nullable Double variantGroupMaxPoints, @Nullable ZonedDateTime variantGroupReleaseDate, @Nullable ZonedDateTime variantGroupStartDate,
            @Nullable ZonedDateTime variantGroupDueDate, @Nullable ZonedDateTime variantGroupAssessmentDueDate,
            @Nullable ZonedDateTime variantGroupExampleSolutionPublicationDate) {
        this(ExerciseType.getExerciseTypeFromClass(type), id, title, maxPoints, bonusPoints, releaseDate, startDate, dueDate, assessmentDueDate, assessmentType, difficulty, mode,
                includedInOverallScore, presentationScoreEnabled, allowFeedbackRequests, allowOnlineEditor, allowOfflineIde, buildAndTestStudentSubmissionsAfterDueDate,
                variantGroupId, variantGroupTitle, variantGroupMaxPoints, variantGroupReleaseDate, variantGroupStartDate, variantGroupDueDate, variantGroupAssessmentDueDate,
                variantGroupExampleSolutionPublicationDate);
    }

    /**
     * Builds the wire DTO after its two collections and the user's team assignment have been loaded independently.
     */
    public ExerciseOverviewDTO toOverviewDTO(Set<String> categories, @Nullable Long studentAssignedTeamId, Set<ParticipationOverviewDTO> studentParticipations,
            ZonedDateTime calculationTime, boolean quizBatchStarted) {
        ExerciseVariantGroupReferenceDTO variantGroup = variantGroupId == null ? null
                : new ExerciseVariantGroupReferenceDTO(variantGroupId, variantGroupTitle, variantGroupMaxPoints, variantGroupReleaseDate, variantGroupStartDate,
                        variantGroupDueDate, variantGroupAssessmentDueDate, variantGroupExampleSolutionPublicationDate);
        boolean teamMode = mode == ExerciseMode.TEAM;
        Boolean quizEnded = type == ExerciseType.QUIZ ? dueDate != null && calculationTime.isAfter(dueDate) : null;
        Set<QuizBatchOverviewDTO> quizBatches = quizBatchStarted ? Set.of(QuizBatchOverviewDTO.STARTED) : Set.of();
        return new ExerciseOverviewDTO(type, id, title, maxPoints, bonusPoints, releaseDate, startDate, dueDate, assessmentDueDate, assessmentType, difficulty, mode, teamMode,
                includedInOverallScore, categories, presentationScoreEnabled, allowFeedbackRequests, allowOnlineEditor, allowOfflineIde, quizEnded, quizBatches,
                studentAssignedTeamId, teamMode, variantGroup, studentParticipations);
    }

    /**
     * Reuses the same row as input to the score calculation.
     */
    public ExerciseCourseScoreDTO toCourseScoreDTO(long courseId) {
        return new ExerciseCourseScoreDTO(id, type, includedInOverallScore, assessmentType, dueDate, assessmentDueDate, buildAndTestStudentSubmissionsAfterDueDate, maxPoints,
                bonusPoints, courseId, variantGroupId, variantGroupMaxPoints);
    }
}
