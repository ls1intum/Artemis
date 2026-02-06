package de.tum.cit.aet.artemis.modeling.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionVersionService;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;

@Conditional(ModelingEnabled.class)
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
     * Record holding the result of retrieving submission assessment data.
     *
     * @param submission    the submission
     * @param participation the student participation
     * @param exercise      the exercise
     * @param result        the result
     */
    public record SubmissionAssessmentData(Submission submission, StudentParticipation participation, Exercise exercise, Result result) {
    }

    /**
     * Retrieves the submission, participation, exercise, and latest completed result for a given submission ID.
     * This method handles the pattern of fetching assessment data from a modeling submission.
     *
     * @param submissionId the id of the submission
     * @return the assessment data containing submission, participation, exercise, and result
     * @throws EntityNotFoundException if the submission or result is not found
     */
    public SubmissionAssessmentData getSubmissionAssessmentData(Long submissionId) {
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNoteAndTeamStudents(submissionId);
        if (submission == null) {
            throw new EntityNotFoundException("Submission", submissionId);
        }
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        Exercise exercise = participation.getExercise();

        Result result = submission.getLatestCompletedResult();
        if (result == null) {
            throw new EntityNotFoundException("Result with submission", submissionId);
        }

        return new SubmissionAssessmentData(submission, participation, exercise, result);
    }

    /**
     * Retrieves and prepares the latest modeling submission for a student participation.
     * This method handles filtering of results based on assessment status, due dates, and user permissions.
     *
     * @param studentParticipation the student participation to get the latest submission for
     * @param modelingExercise     the modeling exercise
     * @param isAtLeastTutor       whether the current user has at least tutor permissions
     * @return the prepared modeling submission with appropriately filtered results
     */
    public ModelingSubmission getLatestSubmissionForParticipation(StudentParticipation studentParticipation, ModelingExercise modelingExercise, boolean isAtLeastTutor) {
        Optional<Submission> optionalLatestSubmission = studentParticipation.findLatestSubmission();
        ModelingSubmission modelingSubmission;

        if (optionalLatestSubmission.isEmpty()) {
            // this should never happen as the submission is initialized along with the participation when the exercise is started
            modelingSubmission = new ModelingSubmission();
            modelingSubmission.setParticipation(studentParticipation);
        }
        else {
            modelingSubmission = (ModelingSubmission) optionalLatestSubmission.get();
        }

        // do not send the result to the client if the assessment is not finished
        Result latestResult = getLatestResultByCompletionDate(modelingSubmission);
        if (latestResult != null && latestResult.getAssessmentType() != AssessmentType.AUTOMATIC_ATHENA
                && (latestResult.getCompletionDate() == null || latestResult.getAssessor() == null)) {
            modelingSubmission.setResults(List.of());
        }

        if (!ExerciseDateService.isAfterAssessmentDueDate(modelingExercise)) {
            // We want to have the preliminary feedback before the assessment due date too
            List<Result> athenaResults = modelingSubmission.getResults().stream().filter(result -> result != null && result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA)
                    .toList();
            modelingSubmission.setResults(athenaResults);
        }

        Result resultToFilter = getLatestResultByCompletionDate(modelingSubmission);
        if (resultToFilter != null && !isAtLeastTutor) {
            resultToFilter.filterSensitiveInformation();
        }

        // make sure sensitive information are not sent to the client
        modelingExercise.filterSensitiveInformation();
        if (modelingExercise.isExamExercise()) {
            modelingExercise.getExerciseGroup().setExam(null);
        }

        return modelingSubmission;
    }

    /**
     * Gets the result with the latest completion date from a submission.
     *
     * @param submission the submission to get the latest result from
     * @return the result with the latest completion date, or null if no results exist
     */
    private Result getLatestResultByCompletionDate(ModelingSubmission submission) {
        if (submission.getResults() == null || submission.getResults().isEmpty()) {
            return null;
        }
        return submission.getResults().stream().filter(result -> result != null && result.getCompletionDate() != null).max(Comparator.comparing(Result::getCompletionDate))
                .orElse(submission.getLatestResult());
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
