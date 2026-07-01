package de.tum.cit.aet.artemis.text.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Input DTO for importing a text exercise.
 * Superset of {@link UpdateTextExerciseDTO} with the additional configuration needed during import.
 * Dumb DTO: only scalars, enums, date/time values, and nested DTOs. The controller builds the entity from this payload.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImportTextExerciseDTO(Long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        ExerciseMode mode, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments,
        Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions,
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate,
        String exampleSolution, Long courseId, Long exerciseGroupId, TeamAssignmentConfigDTO teamAssignmentConfig, PlagiarismDetectionConfigDTO plagiarismDetectionConfig,
        Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks) implements CompetencyLinksHolderDTO {

    /**
     * Creates an ImportTextExerciseDTO from the given source/target text exercise (used for tests and import flows).
     *
     * @param exercise the text exercise to convert
     * @return the corresponding import DTO, or {@code null} if the exercise is {@code null}
     */
    public static ImportTextExerciseDTO of(TextExercise exercise) {
        if (exercise == null) {
            return null;
        }
        // Only a directly-attached course yields a courseId (isCourseExercise() checks the direct course field), so an exam
        // exercise yields courseId == null, keeping the course/exerciseGroup exclusivity intact for import requests.
        Long courseId = exercise.isCourseExercise() ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        Set<GradingCriterionDTO> gradingCriterionDTOs = criteria != null && Hibernate.isInitialized(criteria)
                ? criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet())
                : null;
        Set<CompetencyExerciseLink> links = exercise.getCompetencyLinks();
        Set<CompetencyLinkDTO> competencyLinkDTOs = links != null && Hibernate.isInitialized(links) ? links.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet()) : null;
        TeamAssignmentConfigDTO teamAssignmentConfig = Hibernate.isInitialized(exercise.getTeamAssignmentConfig()) ? TeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig())
                : null;
        PlagiarismDetectionConfigDTO plagiarismDetectionConfig = Hibernate.isInitialized(exercise.getPlagiarismDetectionConfig())
                ? PlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig())
                : null;

        return new ImportTextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMode(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolution(),
                courseId, exerciseGroupId, teamAssignmentConfig, plagiarismDetectionConfig, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
