package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
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
 * Input DTO for importing a modeling exercise.
 * Superset of {@link UpdateModelingExerciseDTO} with the additional configuration needed during import.
 * Dumb DTO: only scalars, enums, date/time values, and nested DTOs. The controller builds the entity from this payload.
 */
@JsonInclude
public record ImportModelingExerciseDTO(Long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        ExerciseMode mode, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String gradingInstructions, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation,
        Long courseId, Long exerciseGroupId, TeamAssignmentConfigDTO teamAssignmentConfig, PlagiarismDetectionConfigDTO plagiarismDetectionConfig,
        List<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks) implements CompetencyLinksHolderDTO {

    /**
     * Creates an ImportModelingExerciseDTO from the given source/target modeling exercise (used for tests and import flows).
     *
     * @param exercise the modeling exercise to convert
     * @return the corresponding import DTO, or {@code null} if the exercise is {@code null}
     */
    public static ImportModelingExerciseDTO of(ModelingExercise exercise) {
        if (exercise == null) {
            return null;
        }
        // Only a directly-attached course yields a courseId (isCourseExercise() checks the direct course field), so an exam
        // exercise yields courseId == null, keeping the course/exerciseGroup exclusivity intact for import requests.
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

        return new ImportModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(), categories,
                exercise.getDifficulty(), exercise.getMode(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), exercise.getDiagramType(), exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), courseId,
                exerciseGroupId, teamAssignmentConfig, plagiarismDetectionConfig, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
