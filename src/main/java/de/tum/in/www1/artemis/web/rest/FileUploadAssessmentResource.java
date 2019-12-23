package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing FileUploadAssessment.
 */
@RestController
@RequestMapping("/api")
public class FileUploadAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadAssessmentResource.class);

    private static final String ENTITY_NAME = "fileUploadAssessment";

    private final FileUploadAssessmentService fileUploadAssessmentService;

    private final FileUploadExerciseService fileUploadExerciseService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final WebsocketMessagingService messagingService;

    public FileUploadAssessmentResource(AuthorizationCheckService authCheckService, FileUploadAssessmentService fileUploadAssessmentService, UserService userService,
            FileUploadExerciseService fileUploadExerciseService, FileUploadSubmissionService fileUploadSubmissionService, WebsocketMessagingService messagingService) {
        super(authCheckService, userService);
        this.fileUploadAssessmentService = fileUploadAssessmentService;
        this.fileUploadExerciseService = fileUploadExerciseService;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.messagingService = messagingService;
    }

    /**
     * Get the result of the file upload submission with the given id. Returns a 403 Forbidden response if the user is not allowed to retrieve the assessment. The user is not allowed
     * to retrieve the assessment if he/she is not a student of the corresponding course, the submission is not his/her submission, the result is not finished or the assessment due date of
     * the corresponding exercise is in the future (or not set).
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the submission with the given id
     */
    @GetMapping("/file-upload-submissions/{submissionId}/result")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        log.debug("REST request to get assessment for submission with id {}", submissionId);
        FileUploadSubmission submission = fileUploadSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        Exercise exercise = participation.getExercise();

        Result result = submission.getResult();
        if (result == null) {
            return notFound();
        }

        if (!authCheckService.isUserAllowedToGetResult(exercise, participation, result)) {
            return forbidden();
        }

        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            exercise.filterSensitiveInformation();
            result.setAssessor(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Save or submit feedback for file upload exercise.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @param submit       defines if assessment is submitted or saved
     * @param feedbacks    list of feedbacks to be saved to the database
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/file-upload-submissions/{submissionId}/feedback")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> saveFileUploadAssessment(@PathVariable Long submissionId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestBody List<Feedback> feedbacks) {
        FileUploadSubmission fileUploadSubmission = fileUploadSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        FileUploadExercise fileUploadExercise = fileUploadExerciseService.findOne(exerciseId);
        checkAuthorization(fileUploadExercise, userService.getUserWithGroupsAndAuthorities());

        Result result = fileUploadAssessmentService.saveAssessment(fileUploadSubmission, feedbacks, fileUploadExercise);
        if (submit) {
            result = fileUploadAssessmentService.submitAssessment(result.getId(), fileUploadExercise, fileUploadSubmission.getSubmissionDate());
        }
        // remove information about the student for tutors to ensure double-blind assessment
        if (!authCheckService.isAtLeastInstructorForExercise(fileUploadExercise)) {
            ((StudentParticipation) result.getParticipation()).setStudent(null);
        }
        if (submit && ((result.getParticipation()).getExercise().getAssessmentDueDate() == null
                || (result.getParticipation()).getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingService.broadcastNewResult(result.getParticipation(), result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/file-upload-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateFileUploadAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdate assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        FileUploadSubmission fileUploadSubmission = fileUploadSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        FileUploadExercise fileUploadExercise = fileUploadExerciseService.findOne(exerciseId);
        checkAuthorization(fileUploadExercise, user);

        Result result = fileUploadAssessmentService.updateAssessmentAfterComplaint(fileUploadSubmission.getResult(), fileUploadExercise, assessmentUpdate);

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(fileUploadExercise)) {
            ((StudentParticipation) result.getParticipation()).setStudent(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("/file-upload-submissions/{submissionId}/cancel-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity cancelAssessment(@PathVariable Long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        FileUploadSubmission fileUploadSubmission = fileUploadSubmissionService.findOneWithEagerResult(submissionId);
        if (fileUploadSubmission.getResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        if (!userService.getUser().getId().equals(fileUploadSubmission.getResult().getAssessor().getId())) {
            // you cannot cancel the assessment of other tutors
            return forbidden();
        }
        fileUploadAssessmentService.cancelAssessmentOfSubmission(fileUploadSubmission);
        return ResponseEntity.ok().build();
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}
