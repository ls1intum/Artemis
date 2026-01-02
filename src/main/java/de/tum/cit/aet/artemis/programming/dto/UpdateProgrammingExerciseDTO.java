package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
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
        String testRepositoryUri, String templateRepositoryUri, String solutionRepositoryUri, List<AuxiliaryRepositoryDTO> auxiliaryRepositories, Boolean allowOnlineEditor,
        Boolean allowOfflineIde, boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, ProgrammingLanguage programmingLanguage,
        String packageName, boolean showTestNamesToStudents, @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, Boolean testCasesChanged, String projectKey,
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

        Set<GradingCriterionDTO> gradingCriterionDTOs = null;
        Set<CompetencyLinkDTO> competencyLinkDTOs = null;
        List<AuxiliaryRepositoryDTO> auxiliaryRepositoryDTOs = null;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();

        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.isEmpty() ? Set.of() : competencyLinks.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }
        if (exercise.getAuxiliaryRepositories() != null && Hibernate.isInitialized(exercise.getAuxiliaryRepositories())) {
            auxiliaryRepositoryDTOs = exercise.getAuxiliaryRepositories().isEmpty() ? List.of()
                    : exercise.getAuxiliaryRepositories().stream().map(AuxiliaryRepositoryDTO::of).toList();
        }

        return new UpdateProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), courseId, exerciseGroupId,
                gradingCriterionDTOs, competencyLinkDTOs, exercise.getTestRepositoryUri(), exercise.getTemplateRepositoryUri(), exercise.getSolutionRepositoryUri(),
                auxiliaryRepositoryDTOs, exercise.isAllowOnlineEditor(), exercise.isAllowOfflineIde(), exercise.isAllowOnlineIde(), exercise.isStaticCodeAnalysisEnabled(),
                exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getProgrammingLanguage(), exercise.getPackageName(), exercise.getShowTestNamesToStudents(),
                exercise.getBuildAndTestStudentSubmissionsAfterDueDate(), exercise.getTestCasesChanged(), exercise.getProjectKey(), exercise.getSubmissionPolicy(),
                exercise.getProjectType(), exercise.isReleaseTestsWithExampleSolution(), UpdateProgrammingExerciseBuildConfigDTO.of(exercise.getBuildConfig()));
    }
}
