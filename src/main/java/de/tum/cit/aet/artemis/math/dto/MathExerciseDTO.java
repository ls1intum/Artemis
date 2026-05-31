package de.tum.cit.aet.artemis.math.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.grader.GraderType;

/**
 * Data Transfer Object for {@link MathExercise}.
 * Used for all exercise REST endpoints (create, update, get, list, import).
 *
 * @param id                                     the exercise ID (null for create, set for update/response)
 * @param title                                  the exercise title
 * @param shortName                              the short name used for identification
 * @param problemStatement                       the problem description shown to students
 * @param description                            internal description / math instructions (math-specific)
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
 * @param courseId                               the course ID (math exercises are course-only)
 * @param sourceExpression                       the starting expression of the math (root MathNode)
 * @param targetExpression                       the goal expression students must derive
 * @param manualDerivation                       true if students write the result expression themselves (false = system auto-applies)
 * @param allowVerification                      whether students may trigger math verification
 * @param onlyShowApplicableRules                whether the rule palette shows only rules applicable at the selected node
 * @param partialCreditEnabled                   whether partial credit is awarded proportionally based on valid steps completed
 * @param graderType                             which {@link GraderType} backend grades this exercise (only REWRITE_CHAIN is wired today; M3+ adds Lean / Isabelle / egg)
 * @param goalMode                               how the goal is encoded: TRANSFORMATION (source→target) or EQUATION (single goal tree closed by tautology)
 * @param goalExpression                         the goal tree for EQUATION mode (typically an {@code equality(LHS, RHS)}); {@code null} in TRANSFORMATION mode
 * @param acNormalization                        whether the grader treats {@code +} and {@code ·} as commutative/associative for equality comparisons
 * @param exampleDerivations                     instructor-supplied example derivations (each is an ordered list of steps)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MathExerciseDTO(Long id, String title, String shortName, String problemStatement, String description, String exampleSolution, Set<String> categories,
        DifficultyLevel difficulty, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments,
        Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions,
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, Long courseId,
        MathNode sourceExpression, MathNode targetExpression, Boolean manualDerivation, Boolean allowVerification, Boolean onlyShowApplicableRules, Boolean partialCreditEnabled,
        GraderType graderType, GoalMode goalMode, MathNode goalExpression, Boolean acNormalization, List<List<DerivationStepDTO>> exampleDerivations) {

    /**
     * @param exercise the entity to project
     * @return a DTO carrying the entity's user-facing fields
     */
    public static MathExerciseDTO of(MathExercise exercise) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        return new MathExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getProblemStatement(), exercise.getDescription(),
                exercise.getExampleSolution(), exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getIncludedInOverallScore(), exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(),
                exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), courseId,
                exercise.getSourceExpression(), exercise.getTargetExpression(), exercise.isManualDerivation(), exercise.isAllowVerification(), exercise.isOnlyShowApplicableRules(),
                exercise.isPartialCreditEnabled(), exercise.getGraderType(), exercise.getGoalMode(), exercise.getGoalExpression(), exercise.isAcNormalization(),
                exercise.getExampleDerivations());
    }

    /**
     * Applies the fields of this DTO to an existing {@link MathExercise} entity.
     * The Course association is not applied here — the caller must set it from the database.
     */
    public void applyToEntity(MathExercise exercise) {
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
        exercise.setManualDerivation(Boolean.TRUE.equals(manualDerivation));
        exercise.setAllowVerification(allowVerification == null || allowVerification);
        exercise.setOnlyShowApplicableRules(Boolean.TRUE.equals(onlyShowApplicableRules));
        exercise.setPartialCreditEnabled(Boolean.TRUE.equals(partialCreditEnabled));
        exercise.setGraderType(graderType == null ? GraderType.REWRITE_CHAIN : graderType);
        exercise.setGoalMode(goalMode == null ? GoalMode.TRANSFORMATION : goalMode);
        exercise.setGoalExpression(goalExpression);
        exercise.setAcNormalization(Boolean.TRUE.equals(acNormalization));
        exercise.setExampleDerivations(exampleDerivations);
    }
}
