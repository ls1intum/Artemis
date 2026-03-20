package de.tum.cit.aet.artemis.text.dto;

import static de.tum.cit.aet.artemis.core.util.DTOHelper.mapInitializedSet;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyExerciseLinkDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * DTO for updating text exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateTextExerciseDTO(Long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions, ZonedDateTime releaseDate,
        ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, String exampleSolution, Long courseId,
        Long exerciseGroupId, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyExerciseLinkDTO> competencyLinks) {

    /**
     * Creates an UpdateTextExerciseDTO from the given TextExercise domain object.
     *
     * @param exercise the text exercise to convert
     * @return the corresponding DTO
     */
    public static UpdateTextExerciseDTO of(TextExercise exercise) {
        if (exercise == null) {
            throw new BadRequestAlertException("No text exercise was provided.", "textExercise", "textExercise.isNull");
        }

        Long courseId = Optional.ofNullable(exercise.getCourseViaExerciseGroupOrCourseMember()).map(c -> c.getId()).orElse(null);
        Long exerciseGroupId = Optional.ofNullable(exercise.getExerciseGroup()).map(g -> g.getId()).orElse(null);

        Set<GradingCriterionDTO> gradingCriterionDTOs = mapInitializedSet(exercise.getGradingCriteria(), GradingCriterionDTO::of);
        Set<CompetencyExerciseLinkDTO> competencyLinkDTOs = mapInitializedSet(exercise.getCompetencyLinks(), CompetencyExerciseLinkDTO::of);

        return new UpdateTextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolution(),
                courseId, exerciseGroupId, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
