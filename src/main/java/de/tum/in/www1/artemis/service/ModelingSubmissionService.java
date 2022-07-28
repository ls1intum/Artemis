package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.modeling.SimilarElementCount;
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

    private final ModelElementRepository modelElementRepository;

    private final ExerciseDateService exerciseDateService;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            CompassService compassService, UserRepository userRepository, SubmissionVersionService submissionVersionService, ParticipationService participationService,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService, FeedbackRepository feedbackRepository,
            ExamDateService examDateService, ExerciseDateService exerciseDateService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ModelElementRepository modelElementRepository, ComplaintRepository complaintRepository) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                exerciseDateService, courseRepository, participationRepository, complaintRepository);
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.compassService = compassService;
        this.submissionVersionService = submissionVersionService;
        this.modelElementRepository = modelElementRepository;
        this.exerciseDateService = exerciseDateService;
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
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findByIdWithEagerResultAndFeedbackAndAssessorAndParticipationResultsElseThrow(submissionId);

        if (modelingSubmission.getLatestResult() == null || modelingSubmission.getLatestResult().getAssessor() == null) {
            checkSubmissionLockLimit(modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
            if (compassService.isSupported(modelingExercise) && correctionRound == 0L) {
                modelingSubmission = assignResultWithFeedbackSuggestionsToSubmission(modelingSubmission, modelingExercise);
            }
        }

        lockSubmission(modelingSubmission, correctionRound);
        return modelingSubmission;
    }

    /**
     * Saves the given submission and the corresponding model and creates the result if necessary. This method used for creating and updating modeling submissions.
     *
     * @param modelingSubmission the submission that should be saved
     * @param modelingExercise   the exercise the submission belongs to
     * @param user               the user who initiated the save
     * @return the saved modelingSubmission entity
     */
    public ModelingSubmission handleModelingSubmission(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, User user) {
        final var optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(modelingExercise, user.getLogin());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + user.getLogin() + " in exercise " + modelingExercise.getId());
        }
        final var participation = optionalParticipation.get();
        final var dueDate = exerciseDateService.getDueDate(participation);
        // Important: for exam exercises, we should NOT check the exercise due date, we only check if for course exercises
        if (dueDate.isPresent() && exerciseDateService.isAfterDueDate(participation) && participation.getInitializationDate().isBefore(dueDate.get())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        modelingSubmission.setResults(new ArrayList<>());

        // update submission properties
        // NOTE: from now on we always set submitted to true to prevent problems here! Except for late submissions of course exercises to prevent issues in auto-save
        if (modelingExercise.isExamExercise() || exerciseDateService.isBeforeDueDate(participation)) {
            modelingSubmission.setSubmitted(true);
        }
        modelingSubmission = save(modelingSubmission, modelingExercise, user, participation);
        return modelingSubmission;
    }

    /**
     * Saves the given submission. Is used for creating and updating modeling submissions.
     *
     * @param modelingSubmission the submission that should be saved
     * @param participation      the participation the submission belongs to
     * @param modelingExercise   the exercise the submission belongs to
     * @param user               the user who initiated the save
     * @return the textSubmission entity that was saved to the database
     */
    private ModelingSubmission save(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise, User user, StudentParticipation participation) {
        modelingSubmission.setSubmissionDate(ZonedDateTime.now());
        modelingSubmission.setType(SubmissionType.MANUAL);
        participation.addSubmission(modelingSubmission);

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        modelingSubmission.setResults(new ArrayList<>());
        modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);

        // versioning of submission
        try {
            if (modelingExercise.isTeamMode()) {
                submissionVersionService.saveVersionForTeam(modelingSubmission, user);
            }
            else if (modelingExercise.isExamExercise()) {
                submissionVersionService.saveVersionForIndividual(modelingSubmission, user);
            }
        }
        catch (Exception ex) {
            log.error("Modeling submission version could not be saved", ex);
        }

        if (participation.getInitializationState() != InitializationState.FINISHED) {
            participation.setInitializationState(InitializationState.FINISHED);
            studentParticipationRepository.save(participation);
        }

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
        var submissionWithoutResult = super.getRandomSubmissionEligibleForNewAssessment(modelingExercise, isExamMode, correctionRound)
                .orElseThrow(() -> new EntityNotFoundException("Modeling submission for exercise " + modelingExercise.getId() + " could not be found"));
        ModelingSubmission modelingSubmission = (ModelingSubmission) submissionWithoutResult;
        if (lockSubmission) {
            if (compassService.isSupported(modelingExercise) && correctionRound == 0L) {
                modelingSubmission = assignResultWithFeedbackSuggestionsToSubmission(modelingSubmission, modelingExercise);
                setNumberOfAffectedSubmissionsPerElement(modelingSubmission);
            }
            lockSubmission(modelingSubmission, correctionRound);
        }
        return modelingSubmission;
    }

    /**
     * Soft lock the submission to prevent other tutors from receiving and assessing it. We remove the model from the models waiting for assessment in Compass to prevent other
     * tutors from retrieving it in the first place. Additionally, we set the assessor and save the result to soft lock the assessment in the client, i.e. the client will not allow
     * tutors to assess a model when an assessor is already assigned. If no result exists for this submission we create one first.
     *
     * @param modelingSubmission the submission to lock
     */
    private void lockSubmission(ModelingSubmission modelingSubmission, int correctionRound) {
        super.lockSubmission(modelingSubmission, correctionRound);
    }

    /**
     * Assigns an automatic result generated by Compass to the given modeling submission and saves the updated submission to the database. If the given submission already contains
     * a manual result, it will not get updated with the automatic result.
     *
     * @param modelingSubmission the modeling submission that should be updated with an automatic result generated by Compass
     * @param modelingExercise the modeling exercise to which the submission belongs
     * @return the updated modeling submission
     */
    private ModelingSubmission assignResultWithFeedbackSuggestionsToSubmission(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        var existingResult = modelingSubmission.getLatestResult();
        if (existingResult != null && existingResult.getAssessmentType() != null && existingResult.getAssessmentType() == AssessmentType.MANUAL) {
            return modelingSubmission;
        }
        Result automaticResult = compassService.getSuggestionResult(modelingSubmission, modelingExercise);
        if (automaticResult != null) {
            automaticResult.setSubmission(null);
            automaticResult.setParticipation(modelingSubmission.getParticipation());
            automaticResult = resultRepository.save(automaticResult);

            automaticResult.setSubmission(modelingSubmission);
            modelingSubmission.addResult(automaticResult);
            modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);
        }
        return modelingSubmission;
    }

    /**
     * Sets number of potential automatic Feedback's for each model element belonging to the `Result`'s submission.
     * This number determines how many other submissions would be affected if the user were to submit a certain element feedback.
     * For each ModelElement of the submission, this method finds how many other ModelElements exist in the same cluster.
     * This number is represented with the `numberOfAffectedSubmissions` field which is set here for each
     * ModelElement of this submission
     *
     * @param submission Result for the Submission acting as a reference for the modeling submission to be searched.
     */
    public void setNumberOfAffectedSubmissionsPerElement(@NotNull ModelingSubmission submission) {
        List<ModelElementRepository.ModelElementCount> elementCounts = modelElementRepository.countOtherElementsInSameClusterForSubmissionId(submission.getId());
        submission.setSimilarElements(elementCounts.stream().map(modelElementCount -> {
            SimilarElementCount similarElementCount = new SimilarElementCount();
            similarElementCount.setElementId(modelElementCount.getElementId());
            similarElementCount.setNumberOfOtherElements(modelElementCount.getNumberOfOtherElements());
            return similarElementCount;
        }).collect(Collectors.toSet()));
    }
}
