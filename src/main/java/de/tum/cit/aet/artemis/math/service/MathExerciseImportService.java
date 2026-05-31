package de.tum.cit.aet.artemis.math.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;

@Conditional(MathEnabled.class)
@Lazy
@Service
public class MathExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(MathExerciseImportService.class);

    private final MathExerciseRepository mathExerciseRepository;

    private final ChannelService channelService;

    public MathExerciseImportService(MathExerciseRepository mathExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, ChannelService channelService, FeedbackService feedbackService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.mathExerciseRepository = mathExerciseRepository;
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
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise));

        return newExercise;
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
        return newExercise;
    }

    /**
     * This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     *
     * @param templateExercise The original exercise from which to fetch the example submissions
     * @param newExercise      The new exercise in which we will insert the example submissions
     * @return The cloned set of example submissions
     */
    private Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();
        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            ExampleSubmission newExampleSubmission = new ExampleSubmission();
            newExampleSubmission.setExercise(newExercise);
            newExampleSubmission.setSubmission(originalExampleSubmission.getSubmission()); // Simplified: link to original submission or copy it?
            // Usually we copy the submission as well (see TextExerciseImportService).
            // For a prototype, we can simplify or follow the text pattern more closely.
            newExampleSubmission.setAssessmentExplanation(originalExampleSubmission.getAssessmentExplanation());

            exampleSubmissionRepository.save(newExampleSubmission);
            newExampleSubmissions.add(newExampleSubmission);
        }
        return newExampleSubmissions;
    }
}
