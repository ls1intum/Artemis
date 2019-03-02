package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@Transactional
public class ModelingSubmissionService {
    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final ResultRepository resultRepository;
    private final CompassService compassService;
    private final ParticipationRepository participationRepository;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository,
                                     ResultRepository resultRepository,
                                     CompassService compassService,
                                     ParticipationRepository participationRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.compassService = compassService;
        this.participationRepository = participationRepository;
    }

    /**
     * Saves the given submission and the corresponding model and creates the result if necessary.
     * Furthermore, the submission is added to the AutomaticSubmissionService if not submitted yet.
     * Is used for creating and updating modeling submissions.
     * If it is used for a submit action, Compass is notified about the new model.
     * Rolls back if inserting fails - occurs for concurrent createModelingSubmission() calls.
     *
     * @param modelingSubmission the submission to notifyCompass
     * @param modelingExercise the exercise to notifyCompass in
     * @param participation the participation where the result should be saved
     * @return the modelingSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelingSubmission save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, Participation participation) {

        // update submission properties
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setType(SubmissionType.MANUAL);
        modelingSubmission.setParticipation(participation);
        modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

        participation.addSubmissions(modelingSubmission);

        if (modelingSubmission.isSubmitted()) {
            notifyCompass(modelingSubmission, modelingExercise);
            checkAutomaticResult(modelingSubmission);
            participation.setInitializationState(InitializationState.FINISHED);
        } else if (modelingExercise.getDueDate() != null && !modelingExercise.isEnded()) {
            // save submission to HashMap if exercise not ended yet
            User user = participation.getStudent();
            AutomaticSubmissionService.updateSubmission(modelingExercise.getId(), user.getLogin(), modelingSubmission);
        }
        Participation savedParticipation = participationRepository.save(participation);
        if (modelingSubmission.getId() == null) {
            modelingSubmission = savedParticipation.findLatestModelingSubmission();
        }

        log.debug("return model: " + modelingSubmission.getModel());
        return modelingSubmission;
    }

    /**
     * Adds a model to compass service to include it in the automatic grading process.
     *
     * @param modelingSubmission    the submission which contains the model
     * @param modelingExercise      the exercise the submission belongs to
     */
    public void notifyCompass(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        this.compassService.addModel(modelingExercise.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
    }

    public ModelingSubmission findOne (Long id){
        return  modelingSubmissionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Exercise with id: \"" + id + "\" does not exist"));
    }

    /**
     * //TODO I do not really understand this method. We should probably refactor it
     *
     * Check if automatic assessment is available and set the result if found.
     *
     * @param modelingSubmission    the modeling submission, which contains the model and the submission status
     */
    public void checkAutomaticResult(ModelingSubmission modelingSubmission) {
        Participation participation = modelingSubmission.getParticipation();
        // create empty result in case submission couldn't be assessed automatically
        //TODO: we need to check if an automatic Assessment is available, I guess we need to look into the result?
        if (modelingSubmission.getResult() == null && automaticAssessmentAvailable) {
            //use the automatic result if available
            Optional<Result> optionalAutomaticResult = resultRepository.findDistinctBySubmissionId(modelingSubmission.getId());
            if (optionalAutomaticResult.isPresent()) {
                Result result = optionalAutomaticResult.get();
                result.submission(modelingSubmission).participation(participation);
                modelingSubmission.setResult(result);
                participation.addResult(modelingSubmission.getResult());
                resultRepository.save(result);
                modelingSubmissionRepository.save(modelingSubmission);
            }
        }
    }
}
