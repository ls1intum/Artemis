package de.tum.in.www1.artemis.service;

import java.util.*;

import org.slf4j.*;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ModelingSubmissionService extends SubmissionService<ModelingSubmission, ModelingSubmissionRepository> {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final CompassService compassService;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, SubmissionRepository submissionRepository, ResultService resultService,
            ResultRepository resultRepository, CompassService compassService, ParticipationService participationService, UserService userService,
            StudentParticipationRepository studentParticipationRepository, SimpMessageSendingOperations messagingTemplate, AuthorizationCheckService authCheckService) {
        super(submissionRepository, userService, authCheckService, resultRepository, participationService, messagingTemplate, studentParticipationRepository,
                modelingSubmissionRepository, resultService);
        this.compassService = compassService;
    }

    /**
     * Get the modeling submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it. Additionally, check if the
     * submission lock limit has been reached.
     *
     * @param submissionId     the id of the modeling submission
     * @param modelingExercise the corresponding exercise
     * @return the locked modeling submission
     */
    @Transactional
    public ModelingSubmission getLockedModelingSubmission(Long submissionId, ModelingExercise modelingExercise) {
        ModelingSubmission modelingSubmission = findOneWithEagerResultAndFeedbackAndAssessorAndParticipationResults(submissionId);

        if (modelingSubmission.getResult() == null || modelingSubmission.getResult().getAssessor() == null) {
            checkSubmissionLockLimit(modelingExercise.getCourse().getId());
            modelingSubmission = assignAutomaticResultToSubmission(modelingSubmission);
        }

        lockModelingSubmission(modelingSubmission, modelingExercise);
        return modelingSubmission;
    }

    /**
     * Get a modeling submission of the given exercise that still needs to be assessed and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param modelingExercise the exercise the submission should belong to
     * @return a locked modeling submission that needs an assessment
     */
    @Transactional
    public ModelingSubmission getLockedModelingSubmissionWithoutResult(ModelingExercise modelingExercise) {
        ModelingSubmission modelingSubmission = getModelingSubmissionWithoutManualResult(modelingExercise)
                .orElseThrow(() -> new EntityNotFoundException("Modeling submission for exercise " + modelingExercise.getId() + " could not be found"));
        modelingSubmission = assignAutomaticResultToSubmission(modelingSubmission);
        lockModelingSubmission(modelingSubmission, modelingExercise);
        return modelingSubmission;
    }

    /**
     * Given an exercise, find a modeling submission for that exercise which still doesn't have a manual result. If the diagram type is supported by Compass we get the next optimal
     * submission from Compass, i.e. the submission for which an assessment means the most knowledge gain for the automatic assessment mechanism. If it's not supported by Compass
     * we just get a random submission without assessment. If there is no submission without manual result we return an empty optional. Note, that we cannot use a readonly
     * transaction here as it is making problems when initially loading the calculation engine and assessing all submissions automatically: we would get an sql exception
     * "Connection is read-only" from hibernate when saving the result in CompassService#assessAutomatically.
     *
     * @param modelingExercise the modeling exercise for which we want to get a modeling submission without result
     * @return a modeling submission without any result
     */
    @Transactional
    public Optional<ModelingSubmission> getModelingSubmissionWithoutManualResult(ModelingExercise modelingExercise) {
        // if the diagram type is supported by Compass, ask Compass for optimal (i.e. most knowledge gain for automatic assessments) submissions to assess next
        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            List<Long> modelsWaitingForAssessment = compassService.getModelsWaitingForAssessment(modelingExercise.getId());

            // shuffle the model list to prevent that the user gets the same submission again after canceling an assessment
            Collections.shuffle(modelsWaitingForAssessment);

            for (Long submissionId : modelsWaitingForAssessment) {
                Optional<ModelingSubmission> submission = genericSubmissionRepository.findWithEagerResultAndFeedbackAndAssessorAndParticipationResultsById(submissionId);
                if (submission.isPresent()) {
                    return submission;
                }
                else {
                    compassService.removeModelWaitingForAssessment(modelingExercise.getId(), submissionId);
                }
            }
        }
        // otherwise return a random submission that is not manually assessed or an empty optional if there is none
        return getRandomUnassessedSubmission(modelingExercise, ModelingSubmission.class);
    }

    /**
     * Saves the given submission and the corresponding model and creates the result if necessary. This method used for creating and updating modeling submissions. If it is used
     * for a submit action, Compass is notified about the new model. Rolls back if inserting fails - occurs for concurrent createModelingSubmission() calls.
     *
     * @param modelingSubmission the submission that should be saved
     * @param modelingExercise   the exercise the submission belongs to
     * @param username           the name of the corresponding user
     * @return the saved modelingSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelingSubmission save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, String username) {
        modelingSubmission = save(modelingSubmission, modelingExercise, username, ModelingSubmission.class);
        if (modelingSubmission.isSubmitted()) {
            notifyCompass(modelingSubmission, modelingExercise);
        }

        log.debug("return model: " + modelingSubmission.getModel());
        return modelingSubmission;
    }

    /**
     * Soft lock the submission to prevent other tutors from receiving and assessing it. We remove the model from the models waiting for assessment in Compass to prevent other
     * tutors from retrieving it in the first place. Additionally, we set the assessor and save the result to soft lock the assessment in the client, i.e. the client will not allow
     * tutors to assess a model when an assessor is already assigned. If no result exists for this submission we create one first.
     *
     * @param modelingSubmission the submission to lock
     * @param modelingExercise   the exercise to which the submission belongs to (needed for Compass)
     */
    private void lockModelingSubmission(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        var result = super.lockSubmission(modelingSubmission);
        if (result.getAssessor() == null && compassService.isSupported(modelingExercise.getDiagramType())) {
            compassService.removeModelWaitingForAssessment(modelingExercise.getId(), modelingSubmission.getId());
        }
        log.debug("Assessment locked with result id: " + result.getId() + " for assessor: " + result.getAssessor().getFirstName());
    }

    /**
     * Assigns an automatic result generated by Compass to the given modeling submission and saves the updated submission to the database. If the given submission already contains
     * a manual result, it will not get updated with the automatic result.
     *
     * @param modelingSubmission the modeling submission that should be updated with an automatic result generated by Compass
     * @return the updated modeling submission
     */
    private ModelingSubmission assignAutomaticResultToSubmission(ModelingSubmission modelingSubmission) {
        Result existingResult = modelingSubmission.getResult();
        if (existingResult != null && existingResult.getAssessmentType() != null && existingResult.getAssessmentType().equals(AssessmentType.MANUAL)) {
            return modelingSubmission;
        }
        StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Result automaticResult = compassService.getAutomaticResultForSubmission(modelingSubmission.getId(), exerciseId);
        if (automaticResult != null) {
            automaticResult.setSubmission(modelingSubmission);
            modelingSubmission.setResult(automaticResult);
            modelingSubmission.getParticipation().addResult(automaticResult);
            modelingSubmission = genericSubmissionRepository.save(modelingSubmission);
            resultRepository.save(automaticResult);

            compassService.removeAutomaticResultForSubmission(modelingSubmission.getId(), exerciseId);
        }

        return modelingSubmission;
    }

    /**
     * Adds a model to compass service to include it in the automatic grading process.
     *
     * @param modelingSubmission the submission which contains the model
     * @param modelingExercise   the exercise the submission belongs to
     */
    public void notifyCompass(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            this.compassService.addModel(modelingExercise.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
        }
    }
}
