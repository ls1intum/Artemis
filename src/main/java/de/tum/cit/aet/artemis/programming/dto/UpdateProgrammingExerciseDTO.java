package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

/**
 * DTO for updating ProgrammingExercise.
 * This DTO includes all fields that can be updated through the update endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateProgrammingExerciseDTO(
        // Core identification
        @Nullable Long id,

        // Exercise base fields
        String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty, Double maxPoints, Double bonusPoints,
        IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, Boolean presentationScoreEnabled,
        Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions,

        // Timeline fields
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate,

        // Course/ExerciseGroup
        Long courseId, Long exerciseGroupId,

        // Grading and competencies
        Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks,

        // Programming exercise specific fields
        String testRepositoryUri, String solutionRepositoryUri, List<AuxiliaryRepositoryDTO> auxiliaryRepositories, Boolean allowOnlineEditor, Boolean allowOfflineIde,
        boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, ProgrammingLanguage programmingLanguage, String packageName,
        boolean showTestNamesToStudents, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, Boolean testCasesChanged, String projectKey,
        @Nullable SubmissionPolicy submissionPolicy, @Nullable ProjectType projectType, boolean releaseTestsWithExampleSolution,

        // Build config
        UpdateProgrammingExerciseBuildConfigDTO buildConfig) implements CompetencyLinksHolderDTO {

    /**
     * Creates a DTO from a ProgrammingExercise entity.
     * Used when you need to send exercise data to the client for editing.
     *
     * @param exercise the ProgrammingExercise entity to convert
     * @return a new UpdateProgrammingExerciseDTO with data from the entity
     */
    public static UpdateProgrammingExerciseDTO of(ProgrammingExercise exercise) {
        if (exercise == null) {
            throw new BadRequestAlertException("No programming exercise was provided.", "programmingExercise", "programmingExercise.isNull");
        }

        // For course exercises: set courseId, leave exerciseGroupId null
        // For exam exercises: set exerciseGroupId, leave courseId null
        Long courseId = exercise.isCourseExercise() && exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId()
                : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterionDTO> gradingCriterionDTOs = mapIfInitialized(exercise.getGradingCriteria(), GradingCriterionDTO::of, Collectors.toSet());
        Set<CompetencyLinkDTO> competencyLinkDTOs = mapIfInitialized(exercise.getCompetencyLinks(), CompetencyLinkDTO::of, Collectors.toSet());
        List<AuxiliaryRepositoryDTO> auxiliaryRepositoryDTOs = mapIfInitialized(exercise.getAuxiliaryRepositories(), AuxiliaryRepositoryDTO::of, Collectors.toUnmodifiableList());

        return new UpdateProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), courseId, exerciseGroupId,
                gradingCriterionDTOs, competencyLinkDTOs, exercise.getTestRepositoryUri(), exercise.getSolutionRepositoryUri(), auxiliaryRepositoryDTOs,
                exercise.isAllowOnlineEditor(), exercise.isAllowOfflineIde(), exercise.isAllowOnlineIde(), exercise.isStaticCodeAnalysisEnabled(),
                exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getProgrammingLanguage(), exercise.getPackageName(), exercise.getShowTestNamesToStudents(),
                exercise.getBuildAndTestStudentSubmissionsAfterDueDate(), exercise.getTestCasesChanged(), exercise.getProjectKey(), exercise.getSubmissionPolicy(),
                exercise.getProjectType(), exercise.isReleaseTestsWithExampleSolution(), UpdateProgrammingExerciseBuildConfigDTO.of(exercise.getBuildConfig()));
    }

    private static <T, R, C extends Collection<R>> C mapIfInitialized(Collection<T> source, Function<T, R> mapper, Collector<R, ?, C> collector) {
        if (source == null || !Hibernate.isInitialized(source)) {
            return null;
        }
        return source.stream().map(mapper).collect(collector);
    }
}
