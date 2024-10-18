package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

public abstract class ExerciseImportService {

    protected final ExampleSubmissionRepository exampleSubmissionRepository;

    protected final SubmissionRepository submissionRepository;

    protected final ResultRepository resultRepository;

    private final FeedbackService feedbackService;

    private static final Logger log = LoggerFactory.getLogger(ExerciseImportService.class);

    protected ExerciseImportService(ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            FeedbackService feedbackService) {
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.feedbackService = feedbackService;
    }

    protected void copyExerciseBasis(final Exercise newExercise, final Exercise importedExercise, final Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        if (importedExercise.isCourseExercise()) {
            newExercise.setCourse(importedExercise.getCourseViaExerciseGroupOrCourseMember());
            newExercise.setPresentationScoreEnabled(importedExercise.getPresentationScoreEnabled());
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
        newExercise.setStartDate(importedExercise.getStartDate());
        newExercise.setReleaseDate(importedExercise.getReleaseDate());
        newExercise.setDueDate(importedExercise.getDueDate());
        newExercise.setAssessmentDueDate(importedExercise.getAssessmentDueDate());
        newExercise.setExampleSolutionPublicationDate(null); // This should not be imported.
        newExercise.validateDates();
        newExercise.setDifficulty(importedExercise.getDifficulty());
        newExercise.setGradingInstructions(importedExercise.getGradingInstructions());
        newExercise.setGradingCriteria(importedExercise.copyGradingCriteria(gradingInstructionCopyTracker));
        newExercise.setCompetencyLinks(importedExercise.getCompetencyLinks());

        if (importedExercise.getPlagiarismDetectionConfig() != null) {
            newExercise.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig(importedExercise.getPlagiarismDetectionConfig()));
        }

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

    /**
     * This helper method does a hard copy of the result of a submission.
     * To copy the feedback, it calls {@link #copyFeedback(List, Result, Map)}
     *
     * @param originalResult                The original result to be copied
     * @param newSubmission                 The submission in which we link the result clone
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned result
     */
    protected Result copyExampleResult(Result originalResult, Submission newSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        Result newResult = new Result();
        newResult.setAssessmentType(originalResult.getAssessmentType());
        newResult.setAssessor(originalResult.getAssessor());
        newResult.setCompletionDate(originalResult.getCompletionDate());
        newResult.setExampleResult(true);
        newResult.setRated(true);
        newResult.setScore(originalResult.getScore());
        newResult.copyProgrammingExerciseCounters(originalResult);
        newResult.setFeedbacks(copyFeedback(originalResult.getFeedbacks(), newResult, gradingInstructionCopyTracker));
        // Cut relationship to parent because result is an ordered collection
        newResult.setSubmission(null);

        newResult = resultRepository.save(newResult);

        // Restore relationship to parent.
        newResult.setSubmission(newSubmission);

        return newResult;
    }

    /**
     * This helper functions does a hard copy of the feedbacks.
     *
     * @param originalFeedbacks             The original list of feedbacks to be copied
     * @param newResult                     The result in which we link the new feedback
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned list of feedback
     */
    private List<Feedback> copyFeedback(List<Feedback> originalFeedbacks, Result newResult, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        List<Feedback> newFeedbacks = new ArrayList<>();
        for (final var originalFeedback : originalFeedbacks) {
            final Feedback newFeedback = feedbackService.copyFeedback(originalFeedback);
            newFeedback.setResult(newResult);

            // Original GradingInstructions should be replaced with copied GradingInstructions before save.
            GradingInstruction originalGradingInstruction = originalFeedback.getGradingInstruction();
            if (originalGradingInstruction != null) {
                GradingInstruction newGradingInstruction = gradingInstructionCopyTracker.get(originalGradingInstruction.getId());
                if (newGradingInstruction == null) {
                    log.warn("New Grading Instruction is not found for original Grading Instruction with id {}", originalGradingInstruction.getId());
                }
                newFeedback.setGradingInstruction(newGradingInstruction);
            }
            newFeedbacks.add(newFeedback);
        }
        return newFeedbacks;
    }
}
