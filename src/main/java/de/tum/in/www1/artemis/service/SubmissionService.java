package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public abstract class SubmissionService<T extends Submission> {

    protected SubmissionRepository submissionRepository;

    private UserService userService;

    protected AuthorizationCheckService authCheckService;

    protected ResultRepository resultRepository;

    protected ParticipationService participationService;

    protected final SimpMessageSendingOperations messagingTemplate;

    protected final StudentParticipationRepository studentParticipationRepository;

    public SubmissionService(SubmissionRepository submissionRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            ParticipationService participationService, SimpMessageSendingOperations messagingTemplate, StudentParticipationRepository studentParticipationRepository) {
        this.submissionRepository = submissionRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
        this.studentParticipationRepository = studentParticipationRepository;
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
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client. IMPORTANT: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     * @param submission that we want to hide sensitive information for
     */
    public void hideDetails(Submission submission) {
        // do not send old submissions or old results to the client
        if (submission.getParticipation() != null) {
            submission.getParticipation().setSubmissions(null);
            submission.getParticipation().setResults(null);

            Exercise exercise = submission.getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                    exercise.filterSensitiveInformation();
                    submission.setResult(null);
                }
                // remove information about the student from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
                    ((StudentParticipation) submission.getParticipation()).filterSensitiveInformation();
                }
            }
        }
    }

    /**
     * Maps abstract Submission type to concrete submission and assigns result.
     * @param result result that will be assigned to concrete submission
     * @param concreteSubmission concrete submission that will be mapped from abstract submission
     * @return concrete submission of type T
     */
    public T mapAbstractToConcreteSubmission(Result result, T concreteSubmission) {
        Submission submission = result.getSubmission();
        result.setSubmission(null);
        if (concreteSubmission instanceof TextSubmission) {
            concreteSubmission.setLanguage(submission.getLanguage());
        }
        concreteSubmission.setResult(result);
        concreteSubmission.setParticipation(submission.getParticipation());
        concreteSubmission.setId(submission.getId());
        concreteSubmission.setSubmissionDate(submission.getSubmissionDate());
        return concreteSubmission;
    }

    /**
     * Creates a new Result object, assigns it to the given submission and stores the changes to the database. Note, that this method is also called for example submissions which
     * do not have a participation. Therefore, we check if the given submission has a participation and only then update the participation with the new result.
     *
     * @param submission the submission for which a new result should be created
     * @return the newly created result
     */
    public Result setNewResult(Submission submission) {
        Result result = new Result();
        result.setSubmission(submission);
        submission.setResult(result);
        if (submission.getParticipation() != null) {
            submission.getParticipation().addResult(result);
        }
        resultRepository.save(result);
        submissionRepository.save(submission);
        return result;
    }

    /**
     * Hides the result details for given submission
     * @param submission that we want to hide details for
     * @param exercise to which the submission belongs to
     * @return submission with result details hidden
     */
    public T hideResultDetails(T submission, Exercise exercise) {
        // do not send the result to the client if the assessment is not finished
        if (submission.getResult() != null && (submission.getResult().getCompletionDate() == null || submission.getResult().getAssessor() == null)) {
            submission.setResult(null);
        }

        // do not send the assessor information to students
        if (submission.getResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            submission.getResult().setAssessor(null);
        }
        return submission;
    }

    /**
     * Gets randomly any unassessed submission of specified type
     * @param exercise exercise to which the submission belongs
     * @param submissionType concrete type of the submission
     * @return submission of the specified type
     */
    public Optional<T> getRandomUnassessedSubmission(Exercise exercise, Class<T> submissionType) {
        // otherwise return a random submission that is not manually assessed or an empty optional if there is none
        List<T> submissionsWithoutResult = participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(exercise.getId()).stream()
                .map(StudentParticipation -> StudentParticipation.findLatestSubmissionOfType(submissionType)).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }

        Random r = new Random();
        return Optional.of(submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())));
    }

    /**
     * Saves the given submission and creates the result if necessary. This method used for creating and updating submissions. Rolls back if inserting fails - occurs for concurrent calls.
     *
     * @param submission the submission that should be saved
     * @param exercise   the exercise the submission belongs to
     * @param username           the name of the corresponding user
     * @return the saved concrete submission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public T save(T submission, Exercise exercise, String username, Class<T> submissionType) {
        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        submission.setResult(null);

        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginWithEagerSubmissionsAnyState(exercise.getId(), username);
        if (!optionalParticipation.isPresent()) {
            throw new EntityNotFoundException("No participation found for " + username + " in exercise with id " + exercise.getId());
        }
        StudentParticipation participation = optionalParticipation.get();

        if (participation.getInitializationState() == InitializationState.FINISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot submit more than once");
        }

        // For now, we do not allow students to retry their modeling exercise after they have received feedback, because this could lead to unfair situations. Some students might
        // get the manual feedback early and can then retry the exercise within the deadline and have a second chance, others might get the manual feedback late and would not have
        // a chance to try it out again.
        // TODO: think about how we can enable retry again in the future in a fair way
        // make sure that no (submitted) submission exists for the given user and exercise to prevent retry submissions
        boolean submittedSubmissionExists = participation.getSubmissions().stream().anyMatch(Submission::isSubmitted);
        if (submittedSubmissionExists) {
            throw new BadRequestAlertException("User " + username + " already participated in exercise with id " + exercise.getId(), "submission", "participationExists");
        }

        // update submission properties
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(participation);
        submission = submissionRepository.save(submission);

        participation.addSubmissions(submission);

        if (submission.isSubmitted()) {
            participation.setInitializationState(InitializationState.FINISHED);
            // We remove all unfinished results here as they should not be sent to the client. Note, that the reference to the unfinished results will not get removed in the
            // database by saving the participation to the DB below since the results are not persisted with the participation.
            participation.setResults(
                    participation.getResults().stream().filter(result -> result.getCompletionDate() != null && result.getAssessor() != null).collect(Collectors.toSet()));
            messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/exercise/" + participation.getExercise().getId() + "/participation",
                    participation);
        }
        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (submission.getId() == null) {
            Optional<T> optionalSubmission = savedParticipation.findLatestSubmissionOfType(submissionType);
            if (optionalSubmission.isPresent()) {
                submission = optionalSubmission.get();
            }
        }
        return submission;
    }

    /**
     * Given an exerciseId, returns all the submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions.
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @return a list of submissions of given type for the given exercise id
     */
    @Transactional(readOnly = true)
    public List<T> getSubmissions(Long exerciseId, boolean submittedOnly, Class<T> submissionType) {
        List<StudentParticipation> participations = studentParticipationRepository.findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(exerciseId);
        List<T> submissions = new ArrayList<>();
        participations.stream().peek(participation -> participation.getExercise().setStudentParticipations(null))
                .map(StudentParticipation -> StudentParticipation.findLatestSubmissionOfType(submissionType))
                // filter out non submitted submissions if the flag is set to true
                .filter(submission -> submission.isPresent() && (!submittedOnly || submission.get().isSubmitted())).forEach(submission -> submissions.add(submission.get()));
        return submissions;
    }

}
