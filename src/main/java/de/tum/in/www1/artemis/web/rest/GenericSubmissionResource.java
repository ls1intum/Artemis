package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Generic Resource for Text, Modeling, Programming and File Upload Submission REST Controllers
 */
public abstract class GenericSubmissionResource<T extends Submission> {

    @Value("${jhipster.clientApp.name}")
    protected String applicationName;

    private static final String ENTITY_NAME = "genericSubmission";

    protected final CourseService courseService;

    protected final AuthorizationCheckService authCheckService;

    protected final ExerciseService exerciseService;

    protected final UserService userService;

    protected final ParticipationService participationService;

    public GenericSubmissionResource(CourseService courseService, AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService,
            ParticipationService participationService) {
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
    }

    /**
     * Check if exercise is valid, has course and the user (student) can access it
     * @param exercise that we want to check
     * @return either null if exercise is valid or one of the error responses if it is not valid
     */
    final ResponseEntity<T> checkExerciseValidityForStudent(Exercise exercise) {
        if (exercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        final Course course = courseService.findOne(exercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "courseNotFound", "The course belonging to this file upload exercise does not exist"))
                    .body(null);
        }
        if (!authCheckService.isAtLeastStudentInCourse(course, userService.getUserWithGroupsAndAuthorities())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }

    /**
     * Check if exercise is valid, the user (tutor) can access it and the user didn't reach the limit of submission locks
     * @param exercise that we want to check
     * @param exerciseType type of the exercise
     * @param submissionService concrete submission service that is used to check lock limit
     * @return either null if exercise is valid or one of the error responses if it is not valid
     */
    final <E extends Exercise> ResponseEntity<T> checkExerciseValidityForTutor(Exercise exercise, Class<E> exerciseType, SubmissionService<T> submissionService) {
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        if (!(exerciseType.isInstance(exercise))) {
            return badRequest();
        }

        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())) {
            return notFound();
        }

        // Check if the limit of simultaneously locked submissions has been reached
        submissionService.checkSubmissionLockLimit(exercise.getCourse().getId());
        return null;
    }

    /**
     * Returns the data needed for the editor, which includes the participation, submission with answer if existing and the assessments if the submission was already
     * submitted.
     *
     * @param participationId the id of the participation for which to find the data for the editor
     * @param exerciseType type of the exercise for which we take the data
     * @param submissionType type of the submission for which we take the data
     * @param newSubmission new instance of concrete submission which can be needed if we don't find submission
     * @return the ResponseEntity with the participation as body
     */
    final <E extends Exercise> ResponseEntity<T> getDataForEditor(long participationId, Class<E> exerciseType, Class<T> submissionType, T newSubmission) {
        final StudentParticipation participation = participationService.findOneWithEagerSubmissionsAndResults(participationId);
        if (participation == null) {
            return ResponseEntity.notFound()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).build();
        }
        E exercise;
        if (exerciseType.isInstance(participation.getExercise())) {
            exercise = exerciseType.cast(participation.getExercise());
            if (exercise == null) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "exercise", "exerciseEmpty", "The exercise belonging to the participation is null."))
                        .body(null);
            }

            // make sure sensitive information are not sent to the client
            exercise.filterSensitiveInformation();
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadExercise", "wrongExerciseType",
                    "The exercise of the participation is not a file upload exercise.")).body(null);
        }

        // Students can only see their own submissions (to prevent cheating). TAs, instructors and admins can see all submissions.
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(exercise))) {
            return forbidden();
        }

        Optional<T> optionalSubmission = participation.findLatestSubmissionOfType(submissionType);
        T submission;
        if (optionalSubmission.isEmpty()) {
            // this should never happen as the submission is initialized along with the participation when the exercise is started
            submission = newSubmission;
            submission.setParticipation(participation);
        }
        else {
            submission = optionalSubmission.get();
        }

        // make sure only the latest submission and latest result is sent to the client
        participation.setSubmissions(null);
        participation.setResults(null);

        if (submission.getResult() != null) {
            // do not send the result to the client if the assessment is not finished
            if (submission.getResult().getCompletionDate() == null || submission.getResult().getAssessor() == null) {
                submission.setResult(null);
            }
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                submission.getResult().setAssessor(null);
            }
        }
        return ResponseEntity.ok(submission);
    }

    /**
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client.
     * ***IMPORTANT***: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     *
     * @param submission from which we want to remove sensitive information before sending it to client
     * @param user current user
     */
    public void hideDetails(Submission submission, User user) {
        var participation = submission.getParticipation();
        // do not send old submissions or old results to the client
        if (participation != null) {
            participation.setSubmissions(null);
            participation.setResults(null);

            Exercise exercise = participation.getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                    exercise.filterSensitiveInformation();
                    submission.setResult(null);
                }
                // remove information about the student from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
                    ((StudentParticipation) participation).filterSensitiveInformation();
                }
            }
        }
    }

    /**
     * Get all submissions by exercise id.
     *
     * @param exerciseId id of the exercise for which the modeling submission should be returned
     * @param submittedOnly if true, it returns only submission with submitted flag set to true
     * @param assessedByTutor if true, it returns only the submissions which are assessed by the current user as a tutor
     * @param submissionService concrete submission service used to get the submissions from the database
     * @param submissionType type of the submission we want to get
     * @return response with a list of submissions
     */
    final ResponseEntity<List<T>> getAllSubmissions(long exerciseId, boolean assessedByTutor, boolean submittedOnly, SubmissionService<T> submissionService,
            Class<T> submissionType) {
        final Exercise exercise = exerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();

        if (assessedByTutor) {
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                throw new AccessForbiddenException("You are not allowed to access this resource");
            }
        }
        else if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        final List<T> submissions;
        if (assessedByTutor) {
            submissions = submissionService.getAllSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            submissions = submissionService.getSubmissions(exerciseId, submittedOnly, submissionType);
        }

        // tutors should not see information about the student of a submission
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            submissions.forEach(submission -> hideDetails(submission, user));
        }

        // remove unnecessary data from the REST response
        submissions.forEach(submission -> {
            if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
                submission.getParticipation().setExercise(null);
            }
        });
        return ResponseEntity.ok().body(submissions);
    }
}
