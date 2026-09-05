package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingDtoCollections;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;

/**
 * DTO for creating and updating modeling exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude
public record UpdateModelingExerciseDTO(Long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions, ZonedDateTime releaseDate,
        ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DiagramType diagramType,
        String exampleSolutionModel, String exampleSolutionExplanation, Long courseId, Long exerciseGroupId, ExerciseMode mode, TeamAssignmentConfigDTO teamAssignmentConfig,
        PlagiarismDetectionConfigDTO plagiarismDetectionConfig, List<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks)
        implements CompetencyLinksHolderDTO {

    /**
     * Creates a DTO from a ModelingExercise entity.
     * Used when you need to send exercise data to the client for editing.
     *
     * @param exercise the ModelingExercise entity to convert
     * @return a new UpdateModelingExerciseDTO with data from the entity
     */
    public static UpdateModelingExerciseDTO of(ModelingExercise exercise) {
        if (exercise == null) {
            throw new BadRequestAlertException("No modeling exercise was provided.", "modelingExercise", "modelingExercise.isNull");
        }
        // Only a directly-attached course yields a courseId (isCourseExercise() checks the direct course field), so an exam
        // exercise yields courseId == null, mirroring the client which sends only the exerciseGroupId for exam exercises and
        // keeps the course/exerciseGroup exclusivity intact.
        Long courseId = exercise.isCourseExercise() ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        List<GradingCriterionDTO> gradingCriterionDTOs = ModelingDtoCollections.listFromInitializedSet(exercise.getGradingCriteria(), GradingCriterionDTO::of);

        Set<CompetencyLinkDTO> competencyLinkDTOs = ModelingDtoCollections.setFromInitializedSet(exercise.getCompetencyLinks(), CompetencyLinkDTO::of);
        TeamAssignmentConfigDTO teamAssignmentConfig = Hibernate.isInitialized(exercise.getTeamAssignmentConfig()) ? TeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig())
                : null;
        PlagiarismDetectionConfigDTO plagiarismDetectionConfig = Hibernate.isInitialized(exercise.getPlagiarismDetectionConfig())
                ? PlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig())
                : null;
        // categories is a LAZY @ElementCollection; copy it (guarded) so the DTO never holds the live Hibernate persistent
        // set (a DTO toString via LoggingAspect would otherwise trigger a LazyInitializationException on Exercise.categories).
        Set<String> categories = ModelingDtoCollections.copyInitializedSet(exercise.getCategories());
        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(), categories,
                exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getDiagramType(),
                exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, exercise.getMode(), teamAssignmentConfig,
                plagiarismDetectionConfig, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
