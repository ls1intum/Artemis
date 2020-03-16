package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class SubmissionService {

    private final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    protected SubmissionRepository submissionRepository;

    protected ResultRepository resultRepository;

    private UserService userService;

    protected AuthorizationCheckService authCheckService;

    public SubmissionService(SubmissionRepository submissionRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository) {
        this.submissionRepository = submissionRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
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
     * Get the submission with the given id from the database. The submission is loaded together with its result and the assessor. Throws an EntityNotFoundException if no
     * submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with the given id
     */
    public Submission findOneWithEagerResult(long submissionId) {
        return submissionRepository.findWithEagerResultById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * Count number of submissions for course. Only submissions for Text, Modeling and File Upload exercises are included.
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    public long countSubmissionsForCourse(long courseId) {
        return submissionRepository.countByCourseIdSubmittedBeforeDueDate(courseId);
    }

    /**
     * Count number of submissions for exercise.
     * @param exerciseId the exercise id we are interested in
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true and the submission date before the exercise due date, or no
     *         exercise due date at all
     */
    public long countSubmissionsForExercise(long exerciseId) {
        return submissionRepository.countByExerciseIdSubmittedBeforeDueDate(exerciseId);
    }

    /**
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client.
     * ***IMPORTANT***: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     *
     * @param submission Submission to be modified.
     * @param user the currently logged in user which is used for hiding specific submission details based on instructor and teaching assistant rights
     */
    public void hideDetails(Submission submission, User user) {
        // do not send old submissions or old results to the client
        if (submission.getParticipation() != null) {
            submission.getParticipation().setSubmissions(null);
            submission.getParticipation().setResults(null);

            Exercise exercise = submission.getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                    exercise.filterSensitiveInformation();
                    submission.setResult(null);
                }
                // remove information about the student from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
                    StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
                    studentParticipation.filterSensitiveInformation();
                }
            }
        }
    }

    /**
     * Creates a new Result object, assigns it to the given submission and stores the changes to the database.
     * @param submission the submission for which a new result should be created
     * @return the newly created result
     */
    public Result setNewResult(Submission submission) {
        Result result = new Result();
        result.setSubmission(submission);
        submission.setResult(result);
        result.setParticipation(submission.getParticipation());
        result = resultRepository.save(result);
        submissionRepository.save(submission);
        return result;
    }

    /**
     * Soft lock the submission to prevent other tutors from receiving and assessing it. We set the assessor and save the result to soft lock the assessment in the client, i.e. the client will not allow
     * tutors to assess a submission when an assessor is already assigned. If no result exists for this submission we create one first.
     *
     * @param submission the submission to lock
     */
    protected Result lockSubmission(Submission submission) {
        Result result = submission.getResult();
        if (result == null) {
            result = setNewResult(submission);
        }

        if (result.getAssessor() == null) {
            result.setAssessor(userService.getUser());
        }

        result.setAssessmentType(AssessmentType.MANUAL);
        result = resultRepository.save(result);
        log.debug("Assessment locked with result id: " + result.getId() + " for assessor: " + result.getAssessor().getName());
        return result;
    }
}
