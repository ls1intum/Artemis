package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

/**
 * DTO for creating and updating modeling exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateModelingExerciseDTO(Long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions, ZonedDateTime releaseDate,
        ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DiagramType diagramType,
        String exampleSolutionModel, String exampleSolutionExplanation, Long courseId, Long exerciseGroupId, ExerciseMode mode, TeamAssignmentConfigDTO teamAssignmentConfig,
        Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks) implements CompetencyLinksHolderDTO {

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

        Set<GradingCriterionDTO> gradingCriterionDTOs;
        Set<CompetencyLinkDTO> competencyLinkDTOs;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();

        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        else {
            gradingCriterionDTOs = null;
        }
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.isEmpty() ? Set.of() : competencyLinks.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }
        else {
            competencyLinkDTOs = null;
        }
        TeamAssignmentConfigDTO teamAssignmentConfig = Hibernate.isInitialized(exercise.getTeamAssignmentConfig()) ? TeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig())
                : null;
        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getDiagramType(),
                exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, exercise.getMode(), teamAssignmentConfig,
                gradingCriterionDTOs, competencyLinkDTOs);
    }
}
