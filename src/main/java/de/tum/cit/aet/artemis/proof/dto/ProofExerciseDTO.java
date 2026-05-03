package de.tum.cit.aet.artemis.proof.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;

/**
 * Data Transfer Object for {@link ProofExercise}.
 * <p>
 * Used for all exercise REST endpoints (create, update, get, list, import).
 * <p>
 * <b>Why use a DTO instead of the entity directly?</b>
 * <ul>
 * <li><b>Security:</b> Prevents mass assignment — the client cannot inject values for fields like {@code course} or internal state.</li>
 * <li><b>API contract:</b> Provides a stable API decoupled from the internal entity structure.</li>
 * <li><b>Validation:</b> Allows request-specific validation without polluting the entity with HTTP-layer concerns.</li>
 * </ul>
 *
 * @param id                                     the exercise ID (null for create, set for update/response)
 * @param title                                  the exercise title
 * @param shortName                              the short name used for identification
 * @param problemStatement                       the problem description shown to students
 * @param description                            internal description / proof instructions (proof-specific)
 * @param predefinedCheckboxState                expected checkbox value for 100% score (proof-specific)
 * @param exampleSolution                        example solution text
 * @param categories                             exercise categories as JSON-encoded strings
 * @param difficulty                             the difficulty level
 * @param maxPoints                              maximum achievable points
 * @param bonusPoints                            additional bonus points
 * @param includedInOverallScore                 how this exercise counts toward the course grade
 * @param allowComplaintsForAutomaticAssessments whether complaints are allowed
 * @param allowFeedbackRequests                  whether feedback requests are enabled
 * @param presentationScoreEnabled               whether presentation scores are tracked
 * @param secondCorrectionEnabled                whether a second correction round is enabled
 * @param feedbackSuggestionModule               the AI feedback suggestion module identifier
 * @param gradingInstructions                    free-text grading instructions for tutors
 * @param releaseDate                            when the exercise becomes visible to students
 * @param startDate                              when students can start working
 * @param dueDate                                submission deadline
 * @param assessmentDueDate                      deadline for tutors to complete assessments
 * @param exampleSolutionPublicationDate         when the example solution becomes visible
 * @param courseId                               the course ID (for course exercises)
 * @param exerciseGroupId                        the exam exercise group ID (for exam exercises)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProofExerciseDTO(Long id, String title, String shortName, String problemStatement, String description, Boolean predefinedCheckboxState, String exampleSolution,
        Set<String> categories, DifficultyLevel difficulty, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore,
        Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled,
        String feedbackSuggestionModule, String gradingInstructions, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
        ZonedDateTime exampleSolutionPublicationDate, Long courseId, Long exerciseGroupId) {

    /**
     * Creates a DTO from a {@link ProofExercise} entity.
     *
     * @param exercise the entity to convert
     * @return a new DTO populated from the entity
     */
    public static ProofExerciseDTO of(ProofExercise exercise) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;
        return new ProofExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getProblemStatement(), exercise.getDescription(),
                exercise.isPredefinedCheckboxState(), exercise.getExampleSolution(), exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(),
                exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), courseId,
                exerciseGroupId);
    }

    /**
     * Applies the fields of this DTO to an existing {@link ProofExercise} entity.
     * Course and ExerciseGroup associations are NOT applied here — the caller must set those from the database.
     *
     * @param exercise the entity to update in-place
     */
    public void applyToEntity(ProofExercise exercise) {
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setProblemStatement(problemStatement);
        exercise.setDescription(description);
        exercise.setPredefinedCheckboxState(predefinedCheckboxState);
        exercise.setExampleSolution(exampleSolution);
        exercise.setCategories(categories);
        exercise.setDifficulty(difficulty);
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints);
        exercise.setIncludedInOverallScore(includedInOverallScore);
        exercise.setGradingInstructions(gradingInstructions);
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(startDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setAllowFeedbackRequests(Boolean.TRUE.equals(allowFeedbackRequests));
        exercise.setAllowComplaintsForAutomaticAssessments(Boolean.TRUE.equals(allowComplaintsForAutomaticAssessments));
        exercise.setPresentationScoreEnabled(presentationScoreEnabled);
        exercise.setSecondCorrectionEnabled(Boolean.TRUE.equals(secondCorrectionEnabled));
        if (feedbackSuggestionModule != null) {
            exercise.setFeedbackSuggestionModule(feedbackSuggestionModule);
        }
    }
}
