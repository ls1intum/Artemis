package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelingSubmissionService extends SubmissionService {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final CompassService compassService;

    private final SubmissionVersionService submissionVersionService;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            CompassService compassService, UserRepository userRepository, SubmissionVersionService submissionVersionService, ParticipationService participationService,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService, FeedbackRepository feedbackRepository,
            ExamDateService examDateService, CourseRepository courseRepository, ParticipationRepository participationRepository) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                courseRepository, participationRepository);
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.compassService = compassService;
        this.submissionVersionService = submissionVersionService;
    }

    /**
     * Get the modeling submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it.
     * Additionally, check if the submission lock limit has been reached.
     *
     * In case Compass is supported (and activated), this method also assigns a result with feedback suggestions to the submission
     *
     * @param submissionId     the id of the modeling submission
     * @param modelingExercise the corresponding exercise
     * @param correctionRound the correction round for which we want the lock
     * @return the locked modeling submission
     */
    public ModelingSubmission lockAndGetModelingSubmission(Long submissionId, ModelingExercise modelingExercise, int correctionRound) {
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findOneWithEagerResultAndFeedbackAndAssessorAndParticipationResults(submissionId);

        if (modelingSubmission.getLatestResult() == null || modelingSubmission.getLatestResult().getAssessor() == null) {
            checkSubmissionLockLimit(modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
            if (compassService.isSupported(modelingExercise) && correctionRound == 0L) {
                modelingSubmission = assignResultWithFeedbackSuggestionsToSubmission(modelingSubmission);
            }
        }

        lockSubmission(modelingSubmission, modelingExercise, correctionRound);
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
     * @param correctionRound - the correction round we want our submission to have results for
     * @param examMode flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a modeling submission without any result
     */
    private Optional<ModelingSubmission> getRandomModelingSubmissionEligibleForNewAssessment(ModelingExercise modelingExercise, boolean examMode, int correctionRound) {
        // if the diagram type is supported by Compass, ask Compass for optimal (i.e. most knowledge gain for automatic assessments) submissions to assess next
        // NOTE: compass only makes sense for the first correction round (i.e. correctionRound == 0)
        if (compassService.isSupported(modelingExercise) && correctionRound == 0) {
            List<Long> modelsWaitingForAssessment = compassService.getModelsWaitingForAssessment(modelingExercise.getId());

            // shuffle the model list to prevent that the user gets the same submission again after canceling an assessment
            Collections.shuffle(modelsWaitingForAssessment);

            for (Long submissionId : modelsWaitingForAssessment) {
                Optional<ModelingSubmission> submission = modelingSubmissionRepository.findWithResultsFeedbacksAssessorAndParticipationResultsById(submissionId);
                if (submission.isPresent()) {
                    return submission;
                }
                else {
                    compassService.removeModelWaitingForAssessment(modelingExercise.getId(), submissionId);
                }
            }
        }

        var submissionWithoutResult = super.getRandomSubmissionEligibleForNewAssessment(modelingExercise, examMode, correctionRound);
        if (submissionWithoutResult.isPresent()) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissionWithoutResult.get();
            return Optional.of(modelingSubmission);
        }
        return Optional.empty();
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
    public ModelingSubmission save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, String username) {
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(modelingExercise, username);
        if (optionalParticipation.isEmpty()) {
            throw new EntityNotFoundException("No participation found for " + username + " in exercise with id " + modelingExercise.getId());
        }
        StudentParticipation participation = optionalParticipation.get();

        final var exerciseDueDate = modelingExercise.getDueDate();
        if (exerciseDueDate != null && exerciseDueDate.isBefore(ZonedDateTime.now()) && participation.getInitializationDate().isBefore(exerciseDueDate)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        modelingSubmission.setResults(new ArrayList<>());

        // update submission properties
        // NOTE: from now on we always set submitted to true to prevent problems here!
        modelingSubmission.setSubmitted(true);
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setType(SubmissionType.MANUAL);
        modelingSubmission.setParticipation(participation);
        modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

        // versioning of submission
        try {
            if (modelingExercise.isTeamMode()) {
                submissionVersionService.saveVersionForTeam(modelingSubmission, username);
            }
            else if (modelingExercise.isExamExercise()) {
                submissionVersionService.saveVersionForIndividual(modelingSubmission, username);
            }
        }
        catch (Exception ex) {
            log.error("Modeling submission version could not be saved: " + ex);
        }

        participation.addSubmission(modelingSubmission);

        participation.setInitializationState(InitializationState.FINISHED);

        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (modelingSubmission.getId() == null) {
            Optional<Submission> optionalSubmission = savedParticipation.findLatestSubmission();
            if (optionalSubmission.isPresent()) {
                modelingSubmission = (ModelingSubmission) optionalSubmission.get();
            }
        }

        log.debug("return model: " + modelingSubmission.getModel());
        return modelingSubmission;
    }

    /**
     * retrieves a modeling submission without assessment for the specified correction round and potentially locks the submission
     *
     * In case Compass is supported (and activated), this method also assigns a result with feedback suggestions to the submission
     *
     * @param lockSubmission whether the submission should be locked
     * @param correctionRound the correction round (0 = first correction, 1 = second correction
     * @param modelingExercise the modeling exercise for which a
     * @param isExamMode whether the exercise belongs to an exam
     * @return a random modeling submission (potentially based on compass)
     */
    public ModelingSubmission findRandomSubmissionWithoutExistingAssessment(boolean lockSubmission, int correctionRound, ModelingExercise modelingExercise, boolean isExamMode) {
        var modelingSubmission = getRandomModelingSubmissionEligibleForNewAssessment(modelingExercise, isExamMode, correctionRound)
                .orElseThrow(() -> new EntityNotFoundException("Modeling submission for exercise " + modelingExercise.getId() + " could not be found"));
        if (lockSubmission) {
            if (compassService.isSupported(modelingExercise) && correctionRound == 0L) {
                modelingSubmission = assignResultWithFeedbackSuggestionsToSubmission(modelingSubmission);
            }
            lockSubmission(modelingSubmission, modelingExercise, correctionRound);
        }
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
    private void lockSubmission(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, int correctionRound) {
        var result = super.lockSubmission(modelingSubmission, correctionRound);
        if (result.getAssessor() == null && compassService.isSupported(modelingExercise)) {
            compassService.removeModelWaitingForAssessment(modelingExercise.getId(), modelingSubmission.getId());
        }
    }

    /**
     * Assigns an automatic result generated by Compass to the given modeling submission and saves the updated submission to the database. If the given submission already contains
     * a manual result, it will not get updated with the automatic result.
     *
     * @param modelingSubmission the modeling submission that should be updated with an automatic result generated by Compass
     * @return the updated modeling submission
     */
    private ModelingSubmission assignResultWithFeedbackSuggestionsToSubmission(ModelingSubmission modelingSubmission) {
        var existingResult = modelingSubmission.getLatestResult();
        if (existingResult != null && existingResult.getAssessmentType() != null && existingResult.getAssessmentType().equals(AssessmentType.MANUAL)) {
            return modelingSubmission;
        }
        var studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(modelingSubmission.getId());
        if (automaticResult != null) {
            automaticResult.setSubmission(null);
            automaticResult.setParticipation(modelingSubmission.getParticipation());
            automaticResult = resultRepository.save(automaticResult);

            automaticResult.setSubmission(modelingSubmission);
            modelingSubmission.addResult(automaticResult);
            modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

            compassService.removeSemiAutomaticResultForSubmission(modelingSubmission.getId(), exerciseId);
        }

        return modelingSubmission;
    }
}
