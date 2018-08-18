package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
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
    private final JsonModelRepository jsonModelRepository;
    private final CompassService compassService;
    private final ParticipationService participationService;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository,
                                     ResultRepository resultRepository,
                                     JsonModelRepository jsonModelRepository,
                                     CompassService compassService,
                                     ParticipationService participationService) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.jsonModelRepository = jsonModelRepository;
        this.compassService = compassService;
        this.participationService = participationService;
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
     * @return the result entity
     */
    @Transactional(rollbackFor = Exception.class)
    public Result save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, Participation participation) {
        User user = participation.getStudent();
        if (modelingSubmission.getId() != null) {
            // check if the the submission was already submitted due to automatic submission
            // and return the corresponding result if that's true
            ModelingSubmission dbModelingSubmission = modelingSubmissionRepository.findOne(modelingSubmission.getId());
            if (dbModelingSubmission.isSubmitted()) {
                try {
                    JsonObject model = getModel(participation.getExercise().getId(), user.getId(), dbModelingSubmission.getId());
                    dbModelingSubmission.setModel(model.toString());
                    Optional<Result> dbResult = resultRepository.findDistinctBySubmissionId(dbModelingSubmission.getId());
                    if (dbResult.isPresent()) {
                        // return the result found for the submitted submission incl. the model
                        Result resultForAutoSubmittedSubmission = dbResult.get();
                        resultForAutoSubmittedSubmission.setSubmission(dbModelingSubmission);
                        return resultForAutoSubmittedSubmission;
                    }
                } catch (Exception e) {
                    log.error("Exception while retrieving the model for submission {}:\n{}", dbModelingSubmission.getId(), e.getMessage());
                }
            }
        }

        Optional<Result> optionalResult = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), false);
        Result result;
        if (!optionalResult.isPresent()) {
            // there is no unrated result for the participation
            try {
                // create new result
                resultRepository.insertIfNonExisting(participation.getId());
                Optional<Result> newResult = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), false);
                if (newResult.isPresent()) {
                    // the insert of the new result was successful
                    // initialize the attributes of the result
                    result = initializeResult(participation, newResult.get());
                } else {
                    result = initializeResult(participation, null);
                }
            } catch (Exception e) {
                throw new ConflictException("Conflict exception", "Tried to call createModelingSubmission() more than once for the same participation");
            }
        } else {
            // an unrated result was found for the participation
            result = optionalResult.get();
        }

        if (result.getId() == null) {
            // there is no existing result and new result could not be created
            return null;
        }

        // update submission properties
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setType(SubmissionType.MANUAL);
        modelingSubmission.setParticipation(participation);

        result.setSubmission(modelingSubmission);
        modelingSubmission.setResult(result);
        modelingSubmissionRepository.save(modelingSubmission);
        resultRepository.save(result);

        if (modelingSubmission.getModel() != null && !modelingSubmission.getModel().isEmpty()) {
            jsonModelRepository.writeModel(modelingExercise.getId(), user.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
        }

        if (modelingSubmission.isSubmitted()) {
            notifyCompass(modelingSubmission, modelingExercise);
        } else if (modelingExercise.getDueDate() != null && !modelingExercise.isEnded()) {
            // save submission to HashMap if exercise not ended yet
            AutomaticSubmissionService.updateSubmission(modelingExercise.getId(), user.getLogin(), modelingSubmission);
        }

        return result;
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
     * Initialize the attributes rated, successful and completionDate for a result or create a new one.
     *
     * @param participation     the participation the result should belong to
     * @param result            null if new result otherwise the result for which to set the initial attributes
     * @return the result with initialized attributes
     */
    private Result initializeResult(Participation participation, Result result) {
        if (result == null) {
            result = new Result().participation(participation);
        }
        result.setRated(false);
        result.setSuccessful(false);
        result.setCompletionDate(ZonedDateTime.now());
        return result;
    }

    /**
     * Find the latest modeling submission by a given participation. First, it tries to retrieve the modeling submission
     * from the participation's submissions. Then it looks for the submission through the participation's results.
     *
     * @param participation    the participation for which to find the modelingSubmission
     * @return the modelingSubmission if found otherwise null
     */
    public ModelingSubmission findLatestModelingSubmissionByParticipation(Participation participation) {
        ModelingSubmission modelingSubmission = null;
        Submission submission = participationService.findLatestSubmission(participation);
        if (submission != null && submission instanceof ModelingSubmission) {
            modelingSubmission = (ModelingSubmission) submission;
        }

        /**
         * This is needed as a backup to find the modeling submission through the participation's results
         * because in the past submissions weren't saved to the participation. Therefore some submissions
         * do not have a reference to their participation and vice versa.
         */
        if (modelingSubmission == null) {
            Result result = participationService.findLatestResult(participation);
            if (result != null && result.getSubmission() != null) {
                if (result.getSubmission() instanceof HibernateProxy) {
                    modelingSubmission = (ModelingSubmission) Hibernate.unproxy(result.getSubmission());
                } else if (result.getSubmission() instanceof ModelingSubmission) {
                    modelingSubmission = (ModelingSubmission) result.getSubmission();
                }
            }
        }
        return modelingSubmission;
    }

    /**
     * Checks whether the given modelingSubmission has a model or not and tries to read and set it.
     *
     * @param modelingSubmission    the modeling submission for which to get and set the model
     * @return the modelingSubmission with the model or null if error occurred while getting model or participation is null
     */
    public ModelingSubmission getAndSetModel(ModelingSubmission modelingSubmission) {
        if (modelingSubmission.getModel() == null || modelingSubmission.getModel() == "") {
            Participation participation = modelingSubmission.getParticipation();
            if (participation == null) {
                log.error("The modeling submission {} does not have a participation.", modelingSubmission);
                return null;
            }
            Exercise exercise = participation.getExercise();
            try {
                JsonObject model = getModel(exercise.getId(), participation.getStudent().getId(), modelingSubmission.getId());
                modelingSubmission.setModel(model.toString());
            } catch (Exception e) {
                log.error("Exception while retrieving the model for modeling submission {}:\n{}", modelingSubmission.getId(), e.getMessage());
                return null;
            }
        }
        return modelingSubmission;
    }
}
