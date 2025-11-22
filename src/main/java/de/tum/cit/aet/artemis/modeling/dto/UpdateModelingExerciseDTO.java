package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Positive;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateModelingExerciseDTO(long id, @Nullable String title, @Nullable String channelName, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable Set<String> categories, @Nullable DifficultyLevel difficulty, @Nullable @Positive Double maxPoints, @Nullable @Positive Double bonusPoints,
        @Nullable IncludedInOverallScore includedInOverallScore, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate,
        @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String exampleSolutionModel,
        @Nullable String exampleSolutionExplanation, @Nullable Long courseId, @Nullable Long exerciseGroupId, @Nullable Set<GradingCriterionDTO> gradingCriteria,
        @Nullable String gradingInstructions) {

    /**
     * Applies this DTO's fields to the given exercise, mutating it in place.
     *
     * @param exercise the exercise to update (will be mutated)
     * @return the same exercise instance after applying updates
     */
    public ModelingExercise update(ModelingExercise exercise) {
        if (this.title != null) {
            exercise.setTitle(this.title);
        }
        if (this.channelName != null) {
            exercise.setChannelName(this.channelName);
        }
        if (this.shortName != null) {
            exercise.setShortName(this.shortName);
        }
        if (this.problemStatement != null) {
            exercise.setProblemStatement(this.problemStatement);
        }
        if (this.categories != null) {
            exercise.setCategories(this.categories);
        }
        if (this.difficulty != null) {
            exercise.setDifficulty(this.difficulty);
        }
        if (this.maxPoints != null) {
            exercise.setMaxPoints(this.maxPoints);
        }
        if (this.bonusPoints != null) {
            exercise.setBonusPoints(this.bonusPoints);
        }
        if (this.includedInOverallScore != null) {
            exercise.setIncludedInOverallScore(this.includedInOverallScore);
        }
        if (this.releaseDate != null) {
            exercise.setReleaseDate(this.releaseDate);
        }
        if (this.startDate != null) {
            exercise.setStartDate(this.startDate);
        }
        if (this.dueDate != null) {
            exercise.setDueDate(this.dueDate);
        }
        if (this.assessmentDueDate != null) {
            exercise.setAssessmentDueDate(this.assessmentDueDate);
        }
        if (this.exampleSolutionModel != null) {
            exercise.setExampleSolutionModel(this.exampleSolutionModel);
        }
        if (this.exampleSolutionExplanation != null) {
            exercise.setExampleSolutionExplanation(this.exampleSolutionExplanation);
        }
        if (this.exampleSolutionPublicationDate != null) {
            exercise.setExampleSolutionPublicationDate(this.exampleSolutionPublicationDate);
        }
        if (this.gradingCriteria != null) {
            Set<GradingCriterion> existingCriteria = exercise.getGradingCriteria();
            var existingById = (existingCriteria != null && Hibernate.isInitialized(existingCriteria))
                    ? existingCriteria.stream().filter(gc -> gc.getId() != null).collect(Collectors.toMap(GradingCriterion::getId, gc -> gc))
                    : Map.<Long, GradingCriterion>of();

            Set<GradingCriterion> updatedCriteria = this.gradingCriteria.stream().map(dto -> {
                GradingCriterion criterion = dto.id() != null ? existingById.get(dto.id()) : null;
                if (criterion == null) {
                    criterion = dto.toEntity();
                    criterion.setExercise(exercise);
                }
                else {
                    dto.applyTo(criterion);
                }
                return criterion;
            }).collect(Collectors.toSet());

            exercise.setGradingCriteria(updatedCriteria);
        }
        if (this.gradingInstructions != null) {
            exercise.setGradingInstructions(this.gradingInstructions);
        }
        return exercise;
    }

    /**
     * Creates a DTO from a ModelingExercise entity.
     * Used when you need to send exercise data to the client for editing.
     *
     * @param exercise the ModelingExercise entity to convert
     * @return a new UpdateModelingExerciseDTO with data from the entity
     */
    public static UpdateModelingExerciseDTO of(ModelingExercise exercise) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterionDTO> gradingCriterionDTOs = null;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }

        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(),
                exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, gradingCriterionDTOs, exercise.getGradingInstructions());
    }
}
