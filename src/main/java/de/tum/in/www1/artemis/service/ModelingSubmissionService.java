package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ModelingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final CompassService compassService;

    private final ParticipationService participationService;

    private final ParticipationRepository participationRepository;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, ResultService resultService, ResultRepository resultRepository, CompassService compassService,
                                     ParticipationService participationService, ParticipationRepository participationRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.compassService = compassService;
        this.participationService = participationService;
        this.participationRepository = participationRepository;
    }

    /**
     * Given an exerciseId, returns all the modeling submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions
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
                    // filter out non submitted submissions if the flag is set to true
                    continue;
                }
                submissions.add(submission.get());
            }
            // avoid infinite recursion
            participation.getExercise().setParticipations(null);
        }
        return submissions;
    }

    @Transactional
    public ModelingSubmission getLockedModelingSubmission(Long submissionId, ModelingExercise modelingExercise) {
        ModelingSubmission modelingSubmission = findOneWithEagerResultAndFeedback(submissionId);
        lockSubmission(modelingSubmission, modelingExercise);
        return modelingSubmission;
    }

    @Transactional
    public ModelingSubmission getLockedModelingSubmissionWithoutResult(ModelingExercise modelingExercise) {
        ModelingSubmission modelingSubmission = getModelingSubmissionWithoutResult(modelingExercise)
            .orElseThrow(() -> new EntityNotFoundException("Modeling submission for exercise " + modelingExercise.getId() + " could not be found"));
        lockSubmission(modelingSubmission, modelingExercise);
        return modelingSubmission;
    }

    /**
     * Given an exercise, find a modeling submission for that exercise which still doesn't have any result.
     * If the diagram type is supported by Compass we get the next optimal submission from Compass, i.e. the submission
     * for which an assessment means the most knowledge gain for the automatic assessment mechanism.
     * If it's not supported by Compass we just get a random submission without assessment. We relay for the randomness
     * to `findAny()`, which return any element of the stream. While it is not mathematically random, it is not
     * deterministic https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html#findAny--
     *
     * @param modelingExercise the modeling exercise for which we want to get a modeling submission without result
     * @return a modeling submission without any result
     */
    @Transactional(readOnly = true)
    public Optional<ModelingSubmission> getModelingSubmissionWithoutResult(ModelingExercise modelingExercise) {
        // ask Compass for optimal submission to assess if diagram type is supported
        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            Set<Long> optimalModelSubmissions = compassService.getModelsWaitingForAssessment(modelingExercise.getId());
            if (!optimalModelSubmissions.isEmpty()) {
                return modelingSubmissionRepository.findById(optimalModelSubmissions.iterator().next());
            }
        }
        // otherwise return any submission that is not assessed
        return participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(modelingExercise.getId()).stream()
                // map to latest submission
                .map(Participation::findLatestModelingSubmission).filter(Optional::isPresent).map(Optional::get).findAny();
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
     * Saves the given submission and the corresponding model and creates the result if necessary. Furthermore, the submission is added to the AutomaticSubmissionService if not
     * submitted yet. Is used for creating and updating modeling submissions. If it is used for a submit action, Compass is notified about the new model. Rolls back if inserting
     * fails - occurs for concurrent createModelingSubmission() calls.
     *
     * @param modelingSubmission the submission to notifyCompass
     * @param modelingExercise   the exercise to notifyCompass in
     * @param username           the name of the corresponding user
     * @return the modelingSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelingSubmission save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, String username) {

        Optional<Participation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginAnyState(modelingExercise.getId(), username);
        if (!optionalParticipation.isPresent()) {
            throw new EntityNotFoundException("No participation found for " + username + " in exercise " + modelingExercise.getId());
        }
        Participation participation = optionalParticipation.get();

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
        }
        else if (modelingExercise.getDueDate() != null && !modelingExercise.isEnded()) {
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
     * Soft lock the submission to prevent other tutors from receiving and assessing it.
     * We remove the model from the models waiting for assessment in Compass to prevent other tutors from retrieving it
     * in the first place.
     * Additionally, we set the assessor and save the result to soft lock the assessment in the client, i.e. the client
     * will not allow tutors to assess a model when an assessor is already assigned. If no result exists for this
     * submission we create one first.
     *
     * @param modelingSubmission the submission to lock
     * @param modelingExercise the exercise to which the submission belongs to (needed for Compass)
     */
    private void lockSubmission(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        if (modelingSubmission.getResult() == null) {
            setNewResult(modelingSubmission);
        }
        if (modelingSubmission.getResult().getAssessor() == null) {
            if (compassService.isSupported(modelingExercise.getDiagramType())) {
                compassService.removeModelWaitingForAssessment(modelingExercise.getId(), modelingSubmission.getId());
            }
            resultService.setAssessor(modelingSubmission.getResult());
        }
    }

    /**
     * Creates and sets new Result object in given submission and stores changes to the database.
     *
     * @param submission
     */
    private void setNewResult(ModelingSubmission submission) {
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

    public ModelingSubmission findOne(Long id) {
        return modelingSubmissionRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Modeling submission with id \"" + id + "\" does not exist"));
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
     * Check if Compass could create an automatic assessment (i.e. Result). If an automatic assessment could be found, the corresponding Result and ModelingSubmission entities are
     * updated accordingly. This method is called after Compass is notified about a new model which triggers the automatic assessment attempt.
     *
     * @param modelingSubmission the modeling submission that should be updated with the automatic assessment
     */
    public void checkAutomaticResult(ModelingSubmission modelingSubmission) {
        Participation participation = modelingSubmission.getParticipation();
        Optional<Result> optionalAutomaticResult = resultRepository.findDistinctBySubmissionId(modelingSubmission.getId());
        boolean automaticAssessmentAvailable = optionalAutomaticResult.isPresent() && optionalAutomaticResult.get().getAssessmentType().equals(AssessmentType.AUTOMATIC);

        if (modelingSubmission.getResult() == null && automaticAssessmentAvailable) {
            // use the automatic result if available
            Result result = optionalAutomaticResult.get();
            result.submission(modelingSubmission).participation(participation); // TODO CZ: do we really need to update the result? this is already done in
                                                                                // CompassService#assessAutomatically
            modelingSubmission.setResult(result);
            participation.addResult(modelingSubmission.getResult()); // TODO CZ: does this even do anything?
            resultRepository.save(result); // TODO CZ: is this necessary? isn't the result saved together with the modeling submission in the next line anyway?
            modelingSubmissionRepository.save(modelingSubmission);
        }
    }
}
