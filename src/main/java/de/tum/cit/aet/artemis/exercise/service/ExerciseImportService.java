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

    /**
     * Resets the transient state on a caller-supplied {@code newExercise} so it can be safely persisted as a
     * brand-new exercise. Clears the id and any participation/submission collections a request body might carry
     * (a standalone import binds a client-supplied entity). Content the caller intends to keep - problem statement,
     * grading criteria, categories, competency links, type-specific fields - is deliberately left untouched.
     *
     * @param newExercise the exercise being built; mutated in place
     */
    protected static void prepareNewExerciseForImport(final Exercise newExercise) {
        newExercise.setId(null);
        newExercise.setStudentParticipations(new HashSet<>());
        newExercise.setTutorParticipations(new HashSet<>());
        newExercise.setExampleSubmissions(new HashSet<>());
        newExercise.setAttachments(new HashSet<>());
        newExercise.setPlagiarismCases(new HashSet<>());
        // teams has orphanRemoval enabled; a client-supplied or source-derived entity may still reference the source's
        // teams, which would fail to persist under a new owner. An imported exercise starts without teams.
        newExercise.setTeams(new HashSet<>());
    }

    /**
     * Backfills the exercise basis onto {@code newExercise} from {@code sourceExercise}: any content field the caller
     * has not already set on {@code newExercise} is taken from the source exercise being imported.
     * <p>
     * The caller owns {@code newExercise} and must set its destination (course or exercise group) and any intended
     * overrides before calling. A standalone REST import passes the full edited exercise, so its values win and the
     * backfill is largely a no-op. A bulk import (exam / course-material) passes a destination skeleton, so nearly
     * every field is filled from {@code sourceExercise}. This single "keep the caller's value, else take the source's"
     * rule keeps both cases correct without a separate intended-vs-source object.
     *
     * @param newExercise                   the exercise being built; already carries the destination and any caller
     *                                          overrides, receives the source content for every field it does not define
     * @param sourceExercise                the source exercise providing the content to backfill
     * @param gradingInstructionCopyTracker tracker for deep-copying grading instructions
     */
    protected void copyExerciseBasis(final Exercise newExercise, final Exercise sourceExercise, final Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        // Scalar content: keep the caller's value where present (standalone edits), else take the source's.
        newExercise.setTitle(firstNonNull(newExercise.getTitle(), sourceExercise.getTitle()));
        newExercise.setMaxPoints(firstNonNull(newExercise.getMaxPoints(), sourceExercise.getMaxPoints()));
        newExercise.setBonusPoints(firstNonNull(newExercise.getBonusPoints(), sourceExercise.getBonusPoints()));
        newExercise.setAssessmentType(firstNonNull(newExercise.getAssessmentType(), sourceExercise.getAssessmentType()));
        newExercise.setProblemStatement(firstNonNull(newExercise.getProblemStatement(), sourceExercise.getProblemStatement()));
        newExercise.setDifficulty(firstNonNull(newExercise.getDifficulty(), sourceExercise.getDifficulty()));
        newExercise.setGradingInstructions(firstNonNull(newExercise.getGradingInstructions(), sourceExercise.getGradingInstructions()));
        // includedInOverallScore and mode have non-null defaults, so a bulk skeleton's default cannot be distinguished
        // from an intentional value; both stay as the caller set them on newExercise (the standalone form supplies them,
        // and the course-material import copies them from the source onto its skeleton - see CourseMaterialImportService).

        // Dates are reset on import: the standalone import clears them client-side and the bulk skeletons have none, so
        // whatever the caller left on newExercise is kept and an imported exercise starts fresh instead of inheriting
        // the source dates.
        newExercise.setExampleSolutionPublicationDate(null); // This should not be imported as the client might serve the original date as the default.
        newExercise.validateDates();

        // Grading criteria: prefer the caller's edited criteria if initialized, else deep-copy the source's.
        // Prefer the caller's edited criteria only if it actually carries some (a fresh bulk skeleton has an
        // initialized-but-empty set, which must not win over the source), else deep-copy the source's criteria.
        Exercise gradingCriteriaSource = hasInitializedGradingCriteria(newExercise) && !newExercise.getGradingCriteria().isEmpty() ? newExercise : sourceExercise;
        if (hasInitializedGradingCriteria(gradingCriteriaSource)) {
            newExercise.setGradingCriteria(gradingCriteriaSource.copyGradingCriteria(gradingInstructionCopyTracker));
        }

        // Competency links point at course-specific competencies, so they only come from newExercise (the standalone
        // edit form or a bulk skeleton), never from the foreign source. Rebuild them so they point at the new exercise.
        Set<CompetencyExerciseLink> copiedLinks = new HashSet<>();
        for (CompetencyExerciseLink link : newExercise.getCompetencyLinks()) {
            copiedLinks.add(new CompetencyExerciseLink(link.getCompetency(), newExercise, link.getWeight()));
        }
        newExercise.setCompetencyLinks(copiedLinks);

        Exercise plagiarismSource = hasPlagiarismDetectionConfig(newExercise) ? newExercise : sourceExercise;
        if (hasPlagiarismDetectionConfig(plagiarismSource)) {
            newExercise.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig(plagiarismSource.getPlagiarismDetectionConfig()));
        }

        if (newExercise.getExerciseGroup() != null) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
        }
        else {
            Exercise categoriesSource = hasInitializedCategories(newExercise) && !newExercise.getCategories().isEmpty() ? newExercise : sourceExercise;
            if (hasInitializedCategories(categoriesSource)) {
                newExercise.setCategories(new HashSet<>(categoriesSource.getCategories()));
            }
            Exercise teamConfigSource = hasTeamAssignmentConfig(newExercise) ? newExercise : sourceExercise;
            if (newExercise.getMode() == ExerciseMode.TEAM && hasTeamAssignmentConfig(teamConfigSource)) {
                newExercise.setTeamAssignmentConfig(teamConfigSource.getTeamAssignmentConfig().copyTeamAssignmentConfig());
            }
        }
    }

    /**
     * Returns {@code value} if it is non-null, otherwise {@code fallback}. Used by import services to keep the value
     * already set on the new exercise (a standalone import's edits) and fall back to the source content a bulk-import
     * skeleton is missing.
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
