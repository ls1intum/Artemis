package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateModelingExerciseDTO(long id, @Nullable String title, @Nullable String channelName, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable Set<String> categories, @Nullable DifficultyLevel difficulty, @Nullable @Positive Double maxPoints, @Nullable @PositiveOrZero Double bonusPoints,
        @Nullable IncludedInOverallScore includedInOverallScore, @Nullable Boolean allowFeedbackRequests, @Nullable String gradingInstructions, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        @Nullable String exampleSolutionModel, @Nullable String exampleSolutionExplanation, @Nullable Long courseId, @Nullable Long exerciseGroupId,
        @Nullable Set<GradingCriterionDTO> gradingCriteria, @Nullable Set<CompetencyExerciseLinkDTO> competencyLinks) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyExerciseLinkDTO(@NotNull CourseCompetencyDTO courseCompetencyDTO, double weight, Long courseId) {

        /**
         * Creates a DTO from a CompetencyExerciseLink entity.
         *
         * @param competencyExerciseLink CompetencyExerciseLink entity to convert
         * @return a new CompetencyExerciseLinkDTO with data from the entity
         */
        public static CompetencyExerciseLinkDTO of(@NotNull CompetencyExerciseLink competencyExerciseLink) {
            if (competencyExerciseLink.getCompetency().getCourse() == null) {
                throw new IllegalStateException("CompetencyExerciseLink references a competency without an associated course. Link ID: "
                        + (competencyExerciseLink.getId() != null ? competencyExerciseLink.getId() : "unknown"));
            }
            return new CompetencyExerciseLinkDTO(CourseCompetencyDTO.of(competencyExerciseLink.getCompetency()), competencyExerciseLink.getWeight(),
                    competencyExerciseLink.getCompetency().getCourse().getId());
        }
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
        Set<CompetencyExerciseLinkDTO> competencyLinkDTOs = null;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();

        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.stream().map(CompetencyExerciseLinkDTO::of).collect(Collectors.toSet());
        }

        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowFeedbackRequests(), exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(),
                exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(),
                courseId, exerciseGroupId, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
