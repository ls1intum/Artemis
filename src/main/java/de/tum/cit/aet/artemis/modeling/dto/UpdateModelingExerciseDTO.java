package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Positive;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

public record UpdateModelingExerciseDTO(long id, @Nullable String title, @Nullable String channelName, @Nullable String problemStatement, @Nullable Set<String> categories,
        @Nullable DifficultyLevel difficulty,
        // Points and scoring (validated in validateGeneralSettings)
        @Nullable @Positive Double maxPoints, @Nullable @Positive Double bonusPoints, @Nullable IncludedInOverallScore includedInOverallScore,
        // Dates and timing
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable Set<CompetencyExerciseLink> competencyLinks,
        // Modeling-specific fields
        @Nullable DiagramType diagramType, @Nullable String exampleSolutionModel, @Nullable String exampleSolutionExplanation,
        // For conflict check
        @Nullable Long courseId, @Nullable Long exerciseGroupId, @Nullable Set<GradingCriterion> gradingCriteria) {

    /**
     * Apply this DTO changes to a ModelingExercise entity.
     *
     * @return a new updated ModelingExercise entity with the data from this DTO
     */
    public ModelingExercise update(ModelingExercise existingExercise) {
        existingExercise.setTitle(this.title);
        existingExercise.setChannelName(this.channelName);
        if (this.problemStatement != null) {
            existingExercise.setProblemStatement(this.problemStatement);
        }
        existingExercise.setCategories(this.categories);
        if (this.difficulty != null) {
            existingExercise.setDifficulty(this.difficulty);
        }
        existingExercise.setMaxPoints(this.maxPoints);
        existingExercise.setBonusPoints(this.bonusPoints);
        existingExercise.setIncludedInOverallScore(this.includedInOverallScore);
        existingExercise.setReleaseDate(this.releaseDate);
        existingExercise.setStartDate(this.startDate);
        existingExercise.setDueDate(this.dueDate);
        existingExercise.setAssessmentDueDate(this.assessmentDueDate);
        existingExercise.setExampleSolutionPublicationDate(this.exampleSolutionPublicationDate);
        if (this.diagramType != null) {
            existingExercise.setDiagramType(this.diagramType);
        }
        existingExercise.setExampleSolutionModel(this.exampleSolutionModel);
        existingExercise.setExampleSolutionExplanation(this.exampleSolutionExplanation);
        existingExercise.setCompetencyLinks(this.competencyLinks);
        existingExercise.setGradingCriteria(gradingCriteria);
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

        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getProblemStatement(), exercise.getCategories(),
                exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getCompetencyLinks(),
                exercise.getDiagramType(), exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, exercise.getGradingCriteria());
    }
}
