package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.JsonModelRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@Transactional
public class ModelingSubmissionService {

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
     * Saves the given submission and the corresponding model
     * @param modelingSubmission the submission to submit
     * @param modelingExercise the exercise to submit in
     * @param participation the participation where the result should be saved
     * @return the result entity
     */
    @Transactional
    public Result save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, Participation participation) {
        // update submission properties
        modelingSubmission.setType(SubmissionType.MANUAL);

        Optional<Result> optionalResult = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId());
        Result result;
        if (!optionalResult.isPresent()) {
            // create and save result
            result = initializeResult(participation, modelingSubmission);
        } else {
            result = optionalResult.get();
        }

        result.setCompletionDate(ZonedDateTime.now());
        modelingSubmission.setSubmissionDate(result.getCompletionDate());

        if (modelingSubmission.getModel() != null && !modelingSubmission.getModel().isEmpty()) {
            jsonModelRepository.writeModel(modelingExercise.getId(), participation.getStudent().getId(), modelingSubmission.getId(), modelingSubmission.getModel());
        }

        result.setSubmission(modelingSubmission);
        modelingSubmission.setResult(result);
        modelingSubmissionRepository.save(modelingSubmission);
        resultRepository.save(result);

        if (modelingSubmission.isSubmitted()) {
            submit(modelingSubmission, modelingExercise);
        }

        return result;
    }

    public void submit(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        this.compassService.addModel(modelingExercise.getId(), modelingSubmission.getId(), modelingSubmission.getModel());
    }

    public JsonObject getModel(long exerciseId, long studentId, long id) {
        if (jsonModelRepository.exists(exerciseId, studentId, id)) {
            return jsonModelRepository.readModel(exerciseId, studentId, id);
        }
        return null;
    }

    private Result initializeResult(Participation participation, ModelingSubmission modelingSubmission) {
        Result result = new Result().participation(participation).submission(modelingSubmission);
        result.setRated(false);
        result.setSuccessful(false);
        result.setCompletionDate(ZonedDateTime.now());

        participation.addResult(result);
        participationService.save(participation);

        return result;
    }

    public ModelingSubmission findByParticipation(Participation participation) {
        ModelingSubmission modelingSubmission;
        Optional<Result> optionalResult = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId());
        Result result;
        if (optionalResult.isPresent()) {
            result = optionalResult.get();
            modelingSubmission = modelingSubmissionRepository.findOne(result.getSubmission().getId());
        } else {
            modelingSubmission = new ModelingSubmission();
            modelingSubmission.setSubmitted(false);
            modelingSubmission.setType(SubmissionType.MANUAL);
            result = initializeResult(participation, modelingSubmission);
        }
        result.setSubmission(modelingSubmission);
        modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

        resultRepository.save(result);
        return modelingSubmission;
    }
}
