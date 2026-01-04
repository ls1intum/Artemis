package de.tum.cit.aet.artemis.modeling.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleParticipationRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseImportService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;

@Conditional(ModelingEnabled.class)
@Lazy
@Service
public class ModelingExerciseImportService extends ExerciseImportService {

    private static final Logger log = LoggerFactory.getLogger(ModelingExerciseImportService.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final ExerciseService exerciseService;

    public ModelingExerciseImportService(ModelingExerciseRepository modelingExerciseRepository, ExampleParticipationRepository exampleParticipationRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, ChannelService channelService, FeedbackService feedbackService,
            Optional<CompetencyProgressApi> competencyProgressApi, ExerciseService exerciseService) {
        super(exampleParticipationRepository, submissionRepository, resultRepository, feedbackService);
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
        this.exerciseService = exerciseService;
    }

    /**
     * Imports a modeling exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except Student-, Tutor participations, and student questions. <br>
     * This method calls {@link #copyModelingExerciseBasis(Exercise, Map)} to set up the basis of the exercise
     * {@link #copyExampleSubmission(Exercise, Exercise, Map)} for a hard copy of the example submissions.
     *
     * @param templateExercise The template exercise which should get imported
     * @param importedExercise The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @NonNull
    public ModelingExercise importModelingExercise(ModelingExercise templateExercise, ModelingExercise importedExercise) {
        // Get the template exercise ID from templateExercise (which always has it)
        // Note: importedExercise might not have an ID if called from REST API
        Long templateExerciseId = templateExercise.getId();
        log.debug("Creating a new Exercise based on exercise {}", templateExerciseId);
        Map<Long, GradingInstruction> gradingInstructionCopyTracker = new HashMap<>();
        ModelingExercise newExercise = copyModelingExerciseBasis(importedExercise, gradingInstructionCopyTracker);

        ModelingExercise newModelingExercise = modelingExerciseRepository.save(newExercise);

        channelService.createExerciseChannel(newModelingExercise, Optional.ofNullable(importedExercise.getChannelName()));
        copyExampleParticipations(templateExerciseId, newExercise, gradingInstructionCopyTracker);

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(newModelingExercise));

        return newModelingExercise;
    }

    /**
     * This helper method copies all attributes of the {@code importedExercise} into the new exercise.
     * Here we ignore all external entities as well as the start-, end-, and assessment due date.
     * Also fills {@code gradingInstructionCopyTracker}.
     *
     * @param importedExercise              The exercise from which to copy the basis
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @return the cloned TextExercise basis
     */
    @NonNull
    private ModelingExercise copyModelingExerciseBasis(Exercise importedExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the exercise basis from {}", importedExercise);
        ModelingExercise newExercise = new ModelingExercise();
        super.copyExerciseBasis(newExercise, importedExercise, gradingInstructionCopyTracker);

        newExercise.setDiagramType(((ModelingExercise) importedExercise).getDiagramType());
        newExercise.setExampleSolutionModel(((ModelingExercise) importedExercise).getExampleSolutionModel());
        newExercise.setExampleSolutionExplanation(((ModelingExercise) importedExercise).getExampleSolutionExplanation());
        return newExercise;
    }

    /**
     * This functions does a hard copy of the example participations contained in the template exercise.
     * To copy the corresponding Submission entity this function calls {@link #copySubmission(Submission, Map, ExampleParticipation)}
     *
     * @param templateExerciseId            The ID of the original exercise from which to fetch the example participations
     * @param newExercise                   The new exercise in which we will insert the example participations
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     */
    private void copyExampleParticipations(Long templateExerciseId, Exercise newExercise, Map<Long, GradingInstruction> gradingInstructionCopyTracker) {
        log.debug("Copying the ExampleParticipations to new Exercise: {}", newExercise);
        // Use the query that also fetches feedbacks for import
        Set<ExampleParticipation> templateExampleParticipations = exampleParticipationRepository.findAllWithSubmissionsResultsAndFeedbacksByExerciseId(templateExerciseId);
        for (ExampleParticipation originalExampleParticipation : templateExampleParticipations) {
            // First create and save the new ExampleParticipation to get an ID
            ExampleParticipation newExampleParticipation = new ExampleParticipation();
            newExampleParticipation.setExercise(newExercise);
            newExampleParticipation.setUsedForTutorial(originalExampleParticipation.isUsedForTutorial());
            newExampleParticipation.setAssessmentExplanation(originalExampleParticipation.getAssessmentExplanation());
            ExampleParticipation savedParticipation = exampleParticipationRepository.save(newExampleParticipation);

            // Now copy the submission with the correct participation
            ModelingSubmission originalSubmission = (ModelingSubmission) originalExampleParticipation.getSubmission();
            ModelingSubmission newSubmission = (ModelingSubmission) copySubmission(originalSubmission, gradingInstructionCopyTracker, savedParticipation);

            if (newSubmission != null) {
                savedParticipation.addSubmission(newSubmission);
            }
        }
    }

    /**
     * This helper function does a hard copy of the {@code originalSubmission} and stores the values in {@code newSubmission}.
     * To copy the submission results this function calls {@link ExerciseImportService#copyExampleResult(Result, Submission, Map)} respectively.
     *
     * @param originalSubmission            The original submission to be copied.
     * @param gradingInstructionCopyTracker The mapping from original GradingInstruction Ids to new GradingInstruction instances.
     * @param targetParticipation           The participation to associate with the new submission (required, cannot be null).
     * @return The cloned submission
     */
    public Submission copySubmission(Submission originalSubmission, Map<Long, GradingInstruction> gradingInstructionCopyTracker, ExampleParticipation targetParticipation) {
        ModelingSubmission newSubmission = new ModelingSubmission();
        if (originalSubmission != null) {
            log.debug("Copying the Submission to new ExampleSubmission: {}", newSubmission);
            newSubmission.setExampleSubmission(true);
            newSubmission.setSubmissionDate(originalSubmission.getSubmissionDate());
            newSubmission.setType(originalSubmission.getType());
            newSubmission.setParticipation(targetParticipation);
            newSubmission.setExplanationText(((ModelingSubmission) originalSubmission).getExplanationText());
            newSubmission.setModel(((ModelingSubmission) originalSubmission).getModel());

            newSubmission = submissionRepository.saveAndFlush(newSubmission);
            if (originalSubmission.getLatestResult() != null) {
                newSubmission.addResult(copyExampleResult(originalSubmission.getLatestResult(), newSubmission, gradingInstructionCopyTracker));
            }
            newSubmission = submissionRepository.saveAndFlush(newSubmission);
        }
        return newSubmission;
    }
}
