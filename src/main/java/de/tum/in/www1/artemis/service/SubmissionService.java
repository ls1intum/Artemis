package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.GenericSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public abstract class SubmissionService {

    protected SubmissionRepository submissionRepository;

    private UserService userService;

    protected AuthorizationCheckService authCheckService;

    protected ResultRepository resultRepository;

    protected ParticipationService participationService;

    public SubmissionService(SubmissionRepository submissionRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            ParticipationService participationService) {
        this.submissionRepository = submissionRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
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
                    StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
                    studentParticipation.setStudent(null);
                }
            }
        }
    }

    /**
     * Maps abstract Submission type to concrete submission and assigns result.
     * @param result result that will be assigned to concrete submission
     * @param concreteSubmission concrete submission that will be mapped from abstract submission
     * @param <T> concrete submission type
     * @return concrete submission of type T
     */
    public <T extends Submission> T mapAbstractToConcreteSubmission(Result result, T concreteSubmission) {
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
     * @param submissionRepository concrete submission repository
     * @return the newly created result
     */
    public Result setNewResult(Submission submission, GenericSubmissionRepository submissionRepository) {
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
     * @param <T> concrete submission type
     * @return submission with result details hidden
     */
    public <T extends Submission> T hideResultDetails(T submission, Exercise exercise) {
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
}
