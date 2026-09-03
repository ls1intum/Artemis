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
 * <p>
 * {@code type} and {@code typeDiscriminator} are deliberately both present and deliberately differ for the milestone
 * subtypes: {@code type} is the server-side category the score calculation buckets by (see {@link #toCourseScoreDTO}),
 * which flattens a {@code UserStoryExercise} into {@link ExerciseType#PROGRAMMING}, while {@code typeDiscriminator} is
 * what actually goes on the wire - see {@link ExerciseType#getDiscriminatorFromClass}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseForCourseOverviewDTO(ExerciseType type, String typeDiscriminator, Long id, String title, Double maxPoints, @Nullable Double bonusPoints,
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
        AssessmentType assessmentType, @Nullable DifficultyLevel difficulty, ExerciseMode mode, IncludedInOverallScore includedInOverallScore,
        @Nullable Boolean presentationScoreEnabled, boolean allowFeedbackRequests, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOfflineIde,
        @Nullable Boolean staticCodeAnalysisEnabled, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, @Nullable Long variantGroupId,
        @Nullable String variantGroupTitle, @Nullable String variantGroupType, @Nullable Long variantGroupMilestoneExerciseId, @Nullable Double variantGroupMaxPoints,
        @Nullable ZonedDateTime variantGroupReleaseDate, @Nullable ZonedDateTime variantGroupStartDate, @Nullable ZonedDateTime variantGroupDueDate,
        @Nullable ZonedDateTime variantGroupAssessmentDueDate, @Nullable ZonedDateTime variantGroupExampleSolutionPublicationDate) {

    /**
     * JPQL constructor accepting the entity class produced by Hibernate's {@code TYPE(...)} function for the exercise
     * itself. {@code variantGroupType} arrives as a plain {@code "variant"}/{@code "milestone"}/{@code null} string
     * already - unlike the exercise, {@code TYPE(variantGroup)} can't be used directly here, since it throws
     * "Could not resolve discriminator value" for the (very common) rows where the LEFT JOINed variantGroup is absent;
     * the query instead derives the string itself via a {@code MilestoneExerciseGroup} existence check, and reads the
     * anchor exercise id from the same subtype table.
     */
    public ExerciseForCourseOverviewDTO(Class<? extends Exercise> type, Long id, String title, Double maxPoints, @Nullable Double bonusPoints, @Nullable ZonedDateTime releaseDate,
            @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, AssessmentType assessmentType,
            @Nullable DifficultyLevel difficulty, ExerciseMode mode, IncludedInOverallScore includedInOverallScore, @Nullable Boolean presentationScoreEnabled,
            boolean allowFeedbackRequests, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOfflineIde, @Nullable Boolean staticCodeAnalysisEnabled,
            @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, @Nullable Long variantGroupId, @Nullable String variantGroupTitle,
            @Nullable String variantGroupType, @Nullable Long variantGroupMilestoneExerciseId, @Nullable Double variantGroupMaxPoints,
            @Nullable ZonedDateTime variantGroupReleaseDate, @Nullable ZonedDateTime variantGroupStartDate, @Nullable ZonedDateTime variantGroupDueDate,
            @Nullable ZonedDateTime variantGroupAssessmentDueDate, @Nullable ZonedDateTime variantGroupExampleSolutionPublicationDate) {
        this(ExerciseType.getExerciseTypeFromClass(type), ExerciseType.getDiscriminatorFromClass(type), id, title, maxPoints, bonusPoints, releaseDate, startDate, dueDate,
                assessmentDueDate, assessmentType, difficulty, mode, includedInOverallScore, presentationScoreEnabled, allowFeedbackRequests, allowOnlineEditor, allowOfflineIde,
                staticCodeAnalysisEnabled, buildAndTestStudentSubmissionsAfterDueDate, variantGroupId, variantGroupTitle, variantGroupType, variantGroupMilestoneExerciseId,
                variantGroupMaxPoints, variantGroupReleaseDate, variantGroupStartDate, variantGroupDueDate, variantGroupAssessmentDueDate,
                variantGroupExampleSolutionPublicationDate);
    }

    /**
     * Builds the wire DTO after its two collections and the user's team assignment have been loaded independently.
     */
    public ExerciseOverviewDTO toOverviewDTO(Set<String> categories, @Nullable Long studentAssignedTeamId, Set<ParticipationOverviewDTO> studentParticipations,
            ZonedDateTime calculationTime, boolean quizBatchStarted) {
        ExerciseVariantGroupReferenceDTO variantGroup = variantGroupId == null ? null
                : new ExerciseVariantGroupReferenceDTO(variantGroupId, variantGroupTitle, variantGroupType, variantGroupMilestoneExerciseId, variantGroupMaxPoints,
                        variantGroupReleaseDate, variantGroupStartDate, variantGroupDueDate, variantGroupAssessmentDueDate, variantGroupExampleSolutionPublicationDate);
        boolean teamMode = mode == ExerciseMode.TEAM;
        Boolean quizEnded = type == ExerciseType.QUIZ ? dueDate != null && calculationTime.isAfter(dueDate) : null;
        Set<QuizBatchOverviewDTO> quizBatches = quizBatchStarted ? Set.of(QuizBatchOverviewDTO.STARTED) : Set.of();
        return new ExerciseOverviewDTO(typeDiscriminator, id, title, maxPoints, bonusPoints, releaseDate, startDate, dueDate, assessmentDueDate, assessmentType, difficulty, mode,
                teamMode, includedInOverallScore, categories, presentationScoreEnabled, allowFeedbackRequests, allowOnlineEditor, allowOfflineIde, staticCodeAnalysisEnabled,
                quizEnded, quizBatches, studentAssignedTeamId, teamMode, variantGroup, studentParticipations);
    }

    /**
     * Reuses the same row as input to the score calculation.
     */
    public ExerciseCourseScoreDTO toCourseScoreDTO(long courseId) {
        return new ExerciseCourseScoreDTO(id, type, includedInOverallScore, assessmentType, dueDate, assessmentDueDate, buildAndTestStudentSubmissionsAfterDueDate, maxPoints,
                bonusPoints, courseId, variantGroupId, variantGroupMaxPoints, "milestone".equals(variantGroupType));
    }
}
