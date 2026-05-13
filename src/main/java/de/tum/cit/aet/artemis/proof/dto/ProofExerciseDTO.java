package de.tum.cit.aet.artemis.proof.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO.DerivationStepDTO;

/**
 * Data Transfer Object for {@link ProofExercise}.
 * Used for all exercise REST endpoints (create, update, get, list, import).
 *
 * @param id                                     the exercise ID (null for create, set for update/response)
 * @param title                                  the exercise title
 * @param shortName                              the short name used for identification
 * @param problemStatement                       the problem description shown to students
 * @param description                            internal description / proof instructions (proof-specific)
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
 * @param sourceExpression                       the starting expression of the proof (root MathNode)
 * @param targetExpression                       the goal expression students must derive
 * @param exampleDerivations                     instructor-supplied example derivations (each is an ordered list of steps)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProofExerciseDTO(Long id, String title, String shortName, String problemStatement, String description, String exampleSolution, Set<String> categories,
        DifficultyLevel difficulty, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments,
        Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions,
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, Long courseId,
        Long exerciseGroupId, MathNode sourceExpression, MathNode targetExpression, List<List<DerivationStepDTO>> exampleDerivations) {

    public static ProofExerciseDTO of(ProofExercise exercise) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;
        return new ProofExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getProblemStatement(), exercise.getDescription(),
                exercise.getExampleSolution(), exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getIncludedInOverallScore(), exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(),
                exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), courseId,
                exerciseGroupId, exercise.getSourceExpression(), exercise.getTargetExpression(), exercise.getExampleDerivations());
    }

    /**
     * Applies the fields of this DTO to an existing {@link ProofExercise} entity.
     * Course and ExerciseGroup associations are NOT applied here — the caller must set those from the database.
     */
    public void applyToEntity(ProofExercise exercise) {
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setProblemStatement(problemStatement);
        exercise.setDescription(description);
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
        exercise.setSourceExpression(sourceExpression);
        exercise.setTargetExpression(targetExpression);
        exercise.setExampleDerivations(exampleDerivations);
    }
}
