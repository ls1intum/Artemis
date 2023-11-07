package de.tum.in.www1.artemis.service;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;

@Service
public class MathExerciseImportService extends ExerciseImportService {

    private final Logger log = LoggerFactory.getLogger(MathExerciseImportService.class);

    private final MathExerciseRepository mathExerciseRepository;

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ChannelService channelService;

    public MathExerciseImportService(MathExerciseRepository mathExerciseRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, MathSubmissionRepository mathSubmissionRepository, ChannelService channelService,
            FeedbackService feedbackService) {
        super(exampleSubmissionRepository, submissionRepository, resultRepository, feedbackService);
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.channelService = channelService;
    }

    /**
     * Imports a math exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyExerciseBase(MathExercise, Map)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(Exercise, Exercise, Map)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NotNull
    public MathExercise importMathExercise(final MathExercise templateExercise, MathExercise importedExercise) {
        log.debug("Creating a new Exercise based on exercise {}", templateExercise);
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        MathExercise newExercise = copyExerciseBase(importedExercise, gradingInstructionCopyTracker);
        disableFeedbackSuggestionsForExamExercises(newExercise);

        newExercise = mathExerciseRepository.save(newExercise);

        channelService.createExerciseChannel(newExercise, Optional.ofNullable(importedExercise.getChannelName()));
        newExercise.setExampleSubmissions(copyExampleSubmission(templateExercise, newExercise, gradingInstructionCopyTracker));
        return newExercise;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     *
     * @param importedExercise              The exercise from which to copy the basis
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return the cloned MathExercise basis
     */
    @NotNull
    private MathExercise copyExerciseBase(MathExercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        MathExercise newExercise = new MathExercise();

        super.copyExerciseBasis(newExercise, importedExercise, gradingInstructionCopyTracker);
        newExercise.setExampleSolution(importedExercise.getExampleSolution());
        return newExercise;
    }

    /**
     * Disable feedback suggestions on exam exercises (currently not supported)
     *
     * @param exercise the exercise to disable feedback suggestions for
     */
    private void disableFeedbackSuggestionsForExamExercises(MathExercise exercise) {
        if (exercise.isExamExercise()) {
            exercise.disableFeedbackSuggestions();
        }
    }

    /**
     * This functions does a hard copy of the example submissions contained in {@code templateExercise}.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission, Map)}}
     *
     * @param templateExercise              {MathExercise} The original exercise from which to fetch the example submissions
     * @param newExercise                   The new exercise in which we will insert the example submissions
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return The cloned set of example submissions
     */
    Set<ExampleSubmission> copyExampleSubmission(Exercise templateExercise, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the ExampleSubmissions to new Exercise: {}", newExercise);
        Set<ExampleSubmission> newExampleSubmissions = new HashSet<>();

        for (ExampleSubmission originalExampleSubmission : templateExercise.getExampleSubmissions()) {
            MathSubmission originalSubmission = (MathSubmission) originalExampleSubmission.getSubmission();
            MathSubmission newSubmission = copySubmission(originalSubmission, gradingInstructionCopyTracker);

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
     * This helper function does a hard copy of the {@code originalSubmission} and stores the values in {@code newSubmission}.
     * To copy the submission results this function calls {@link ExerciseImportService#copyExampleResult(Result, Submission, Map)}
     * respectively.
     *
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @param originalSubmission            The original submission to be copied.
     * @return The cloned submission
     */
    MathSubmission copySubmission(final Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        MathSubmission newSubmission = new MathSubmission();

        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setLanguage(((MathSubmission) originalSubmission).getLanguage());
            newSubmission.setType(originalSubmission.getType());
            newSubmission.setParticipation(originalSubmission.getParticipation());
            newSubmission.setText(((MathSubmission) originalSubmission).getText());
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission, gradingInstructionCopyTracker));
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            newSubmission = mathSubmissionRepository.findByIdWithEagerResultsAndFeedbackElseThrow(newSubmission.getId());
        }

        return newSubmission;
    }
}
