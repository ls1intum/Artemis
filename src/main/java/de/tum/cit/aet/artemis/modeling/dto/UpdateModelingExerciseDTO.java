package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateModelingExerciseDTO(long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions, ZonedDateTime releaseDate,
        ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, String exampleSolutionModel,
        String exampleSolutionExplanation, Long courseId, Long exerciseGroupId, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyExerciseLinkDTO> competencyLinks) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyExerciseLinkDTO(@NotNull CourseCompetencyDTO courseCompetencyDTO, Double weight, Long courseId) {

        /**
         * Creates a DTO from a CompetencyExerciseLink entity.
         *
         * @param competencyExerciseLink CompetencyExerciseLink entity to convert
         * @return a new CompetencyExerciseLinkDTO with data from the entity
         */
        public static CompetencyExerciseLinkDTO of(CompetencyExerciseLink competencyExerciseLink) {
            if (competencyExerciseLink == null) {
                throw new BadRequestAlertException("No competency link was provided.", "competencyExerciseLink", "competencyExerciseLink.isNull");
            }
            if (competencyExerciseLink.getCompetency() == null) {
                throw new BadRequestAlertException("The competency link must reference a competency.", "competencyExerciseLink", "competencyExerciseLink.competencyMissing");
            }
            if (competencyExerciseLink.getCompetency().getCourse() == null) {
                throw new BadRequestAlertException("The competency referenced by this link is not associated with a course.", "competencyExerciseLink",
                        "competencyExerciseLink.courseMissing");
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
        if (exercise == null) {
            throw new BadRequestAlertException("No modeling exercise was provided.", "modelingExercise", "modelingExercise.isNull");
        }
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterionDTO> gradingCriterionDTOs;
        Set<CompetencyExerciseLinkDTO> competencyLinkDTOs;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();

        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        else {
            gradingCriterionDTOs = null;
        }
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.isEmpty() ? Set.of() : competencyLinks.stream().map(CompetencyExerciseLinkDTO::of).collect(Collectors.toSet());
        }
        else {
            competencyLinkDTOs = null;
        }
        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolutionModel(),
                exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
