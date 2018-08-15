package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
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
     * Rolls back if inserting fails - occurs for concurrent createModelingSubmission() calls.
     *
     * @param modelingSubmission the submission to submit
     * @param modelingExercise the exercise to submit in
     * @param participation the participation where the result should be saved
     * @return the result entity
     */
    @Transactional(rollbackFor = Exception.class)
    public Result save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, Participation participation) {
        User user = participation.getStudent();
        if (modelingSubmission.getId() != null) {
            // check if the the submission was already submitted due to auto submit
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

        result.setSubmission(modelingSubmission);
        modelingSubmission.setResult(result);
        modelingSubmissionRepository.save(modelingSubmission);
        resultRepository.save(result);

        if (modelingSubmission.getModel() != null && !modelingSubmission.getModel().isEmpty()) {
            jsonModelRepository.writeModel(modelingExercise.getId(), user.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
        }

        if (modelingSubmission.isSubmitted()) {
            submit(modelingSubmission, modelingExercise);
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
     * @param modelingExercise      the exercise the model belongs to
     */
    public void submit(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        this.compassService.addModel(modelingExercise.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
    }

    /**
     * Find the model for given exerciseId, studentId and model id, which is the same as submission id.
     *
     * @param exerciseId    the exercise id for which to find the model
     * @param studentId     the student id for which to find the model
     * @param id            the model id
     * @return the model JsonObject if found otherwise null
     */
    public JsonObject getModel(long exerciseId, long studentId, long id) {
        if (jsonModelRepository.exists(exerciseId, studentId, id)) {
            return jsonModelRepository.readModel(exerciseId, studentId, id);
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
     * Find the modelingSubmission by a given participation.
     *
     * @param participation    the participation for which to find the modelingSubmission
     * @return the modelingSubmission if found otherwise null
     */
    public ModelingSubmission findByParticipation(Participation participation) {
        ModelingSubmission modelingSubmission;
        Optional<Result> optionalResult = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId());
        Result result;
        if (optionalResult.isPresent()) {
            result = optionalResult.get();
            modelingSubmission = modelingSubmissionRepository.findOne(result.getSubmission().getId());
            return modelingSubmission;
        }
        return null;
    }
}
