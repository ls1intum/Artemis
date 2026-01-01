package de.tum.cit.aet.artemis.text.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * DTO for updating text exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateTextExerciseDTO(
        // Base exercise fields
        Long id, String title, String shortName, @NotNull Double maxPoints, Double bonusPoints, AssessmentType assessmentType, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DifficultyLevel difficulty, ExerciseMode mode,
        // Exercise fields
        IncludedInOverallScore includedInOverallScore, String problemStatement, String gradingInstructions, Set<String> categories, Boolean presentationScoreEnabled,
        Boolean secondCorrectionEnabled, String feedbackSuggestionModule, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, String channelName,
        // Competency links as DTOs
        Set<CompetencyLinkDTO> competencyLinks,
        // Course/ExerciseGroup references (by ID)
        Long courseId, Long exerciseGroupId,
        // TextExercise specific fields
        String exampleSolution) implements CompetencyLinksHolderDTO {

    /**
     * Creates a TextExerciseFromEditorDTO from the given TextExercise domain object.
     *
     * @param exercise the text exercise to convert
     * @return the corresponding DTO
     */
    public static UpdateTextExerciseDTO of(TextExercise exercise) {
        // Only convert competency links if they are initialized (to avoid LazyInitializationException)
        Set<CompetencyLinkDTO> competencyLinkDTOs = null;
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.isEmpty() ? Set.of() : competencyLinks.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }

        // Determine courseId and exerciseGroupId based on the exercise type
        Long courseId = exercise.isCourseExercise() ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        return new UpdateTextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getAssessmentType(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), exercise.getDifficulty(), exercise.getMode(), exercise.getIncludedInOverallScore(), exercise.getProblemStatement(),
                exercise.getGradingInstructions(), exercise.getCategories(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.getFeedbackSuggestionModule(), exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getChannelName(),
                competencyLinkDTOs, courseId, exerciseGroupId, exercise.getExampleSolution());
    }

    /**
     * Applies the DTO values to an existing TextExercise entity.
     * This updates the managed entity with values from the DTO.
     *
     * @param textExercise the existing text exercise to update
     */
    public void applyTo(TextExercise textExercise) {
        // Base exercise fields
        if (title != null) {
            textExercise.setTitle(title);
        }
        if (shortName != null) {
            textExercise.setShortName(shortName);
        }
        if (maxPoints != null) {
            textExercise.setMaxPoints(maxPoints);
        }
        if (bonusPoints != null) {
            textExercise.setBonusPoints(bonusPoints);
        }
        if (assessmentType != null) {
            textExercise.setAssessmentType(assessmentType);
        }
        textExercise.setReleaseDate(releaseDate);
        textExercise.setStartDate(startDate);
        textExercise.setDueDate(dueDate);
        textExercise.setAssessmentDueDate(assessmentDueDate);
        textExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        if (difficulty != null) {
            textExercise.setDifficulty(difficulty);
        }
        if (mode != null) {
            textExercise.setMode(mode);
        }

        // Exercise fields
        if (includedInOverallScore != null) {
            textExercise.setIncludedInOverallScore(includedInOverallScore);
        }
        if (problemStatement != null) {
            textExercise.setProblemStatement(problemStatement);
        }
        if (gradingInstructions != null) {
            textExercise.setGradingInstructions(gradingInstructions);
        }
        if (categories != null) {
            textExercise.setCategories(categories);
        }
        if (presentationScoreEnabled != null) {
            textExercise.setPresentationScoreEnabled(presentationScoreEnabled);
        }
        if (secondCorrectionEnabled != null) {
            textExercise.setSecondCorrectionEnabled(secondCorrectionEnabled);
        }
        // feedbackSuggestionModule can be null intentionally, so we always set it
        textExercise.setFeedbackSuggestionModule(feedbackSuggestionModule);
        if (allowComplaintsForAutomaticAssessments != null) {
            textExercise.setAllowComplaintsForAutomaticAssessments(allowComplaintsForAutomaticAssessments);
        }
        if (allowFeedbackRequests != null) {
            textExercise.setAllowFeedbackRequests(allowFeedbackRequests);
        }
        if (channelName != null) {
            textExercise.setChannelName(channelName);
        }

        // TextExercise specific fields
        // exampleSolution can be null intentionally
        textExercise.setExampleSolution(exampleSolution);
    }
}
