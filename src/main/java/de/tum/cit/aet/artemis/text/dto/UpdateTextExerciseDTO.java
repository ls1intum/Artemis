package de.tum.cit.aet.artemis.text.dto;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
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
        Long exerciseGroupId, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks) implements CompetencyLinksHolderDTO {

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

        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();

        Set<GradingCriterionDTO> gradingCriterionDTOs = mapIfInitialized(criteria, GradingCriterionDTO::of);
        Set<CompetencyLinkDTO> competencyLinkDTOs = mapIfInitialized(exercise.getCompetencyLinks(), CompetencyLinkDTO::of);

        return new UpdateTextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolution(),
                courseId, exerciseGroupId, gradingCriterionDTOs, competencyLinkDTOs);
    }

    private static <T, R> Set<R> mapIfInitialized(Collection<T> collection, Function<T, R> mapper) {
        return collection != null && Hibernate.isInitialized(collection) ? collection.stream().map(mapper).collect(Collectors.toSet()) : null;
    }
}
