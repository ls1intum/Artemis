package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Generic Resource for Text, Modeling and File Upload Submission REST Controllers
 */
public abstract class GenericSubmissionResource<T extends Submission, E extends Exercise> {

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
    ResponseEntity<T> checkExerciseValidityForStudent(E exercise) {
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
     * Check if exercise is valid and the user (tutor) can access it
     * @param exercise that we want to check
     * @param exerciseType type of the exercise
     * @return either null if exercise is valid or one of the error responses if it is not valid
     */
    public ResponseEntity<T> checkExerciseValidityForTutor(Exercise exercise, Class<E> exerciseType) {
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
        return null;
    }

    /**
     * Remove information about the student from the submissions for tutors to ensure a double-blind assessment
     */
    protected List<T> clearStudentInformation(List<T> submissions, Exercise exercise, User user) {
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            submissions.forEach(submission -> ((StudentParticipation) submission.getParticipation()).setStudent(null));
        }
        return submissions;
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
    protected ResponseEntity<T> getDataForEditor(long participationId, Class<E> exerciseType, Class<T> submissionType, T newSubmission) {
        StudentParticipation participation = participationService.findOneWithEagerSubmissionsAndResults(participationId);
        if (participation == null) {
            return ResponseEntity.notFound()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).build();
        }
        E exercise;
        if (exerciseType.isInstance(participation.getExercise())) {
            exercise = (E) participation.getExercise();
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

        // Students can only see their own file uploads (to prevent cheating). TAs, instructors and admins can see all file uploads.
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
            // only try to get and set the file upload if the fileUploadSubmission existed before
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

}
