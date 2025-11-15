package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateModelingExerciseDTO(long id, @Nullable String title, @Nullable String channelName, @Nullable String problemStatement, @Nullable Set<String> categories,
        @Nullable DifficultyLevel difficulty, @Nullable @Positive Double maxPoints, @Nullable @Positive Double bonusPoints, @Nullable IncludedInOverallScore includedInOverallScore,
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String exampleSolutionModel, @Nullable String exampleSolutionExplanation, @Nullable Long courseId,
        @Nullable Long exerciseGroupId, @Nullable Set<GradingCriterionDTO> gradingCriteria) {

    /**
     * Apply this DTO changes to a ModelingExercise entity.
     *
     * @param existingExercise the exercise to update
     * @return the updated ModelingExercise entity (same instance, modified in place)
     */
    public ModelingExercise update(ModelingExercise existingExercise) {
        if (this.title != null) {
            existingExercise.setTitle(this.title);
        }
        if (this.channelName != null) {
            existingExercise.setChannelName(this.channelName);
        }
        if (this.problemStatement != null) {
            existingExercise.setProblemStatement(this.problemStatement);
        }
        if (this.categories != null) {
            existingExercise.setCategories(this.categories);
        }
        if (this.difficulty != null) {
            existingExercise.setDifficulty(this.difficulty);
        }
        if (this.maxPoints != null) {
            existingExercise.setMaxPoints(this.maxPoints);
        }
        if (this.bonusPoints != null) {
            existingExercise.setBonusPoints(this.bonusPoints);
        }
        if (this.includedInOverallScore != null) {
            existingExercise.setIncludedInOverallScore(this.includedInOverallScore);
        }
        if (this.releaseDate != null) {
            existingExercise.setReleaseDate(this.releaseDate);
        }
        if (this.startDate != null) {
            existingExercise.setStartDate(this.startDate);
        }
        if (this.dueDate != null) {
            existingExercise.setDueDate(this.dueDate);
        }
        if (this.assessmentDueDate != null) {
            existingExercise.setAssessmentDueDate(this.assessmentDueDate);
        }
        if (this.exampleSolutionPublicationDate != null) {
            existingExercise.setExampleSolutionPublicationDate(this.exampleSolutionPublicationDate);
        }
        if (this.exampleSolutionModel != null) {
            existingExercise.setExampleSolutionModel(this.exampleSolutionModel);
        }
        if (this.exampleSolutionExplanation != null) {
            existingExercise.setExampleSolutionExplanation(this.exampleSolutionExplanation);
        }
        if (this.gradingCriteria != null) {
            existingExercise.setGradingCriteria(this.gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toSet()));
        }
        return existingExercise;
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
        Set<GradingCriterionDTO> gradingCriterionDTOs = exercise.getGradingCriteria() != null
                ? exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet())
                : Set.of();
        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getProblemStatement(), exercise.getCategories(),
                exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolutionModel(),
                exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, gradingCriterionDTOs);
    }
}
