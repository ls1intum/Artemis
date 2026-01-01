package de.tum.cit.aet.artemis.modeling.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionVersionService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ModelingSubmissionService extends SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ModelingSubmissionService.class);

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final SubmissionVersionService submissionVersionService;

    private final ExerciseDateService exerciseDateService;

    public ModelingSubmissionService(ModelingSubmissionRepository modelingSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            UserRepository userRepository, SubmissionVersionService submissionVersionService, ParticipationService participationService,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService, FeedbackRepository feedbackRepository,
            Optional<ExamDateApi> examDateApi, ExerciseDateService exerciseDateService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ComplaintRepository complaintRepository, FeedbackService feedbackService, Optional<AthenaApi> athenaSubmissionSelectionService) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateApi,
                exerciseDateService, courseRepository, participationRepository, complaintRepository, feedbackService, athenaSubmissionSelectionService);
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.submissionVersionService = submissionVersionService;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Get the modeling submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it.
     * Additionally, check if the submission lock limit has been reached.
     *
     * @param submissionId     the id of the modeling submission
     * @param modelingExercise the corresponding exercise
     * @param correctionRound  the correction round for which we want the lock
     * @return the locked modeling submission
     */
    public ModelingSubmission lockAndGetModelingSubmission(Long submissionId, ModelingExercise modelingExercise, int correctionRound) {
        var submission = modelingSubmissionRepository.findByIdWithEagerResultAndFeedbackAndAssessorAndAssessmentNoteAndParticipationResultsElseThrow(submissionId);

        if (submission.getLatestResult() == null || submission.getLatestResult().getAssessor() == null) {
            checkSubmissionLockLimit(modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        }

        lockSubmission(submission, correctionRound);
        return submission;
    }

    /**
     * Saves the given submission and the corresponding model and creates the result if necessary. This method used for creating and updating modeling submissions.
     *
     * @param modelingSubmission the submission that should be saved
     * @param exercise           the exercise the submission belongs to
     * @param user               the user who initiated the save
     * @return the saved modelingSubmission entity
     */
    public ModelingSubmission handleModelingSubmission(ModelingSubmission modelingSubmission, ModelingExercise exercise, User user) {
        final var optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(exercise, user.getLogin());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + user.getLogin() + " in exercise " + exercise.getId());
        }
        final var participation = optionalParticipation.get();
        final var dueDate = ExerciseDateService.getDueDate(participation);
        // Important: for exam exercises, we should NOT check the exercise due date, we only check if for course exercises
        if (dueDate.isPresent() && exerciseDateService.isAfterDueDate(participation) && participation.getInitializationDate().isBefore(dueDate.get())) {
            throw new AccessForbiddenException();
        }

        // update submission properties
        // NOTE: from now on we always set submitted to true to prevent problems here! Except for late submissions of course exercises to prevent issues in auto-save
        if (exercise.isExamExercise() || exerciseDateService.isBeforeDueDate(participation)) {
            modelingSubmission.setSubmitted(true);
        }

        // if athena results are present, then create a new submission on submit
        // If results exist for this submission, create a new submission by setting the ID to null
        if (modelingSubmission.getId() != null && resultRepository.existsBySubmissionId(modelingSubmission.getId())) {
            modelingSubmission.setId(null);
        }
        modelingSubmission = save(modelingSubmission, exercise, user, participation);
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

        if (participation.getInitializationState() != InitializationState.FINISHED) {
            participation.setInitializationState(InitializationState.FINISHED);
            studentParticipationRepository.save(participation);
        }

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        modelingSubmission.setResults(new HashSet<>());
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

        return modelingSubmission;
    }

    /**
     * retrieves a modeling submission without assessment for the specified correction round and potentially locks the submission
     *
     * @param lockSubmission   whether the submission should be locked
     * @param correctionRound  the correction round (0 = first correction, 1 = second correction
     * @param modelingExercise the modeling exercise for which a
     * @param isExamMode       whether the exercise belongs to an exam
     * @return a random modeling submission if present
     */
    public Optional<ModelingSubmission> findRandomSubmissionWithoutExistingAssessment(boolean lockSubmission, int correctionRound, ModelingExercise modelingExercise,
            boolean isExamMode) {
        var submissionWithoutResult = super.getRandomAssessableSubmission(modelingExercise, isExamMode, correctionRound);
        if (submissionWithoutResult.isEmpty()) {
            return Optional.empty();
        }

        // NOTE: we load the feedback for the submission eagerly to avoid org.hibernate.LazyInitializationException
        var submissionId = submissionWithoutResult.get().getId();
        var submission = modelingSubmissionRepository.findByIdWithEagerResultAndFeedbackAndAssessorAndAssessmentNoteAndParticipationResultsElseThrow(submissionId);
        if (lockSubmission) {
            lockSubmission(submission, correctionRound);
        }

        return Optional.of(submission);
    }
}
