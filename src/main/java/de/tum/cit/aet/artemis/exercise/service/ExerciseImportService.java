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
    }

    /**
     * Copies exercise fields from two sources: structural context from {@code importedExercise} (target course/group)
     * and content that falls back to {@code templateExercise} (the original exercise) when {@code importedExercise} does
     * not carry it.
     * <p>
     * The two arguments are the same object for the standalone REST import, where {@code importedExercise} is the full
     * exercise submitted from the edit form (edited content, cleared dates). They differ for exam-import and
     * course-material-import, where {@code importedExercise} is a skeleton carrying only the destination and a few
     * overrides, and the content lives in {@code templateExercise}. Reading each field from {@code importedExercise}
     * first and only falling back to {@code templateExercise} keeps both cases correct: the standalone import honours
     * the submitted values, and the bulk imports pick up the source content the skeleton is missing.
     *
     * @param newExercise                   the fresh entity being built
     * @param importedExercise              the intended exercise (full for standalone import, a destination skeleton for bulk import)
     * @param templateExercise              the original exercise providing content the skeleton is missing
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

        // Scalar content: prefer importedExercise (honours edits from the standalone import form), fall back to the
        // template when the skeleton does not carry the value (exam / course-material import).
        newExercise.setTitle(firstNonNull(importedExercise.getTitle(), templateExercise.getTitle()));
        newExercise.setMaxPoints(firstNonNull(importedExercise.getMaxPoints(), templateExercise.getMaxPoints()));
        newExercise.setBonusPoints(firstNonNull(importedExercise.getBonusPoints(), templateExercise.getBonusPoints()));
        newExercise.setAssessmentType(firstNonNull(importedExercise.getAssessmentType(), templateExercise.getAssessmentType()));
        newExercise.setProblemStatement(firstNonNull(importedExercise.getProblemStatement(), templateExercise.getProblemStatement()));
        newExercise.setDifficulty(firstNonNull(importedExercise.getDifficulty(), templateExercise.getDifficulty()));
        newExercise.setGradingInstructions(firstNonNull(importedExercise.getGradingInstructions(), templateExercise.getGradingInstructions()));
        // includedInOverallScore and mode (below) have non-null defaults, so the skeleton cannot be distinguished from
        // an intentional value; both are editable in the standalone import form, so they are taken from importedExercise.
        // The course-material import copies them from the source onto its skeleton (see CourseMaterialImportService).
        newExercise.setIncludedInOverallScore(importedExercise.getIncludedInOverallScore());

        // Dates are reset on import: the standalone import clears them client-side and the bulk skeletons have none.
        // Read them from importedExercise so an imported exercise starts fresh instead of inheriting the source dates.
        newExercise.setStartDate(importedExercise.getStartDate());
        newExercise.setReleaseDate(importedExercise.getReleaseDate());
        newExercise.setDueDate(importedExercise.getDueDate());
        newExercise.setAssessmentDueDate(importedExercise.getAssessmentDueDate());
        newExercise.setExampleSolutionPublicationDate(null); // This should not be imported as the client might serve the original date as the default.
        newExercise.validateDates();

        Exercise gradingCriteriaSource = hasInitializedGradingCriteria(importedExercise) ? importedExercise : templateExercise;
        if (hasInitializedGradingCriteria(gradingCriteriaSource)) {
            newExercise.setGradingCriteria(gradingCriteriaSource.copyGradingCriteria(gradingInstructionCopyTracker));
        }

        // Competency links point at course-specific competencies, so they can only come from the target-context
        // importedExercise (the standalone import form or a bulk skeleton), never from the foreign template source.
        Set<CompetencyExerciseLink> copiedLinks = new HashSet<>();
        for (CompetencyExerciseLink link : importedExercise.getCompetencyLinks()) {
            copiedLinks.add(new CompetencyExerciseLink(link.getCompetency(), newExercise, link.getWeight()));
        }
        newExercise.setCompetencyLinks(copiedLinks);

        Exercise plagiarismSource = hasPlagiarismDetectionConfig(importedExercise) ? importedExercise : templateExercise;
        if (hasPlagiarismDetectionConfig(plagiarismSource)) {
            newExercise.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig(plagiarismSource.getPlagiarismDetectionConfig()));
        }

        if (newExercise.getExerciseGroup() != null) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
        }
        else {
            Exercise categoriesSource = hasInitializedCategories(importedExercise) && !importedExercise.getCategories().isEmpty() ? importedExercise : templateExercise;
            if (hasInitializedCategories(categoriesSource)) {
                newExercise.setCategories(new HashSet<>(categoriesSource.getCategories()));
            }
            newExercise.setMode(importedExercise.getMode());
            Exercise teamConfigSource = hasTeamAssignmentConfig(importedExercise) ? importedExercise : templateExercise;
            if (newExercise.getMode() == ExerciseMode.TEAM && hasTeamAssignmentConfig(teamConfigSource)) {
                newExercise.setTeamAssignmentConfig(teamConfigSource.getTeamAssignmentConfig().copyTeamAssignmentConfig());
            }
        }
    }

    /**
     * Returns {@code value} if it is non-null, otherwise {@code fallback}. Used by import services to prefer the
     * intended exercise's field and fall back to the source content a bulk-import skeleton is missing.
     */
    protected static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static boolean hasInitializedGradingCriteria(Exercise exercise) {
        return Hibernate.isInitialized(exercise.getGradingCriteria()) && exercise.getGradingCriteria() != null;
    }

    private static boolean hasInitializedCategories(Exercise exercise) {
        return Hibernate.isInitialized(exercise.getCategories()) && exercise.getCategories() != null;
    }

    private static boolean hasPlagiarismDetectionConfig(Exercise exercise) {
        return Hibernate.isPropertyInitialized(exercise, "plagiarismDetectionConfig") && exercise.getPlagiarismDetectionConfig() != null;
    }

    private static boolean hasTeamAssignmentConfig(Exercise exercise) {
        return Hibernate.isPropertyInitialized(exercise, "teamAssignmentConfig") && exercise.getTeamAssignmentConfig() != null;
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
