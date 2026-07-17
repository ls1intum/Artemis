package de.tum.cit.aet.artemis.exercise.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
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
        copyExerciseBasis(newExercise, importedExercise, importedExercise, gradingInstructionCopyTracker);
        // When source and target are the same object, copy competency links (standalone REST import path).
        // The 4-param overload clears them because cross-course import cannot safely reference foreign competencies.
        Set<CompetencyExerciseLink> copiedLinks = new HashSet<>();
        for (CompetencyExerciseLink link : importedExercise.getCompetencyLinks()) {
            copiedLinks.add(new CompetencyExerciseLink(link.getCompetency(), newExercise, link.getWeight()));
        }
        newExercise.setCompetencyLinks(copiedLinks);
    }

    /**
     * Copies exercise fields from two sources: structural context from {@code importedExercise} (target course/group,
     * possible title/points overrides) and content from {@code templateExercise} (the original exercise with all fields).
     * <p>
     * This overload exists because exam-import and course-material-import create a skeleton {@code importedExercise}
     * carrying only destination context + optional overrides, while the original exercise data lives in {@code templateExercise}.
     *
     * @param newExercise                   the fresh entity being built
     * @param importedExercise              skeleton with target course/exerciseGroup and optional title/points overrides
     * @param templateExercise              the original exercise providing content fields
     * @param gradingInstructionCopyTracker tracker for deep-copying grading instructions
     */
    protected void copyExerciseBasis(final Exercise newExercise, final Exercise importedExercise, final Exercise templateExercise,
            final Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        // Structural context: always from importedExercise (the target destination)
        if (importedExercise.isCourseExercise()) {
            newExercise.setCourse(importedExercise.getCourseViaExerciseGroupOrCourseMember());
            newExercise.setPresentationScoreEnabled(importedExercise.getPresentationScoreEnabled());
        }
        else {
            newExercise.setExerciseGroup(importedExercise.getExerciseGroup());
        }

        // Overridable fields: use importedExercise if set, fall back to templateExercise
        newExercise.setTitle(importedExercise.getTitle() != null ? importedExercise.getTitle() : templateExercise.getTitle());
        newExercise.setMaxPoints(importedExercise.getMaxPoints() != null ? importedExercise.getMaxPoints() : templateExercise.getMaxPoints());
        newExercise.setBonusPoints(importedExercise.getBonusPoints() != null ? importedExercise.getBonusPoints() : templateExercise.getBonusPoints());

        // Content fields: always from templateExercise (the original exercise)
        newExercise.setIncludedInOverallScore(templateExercise.getIncludedInOverallScore());
        newExercise.setAssessmentType(templateExercise.getAssessmentType());
        newExercise.setProblemStatement(templateExercise.getProblemStatement());
        newExercise.setStartDate(templateExercise.getStartDate());
        newExercise.setReleaseDate(templateExercise.getReleaseDate());
        newExercise.setDueDate(templateExercise.getDueDate());
        newExercise.setAssessmentDueDate(templateExercise.getAssessmentDueDate());
        newExercise.setExampleSolutionPublicationDate(null); // This should not be imported as the client might serve the original date as the default.
        newExercise.validateDates();
        newExercise.setDifficulty(templateExercise.getDifficulty());
        newExercise.setGradingInstructions(templateExercise.getGradingInstructions());
        if (Hibernate.isInitialized(templateExercise.getGradingCriteria())) {
            newExercise.setGradingCriteria(templateExercise.copyGradingCriteria(gradingInstructionCopyTracker));
        }

        // Competency links are not copied cross-course (the competencies may not exist in the target course).
        // Callers that need them handle them separately via competencyExerciseLinkService.
        newExercise.setCompetencyLinks(new HashSet<>());

        if (Hibernate.isPropertyInitialized(templateExercise, "plagiarismDetectionConfig") && templateExercise.getPlagiarismDetectionConfig() != null) {
            newExercise.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig(templateExercise.getPlagiarismDetectionConfig()));
        }

        if (newExercise.getExerciseGroup() != null) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
        }
        else {
            if (Hibernate.isInitialized(templateExercise.getCategories())) {
                newExercise.setCategories(templateExercise.getCategories());
            }
            newExercise.setMode(templateExercise.getMode());
            if (newExercise.getMode() == ExerciseMode.TEAM && Hibernate.isPropertyInitialized(templateExercise, "teamAssignmentConfig")
                    && templateExercise.getTeamAssignmentConfig() != null) {
                newExercise.setTeamAssignmentConfig(templateExercise.getTeamAssignmentConfig().copyTeamAssignmentConfig());
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
        newResult.setExerciseId(originalResult.getExerciseId());
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
    private List<Feedback> copyFeedback(Collection<Feedback> originalFeedbacks, Result newResult, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
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
