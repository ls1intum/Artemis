package de.tum.in.www1.artemis.service;

import java.util.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.repository.*;

public abstract class ExerciseImportService {

    protected final ExampleSubmissionRepository exampleSubmissionRepository;

    protected final SubmissionRepository submissionRepository;

    protected final ResultRepository resultRepository;

    protected final TextBlockRepository textBlockRepository;

    public ExerciseImportService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            TextBlockRepository textBlockRepository) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.textBlockRepository = textBlockRepository;
    }

    void copyExerciseBasis(final Exercise newExercise, final Exercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        if (importedExercise.isCourseExercise()) {
            newExercise.setCourse(importedExercise.getCourseViaExerciseGroupOrCourseMember());
        }
        else {
            newExercise.setExerciseGroup(importedExercise.getExerciseGroup());
        }

        newExercise.setTitle(importedExercise.getTitle());
        newExercise.setMaxPoints(importedExercise.getMaxPoints());
        newExercise.setBonusPoints(importedExercise.getBonusPoints());
        newExercise.setIncludedInOverallScore(importedExercise.getIncludedInOverallScore());
        newExercise.setAssessmentType(importedExercise.getAssessmentType());
        newExercise.setProblemStatement(importedExercise.getProblemStatement());
        newExercise.setReleaseDate(importedExercise.getReleaseDate());
        newExercise.setDueDate(importedExercise.getDueDate());
        newExercise.setAssessmentDueDate(importedExercise.getAssessmentDueDate());
        newExercise.setExampleSolutionPublicationDate(null); // This should not be imported.
        newExercise.validateDates();
        newExercise.setDifficulty(importedExercise.getDifficulty());
        newExercise.setGradingInstructions(importedExercise.getGradingInstructions());
        newExercise.setGradingCriteria(importedExercise.copyGradingCriteria(gradingInstructionCopyTracker));
        if (newExercise.getExerciseGroup() != null) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
        }
        else {
            newExercise.setCategories(importedExercise.getCategories());
            newExercise.setMode(importedExercise.getMode());
            if (newExercise.getMode() == ExerciseMode.TEAM) {
                newExercise.setTeamAssignmentConfig(importedExercise.getTeamAssignmentConfig().copyTeamAssignmentConfig());
            }
        }
    }

    abstract Set<ExampleSubmission> copyExampleSubmission(final Exercise templateExercise, final Exercise newExercise);

    abstract Submission copySubmission(final Submission originalSubmission);

    /** This helper method does a hard copy of the result of a submission.
     * To copy the feedback, it calls {@link #copyFeedback(List, Result)}
     *
     * @param originalResult The original result to be copied
     * @param newSubmission The submission in which we link the result clone
     * @return The cloned result
     */
    Result copyExampleResult(Result originalResult, Submission newSubmission) {
        Result newResult = new Result();
        newResult.setAssessmentType(originalResult.getAssessmentType());
        newResult.setAssessor(originalResult.getAssessor());
        newResult.setCompletionDate(originalResult.getCompletionDate());
        newResult.setExampleResult(true);
        newResult.setRated(true);
        newResult.setResultString(originalResult.getResultString());
        newResult.setHasFeedback(originalResult.getHasFeedback());
        newResult.setScore(originalResult.getScore());
        newResult.setFeedbacks(copyFeedback(originalResult.getFeedbacks(), newResult));
        // Cut relationship to parent because result is an ordered collection
        newResult.setSubmission(null);

        newResult = resultRepository.save(newResult);

        // Restore relationship to parent.
        newResult.setSubmission(newSubmission);

        return newResult;
    }

    /** This helper functions does a hard copy of the feedbacks.
     *
     * @param originalFeedbacks The original list of feedbacks to be copied
     * @param newResult The result in which we link the new feedback
     * @return The cloned list of feedback
     */
    private List<Feedback> copyFeedback(List<Feedback> originalFeedbacks, Result newResult) {
        List<Feedback> newFeedbacks = new ArrayList<>();
        for (final var originalFeedback : originalFeedbacks) {
            Feedback newFeedback = new Feedback();
            newFeedback.setCredits(originalFeedback.getCredits());
            newFeedback.setDetailText(originalFeedback.getDetailText());
            newFeedback.setPositive(originalFeedback.isPositive());
            newFeedback.setReference(originalFeedback.getReference());
            newFeedback.setType(originalFeedback.getType());
            newFeedback.setText(originalFeedback.getText());
            newFeedback.setResult(newResult);

            // Original GradingInstructions should be replaced with copied GradingInstructions before save.
            newFeedback.setGradingInstruction(originalFeedback.getGradingInstruction());
            newFeedbacks.add(newFeedback);
        }
        return newFeedbacks;
    }
}
