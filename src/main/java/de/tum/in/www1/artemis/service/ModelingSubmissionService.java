package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ModelingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final CompassService compassService;

    private final ParticipationService participationService;

    private final UserService userService;

    private final ParticipationRepository participationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, SubmissionRepository submissionRepository, ResultService resultService,
            ResultRepository resultRepository, CompassService compassService, ParticipationService participationService, UserService userService,
            ParticipationRepository participationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.compassService = compassService;
        this.participationService = participationService;
        this.userService = userService;
        this.participationRepository = participationRepository;
        this.messagingTemplate = messagingTemplate;
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
        ModelingSubmission modelingSubmission = findOneWithEagerResultAndFeedback(submissionId);
        if (modelingSubmission.getResult() == null || modelingSubmission.getResult().getAssessor() == null) {
            checkSubmissionLockLimit(modelingExercise.getCourse().getId());
        }
        lockSubmission(modelingSubmission, modelingExercise);
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
        ModelingSubmission modelingSubmission = getModelingSubmissionWithoutResult(modelingExercise)
                .orElseThrow(() -> new EntityNotFoundException("Modeling submission for exercise " + modelingExercise.getId() + " could not be found"));
        lockSubmission(modelingSubmission, modelingExercise);
        return modelingSubmission;
    }

    /**
     * Given an exercise, find a modeling submission for that exercise which still doesn't have any result. If the diagram type is supported by Compass we get the next optimal
     * submission from Compass, i.e. the submission for which an assessment means the most knowledge gain for the automatic assessment mechanism. If it's not supported by Compass
     * we just get a random submission without assessment.
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
                // TODO CZ: think about how to handle canceled assessments with Compass as I do not want to receive the same submission again, if I canceled the assessment
                return modelingSubmissionRepository.findById(optimalModelSubmissions.iterator().next());
            }
        }

        // otherwise return a random submission that is not assessed or an empty optional
        Random r = new Random();
        List<ModelingSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(modelingExercise.getId()).stream()
                .map(Participation::findLatestModelingSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())));
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

        Optional<Participation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginWithEagerSubmissionsAnyState(modelingExercise.getId(), username);
        if (!optionalParticipation.isPresent()) {
            throw new EntityNotFoundException("No participation found for " + username + " in exercise with id " + modelingExercise.getId());
        }
        Participation participation = optionalParticipation.get();

        // For now, we do not allow students to retry their modeling exercise after they have received feedback, because this could lead to unfair situations. Some students might
        // get the manual feedback early and can then retry the exercise within the deadline and have a second chance, others might get the manual feedback late and would not have
        // a chance to try it out again.
        // TODO: think about how we can enable retry again in the future in a fair way
        // make sure that no (submitted) submission exists for the given user and exercise to prevent retry submissions
        boolean submittedSubmissionExists = participation.getSubmissions().stream().anyMatch(submission -> submission.isSubmitted());
        if (submittedSubmissionExists) {
            throw new BadRequestAlertException("User " + username + " already participated in exercise with id " + modelingExercise.getId(), "modelingSubmission",
                    "participationExists");
        }

        // update submission properties
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setType(SubmissionType.MANUAL);
        modelingSubmission.setParticipation(participation);
        modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

        participation.addSubmissions(modelingSubmission);

        if (modelingSubmission.isSubmitted()) {
            notifyCompass(modelingSubmission, modelingExercise);
            checkAutomaticResult(modelingSubmission, modelingExercise);
            participation.setInitializationState(InitializationState.FINISHED);
            messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/exercise/" + participation.getExercise().getId() + "/participation",
                    participation);
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
     * Check if the limit of simultaneously locked submissions (i.e. unfinished assessments) has been reached for the current user in the given course. Throws a
     * BadRequestAlertException if the limit has been reached.
     *
     * @param courseId the id of the course
     */
    public void checkSubmissionLockLimit(long courseId) {
        long numberOfLockedSubmissions = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userService.getUserWithGroupsAndAuthorities().getId(), courseId);
        if (numberOfLockedSubmissions >= MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) {
            throw new BadRequestAlertException("The limit of locked submissions has been reached", "submission", "lockedSubmissionsLimitReached");
        }
    }

    /**
     * Soft lock the submission to prevent other tutors from receiving and assessing it. We remove the model from the models waiting for assessment in Compass to prevent other
     * tutors from retrieving it in the first place. Additionally, we set the assessor and save the result to soft lock the assessment in the client, i.e. the client will not allow
     * tutors to assess a model when an assessor is already assigned. If no result exists for this submission we create one first.
     *
     * @param modelingSubmission the submission to lock
     * @param modelingExercise   the exercise to which the submission belongs to (needed for Compass)
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
        return modelingSubmissionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Modeling submission with id \"" + id + "\" does not exist"));
    }

    public ModelingSubmission findOneWithEagerResult(Long id) {
        return modelingSubmissionRepository.findByIdWithEagerResult(id).orElseThrow(() -> new EntityNotFoundException("Modeling submission with id \"" + id + "\" does not exist"));
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
    public void checkAutomaticResult(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        if (!compassService.isSupported(modelingExercise.getDiagramType())) {
            return;
        }
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

    /**
     * @param courseId the course we are interested in
     * @return the number of text submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    @Transactional(readOnly = true)
    public long countSubmissionsToAssessByCourseId(Long courseId) {
        return modelingSubmissionRepository.countByCourseIdSubmittedBeforeDueDate(courseId);
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of text submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    @Transactional(readOnly = true)
    public long countSubmissionsToAssessByExerciseId(Long exerciseId) {
        return modelingSubmissionRepository.countByExerciseIdSubmittedBeforeDueDate(exerciseId);
    }
}
