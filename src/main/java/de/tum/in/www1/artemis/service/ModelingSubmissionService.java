package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ModelingSubmissionService {
    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final ResultRepository resultRepository;
    private final CompassService compassService;
    private final ParticipationService participationService;
    private final ParticipationRepository participationRepository;


    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, ResultRepository resultRepository, CompassService compassService, ParticipationService participationService, ParticipationRepository participationRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.resultRepository = resultRepository;
        this.compassService = compassService;
        this.participationService = participationService;
        this.participationRepository = participationRepository;
    }


    /**
     * Given an exerciseId, returns all the modeling submissions for that exercise, including their results. Submissions can be filtered to include only already submitted submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @return a list of modeling submissions for the given exercise id
     */
    @Transactional(readOnly = true)
    public List<ModelingSubmission> getModelingSubmissions(Long exerciseId, boolean submittedOnly) {
        List<Participation> participations = participationRepository.findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(exerciseId);
        List<ModelingSubmission> submissions = new ArrayList<>();
        for (Participation participation : participations) {
            Optional<ModelingSubmission> submission = participation.findLatestModelingSubmission();
            if (submission.isPresent()) {
                if (submittedOnly && !submission.get().isSubmitted()) {
                    //filter out non submitted submissions if the flag is set to true
                    continue;
                }
                submissions.add(submission.get());
            }
            //avoid infinite recursion
            participation.getExercise().setParticipations(null);
        }
        // TODO CZ: test if it works + cleanup
//        submissions.forEach(submission -> {
//            Hibernate.initialize(submission.getResult()); // eagerly load the association
//            if (submission.getResult() != null) {
//                Hibernate.initialize(submission.getResult().getAssessor());
//            }
//        });
        return submissions;
    }


    /**
     * Given an exercise id, find a random modeling submission for that exercise which still doesn't have any result. We relay for the randomness to `findAny()`, which return any
     * element of the stream. While it is not mathematically random, it is not deterministic https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html#findAny--
     *
     * @param exerciseId the exercise we want to retrieve
     * @return a modeling submission without any result
     */
    @Transactional(readOnly = true)
    public Optional<ModelingSubmission> getModelingSubmissionWithoutResult(long exerciseId) {
        return this.participationService.findByExerciseIdWithEagerSubmissions(exerciseId).stream().peek(participation -> participation.getExercise().setParticipations(null))
            // Map to Latest Submission
            .map(Participation::findLatestModelingSubmission).filter(Optional::isPresent).map(Optional::get)
            // It needs to be submitted to be ready for assessment
            .filter(Submission::isSubmitted).filter(textSubmission -> {
                Result result = resultRepository.findDistinctBySubmissionId(textSubmission.getId()).orElse(null);
                return result == null;
            })
            .findAny();
    }


    /**
     * Given an exercise id and a tutor id, it returns all the modeling submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param tutorId    - the id of the tutor we are interested in
     * @return a list of modeling submissions
     */
    @Transactional(readOnly = true)
    public List<ModelingSubmission> getAllModelingSubmissionsByTutorForExercise(Long exerciseId, Long tutorId) {
        // We take all the results in this exercise associated to the tutor, and from there we retrieve the submissions
        List<Result> results = this.resultRepository.findAllByParticipationExerciseIdAndAssessorId(exerciseId, tutorId);

        return results.stream().map(result -> {
            Submission submission = result.getSubmission();
            ModelingSubmission modelingSubmission = new ModelingSubmission();

            result.setSubmission(null);
            modelingSubmission.setResult(result);
            modelingSubmission.setParticipation(submission.getParticipation());
            modelingSubmission.setId(submission.getId());
            modelingSubmission.setSubmissionDate(submission.getSubmissionDate());

            return modelingSubmission;
        }).collect(Collectors.toList());
    }


    /**
     * Saves the given submission and the corresponding model and creates the result if necessary.
     * Furthermore, the submission is added to the AutomaticSubmissionService if not submitted yet.
     * Is used for creating and updating modeling submissions.
     * If it is used for a submit action, Compass is notified about the new model.
     * Rolls back if inserting fails - occurs for concurrent createModelingSubmission() calls.
     *
     * @param modelingSubmission the submission to notifyCompass
     * @param modelingExercise   the exercise to notifyCompass in
     * @param participation      the participation where the result should be saved
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
            Optional<ModelingSubmission> optionalModelingSubmission = savedParticipation.findLatestModelingSubmission();
            if (optionalModelingSubmission.isPresent()) {
                modelingSubmission = optionalModelingSubmission.get();
            }
        }

        log.debug("return model: " + modelingSubmission.getModel());
        return modelingSubmission;
    }


    /**
     * Creates and sets new Result object in given submission and stores changes to the database.
     * @param submission
     */
    public void setNewResult(ModelingSubmission submission){
        Result result = new Result();
        result.setSubmission(submission);
        submission.setResult(result);
        submission.getParticipation().addResult(result);
        resultRepository.save(result);
        modelingSubmissionRepository.save(submission);
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


    public ModelingSubmission findOneWithEagerResult(Long id) {
        return modelingSubmissionRepository.findByIdWithEagerResult(id)
            .orElseThrow(() -> new EntityNotFoundException("Modeling submission with id \"" + id + "\" does not exist"));
    }

    public ModelingSubmission findOneWithEagerResultAndFeedback(Long id) {
        return modelingSubmissionRepository.findByIdWithEagerResultAndFeedback(id)
            .orElseThrow(() -> new EntityNotFoundException("Modeling submission with id \"" + id + "\" does not exist"));
    }


    /**
     * Check if Compass could create an automatic assessment (i.e. Result). If an automatic assessment could be found,
     * the corresponding Result and ModelingSubmission entities are updated accordingly.
     * This method is called after Compass is notified about a new model which triggers the automatic assessment
     * attempt.
     *
     * @param modelingSubmission the modeling submission that should be updated with the automatic assessment
     */
    public void checkAutomaticResult(ModelingSubmission modelingSubmission) {
        Participation participation = modelingSubmission.getParticipation();
        Optional<Result> optionalAutomaticResult = resultRepository.findDistinctBySubmissionId(modelingSubmission.getId());
        boolean automaticAssessmentAvailable = optionalAutomaticResult.isPresent() &&
            optionalAutomaticResult.get().getAssessmentType().equals(AssessmentType.AUTOMATIC);

        if (modelingSubmission.getResult() == null && automaticAssessmentAvailable) {
            //use the automatic result if available
            Result result = optionalAutomaticResult.get();
            result.submission(modelingSubmission).participation(participation); // TODO CZ: do we really need to update the result? this is already done in CompassService#assessAutomatically
            modelingSubmission.setResult(result);
            participation.addResult(modelingSubmission.getResult()); // TODO CZ: does this even do anything?
            resultRepository.save(result); // TODO CZ: is this necessary? isn't the result saved together with the modeling submission in the next line anyway?
            modelingSubmissionRepository.save(modelingSubmission);
        }
    }
}
