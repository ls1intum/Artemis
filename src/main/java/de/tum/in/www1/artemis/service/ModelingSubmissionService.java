package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@Transactional
public class ModelingSubmissionService {
    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final ResultRepository resultRepository;
    private final JsonModelRepository jsonModelRepository;
    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final CompassService compassService;
    private final ParticipationService participationService;
    private final ParticipationRepository participationRepository;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository,
                                     ResultRepository resultRepository,
                                     JsonModelRepository jsonModelRepository,
                                     JsonAssessmentRepository jsonAssessmentRepository,
                                     CompassService compassService,
                                     ParticipationService participationService,
                                     ParticipationRepository participationRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.jsonModelRepository = jsonModelRepository;
        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.compassService = compassService;
        this.participationService = participationService;
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
        String model = modelingSubmission.getModel();
        // update submission properties
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setType(SubmissionType.MANUAL);
        modelingSubmission.setParticipation(participation);
        modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

        participation.addSubmissions(modelingSubmission);

        User user = participation.getStudent();

        if (modelingSubmission.isSubmitted()) {
            notifyCompass(modelingSubmission, modelingExercise);
            checkAutomaticResult(modelingSubmission);
            participation.setInitializationState(ParticipationState.FINISHED);
        } else if (modelingExercise.getDueDate() != null && !modelingExercise.isEnded()) {
            // save submission to HashMap if exercise not ended yet
            AutomaticSubmissionService.updateSubmission(modelingExercise.getId(), user.getLogin(), modelingSubmission);
        }
        Participation savedParticipation = participationRepository.save(participation);
        if (modelingSubmission.getId() == null) {
            modelingSubmission = savedParticipation.findLatestModelingSubmission();
        }

        if (model != null && !model.isEmpty()) {
            jsonModelRepository.writeModel(modelingExercise.getId(), user.getId(), modelingSubmission.getId(), model);
        }

        return modelingSubmission;
    }

    /**
     * Adds a model to compass service to include it in the automatic grading process.
     *
     * @param modelingSubmission    the submission which contains the model
     * @param modelingExercise      the exercise the submission belongs to
     */
    public void notifyCompass(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        modelingSubmission = getAndSetModel(modelingSubmission);
        this.compassService.addModel(modelingExercise.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
    }

    /**
     * Checks if zhe model for given exerciseId, studentId and model modelId exists and returns it if found.
     *
     * @param exerciseId    the exercise modelId for which to find the model
     * @param studentId     the student modelId for which to find the model
     * @param modelId       the model modelId which corresponds to the submission id
     * @return the model JsonObject if found otherwise null
     */
    public JsonObject getModel(long exerciseId, long studentId, long modelId) {
        if (jsonModelRepository.exists(exerciseId, studentId, modelId)) {
            return jsonModelRepository.readModel(exerciseId, studentId, modelId);
        }
        return null;
    }

    /**
     * Checks whether the given modelingSubmission has a model or not and tries to read and set it.
     *
     * @param modelingSubmission    the modeling submission for which to get and set the model
     * @return the modelingSubmission with the model if the model could be read
     */
    public ModelingSubmission getAndSetModel(ModelingSubmission modelingSubmission) {
        if (modelingSubmission.getModel() == null || modelingSubmission.getModel() == "") {
            Participation participation = modelingSubmission.getParticipation();
            if (participation == null) {
                log.error("The modeling submission {} does not have a participation.", modelingSubmission);
            }
            Exercise exercise = participation.getExercise();
            try {
                JsonObject model = getModel(exercise.getId(), participation.getStudent().getId(), modelingSubmission.getId());
                modelingSubmission.setModel(model.toString());
            } catch (Exception e) {
                log.error("Exception while retrieving the model for modeling submission {}:\n{}", modelingSubmission.getId(), e.getMessage());
            }
        }
        return modelingSubmission;
    }

    /**
     * Check if automatic assessment is available and set the result if found.
     *
     * @param modelingSubmission    the modeling submission, which contains the model and the submission status
     * @return the modelingSubmission with the result if applicable
     */
    public ModelingSubmission checkAutomaticResult(ModelingSubmission modelingSubmission) {
        Participation participation = modelingSubmission.getParticipation();
        if (modelingSubmission.getResult() == null && jsonAssessmentRepository.exists(participation.getExercise().getId(), participation.getStudent().getId(), modelingSubmission.getId(), false)) {
            Result result = resultRepository.findDistinctBySubmissionId(modelingSubmission.getId()).orElse(null);
            modelingSubmission.setResult(result);
        } else {
            // create empty result if submission couldn't be assessed automatically
            // the result is needed for manual assessment
            Result result = new Result().submission(modelingSubmission).participation(participation);
            modelingSubmission.setResult(result);
        }
        return modelingSubmission;
    }
}
