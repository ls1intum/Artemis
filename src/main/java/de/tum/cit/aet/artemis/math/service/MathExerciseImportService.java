package de.tum.cit.aet.artemis.math.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

@Conditional(MathEnabled.class)
@Lazy
@Service
public class MathExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(MathExerciseImportService.class);

    private final MathExerciseRepository mathExerciseRepository;

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ChannelService channelService;

    public MathExerciseImportService(MathExerciseRepository mathExerciseRepository, MathSubmissionRepository mathSubmissionRepository,
            ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository, ChannelService channelService,
            FeedbackService feedbackService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.channelService = channelService;
    }

    /**
     * Imports a math exercise creating a new entity, copying all basic values and saving it in the database.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NonNull
    public MathExercise importMathExercise(final MathExercise templateExercise, MathExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        MathExercise newExercise = copyMathExerciseBasis(importedExercise, gradingInstructionCopyTracker);

        MathExercise savedExercise = mathExerciseRepository.save(newExercise);

        channelService.createExerciseChannel(savedExercise, Optional.ofNullable(importedExercise.getChannelName()));
        savedExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, savedExercise, gradingInstructionCopyTracker));

        return savedExercise;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     *
     * @param importedExercise              The exercise from which to copy the basis
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return the cloned MathExercise basis
     */
    @NonNull
    private MathExercise copyMathExerciseBasis(MathExercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        MathExercise newExercise = new MathExercise();

        super.copyExerciseBasis(newExercise, importedExercise, gradingInstructionCopyTracker);
        newExercise.setDescription(importedExercise.getDescription());
        newExercise.setExampleSolution(importedExercise.getExampleSolution());
        newExercise.setManualDerivation(importedExercise.isManualDerivation());
        return newExercise;
    }

    /**
     * This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding {@link de.tum.cit.aet.artemis.exercise.domain.Submission} entity this calls {@link #copySubmission(MathSubmission, Map)}.
     *
     * @param templateExercise              The original exercise from which to fetch the example submissions
     * @param newExercise                   The new exercise in which we will insert the example submissions
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned set of example submissions
     */
    private Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        if (!Hibernate.isInitialized(templateExercise.getExampleSubmissions())) {
            return newExampleSubmissions;
        }
        // Preload every example submission's assessment graph (result + feedbacks + assessor) in a single bulk query,
        // so copySubmission does not issue one query per example submission (N+1).
        Map<Long, MathSubmission> submissionsWithAssessmentById = loadSubmissionsWithAssessment(templateExercise.getExampleSubmissions());
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            Submission originalSubmission = originalExampleSubmission.getSubmission();
            // Hard-copy the submission: ExampleSubmission.submission is a unique @OneToOne with cascade=REMOVE/orphanRemoval,
            // so the new example submission must own its own submission rather than share the template's.
            MathSubmission source = originalSubmission == null ? null : submissionsWithAssessmentById.get(originalSubmission.getId());
            MathSubmission newSubmission = copySubmission(source, gradingInstructionCopyTracker);

            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(newSubmission);
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }

    /**
     * Bulk-loads the assessment graph (results + feedbacks + assessor) for every submission behind the given example submissions,
     * keyed by submission id. Loading them all at once avoids issuing one query per example submission in {@link #copySubmission}.
     *
     * @param exampleSubmissions the template's example submissions
     * @return a map from submission id to the submission with its assessment graph eagerly loaded (empty if there are none)
     */
    private Map<Long, MathSubmission> loadSubmissionsWithAssessment(Set<ExampleSubmission> exampleSubmissions) {
        Set<Long> submissionIds = exampleSubmissions.stream().map(ExampleSubmission::getSubmission).filter(Objects::nonNull).map(Submission::getId).collect(Collectors.toSet());
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MathSubmission> submissionsById = new HashMap<>();
        mathSubmissionRepository.findAllWithResultsAndFeedbacksAndAssessorByIdIn(submissionIds).forEach(submission -> submissionsById.put(submission.getId(), submission));
        return submissionsById;
    }

    /**
     * This helper function does a hard copy of the {@code originalSubmission} into a new {@link MathSubmission}, including its
     * latest example result. Mirrors {@code TextExerciseImportService#copySubmission} but without text blocks.
     *
     * @param originalSubmission            The original submission to be copied, with its assessment graph preloaded (may be {@code null})
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned submission
     */
    private MathSubmission copySubmission(final MathSubmission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        MathSubmission newSubmission = new MathSubmission();
        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setType(originalSubmission.getType());
            // Intentionally not copying the participation: example submissions are standalone teaching artifacts and must
            // not inherit the source exercise's participation (see ExampleSubmissionService#importStudentSubmissionAsExampleSubmission).
            newSubmission.setContent(originalSubmission.getContent());
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            Result originalResult = originalSubmission.getLatestResult();
            if (originalResult != null) {
                newSubmission.addResult(copyExampleResult(originalResult, newSubmission, gradingInstructionCopyTracker));
                newSubmission = submissionRepository.saveAndFlush(newSubmission);
            }
        }
        return newSubmission;
    }
}
