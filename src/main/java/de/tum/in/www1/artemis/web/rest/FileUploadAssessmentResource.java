package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.websocket.ResultWebsocketService;

/**
 * REST controller for managing FileUploadAssessment.
 */
@RestController
@RequestMapping("/api")
public class FileUploadAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadAssessmentResource.class);

    private static final String ENTITY_NAME = "fileUploadAssessment";

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public FileUploadAssessmentResource(AuthorizationCheckService authCheckService, AssessmentService assessmentService, UserRepository userRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository, ResultWebsocketService resultWebsocketService,
            ExerciseRepository exerciseRepository, ResultRepository resultRepository, ExamService examService, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, SingleUserNotificationService singleUserNotificationService) {
        super(authCheckService, userRepository, exerciseRepository, assessmentService, resultRepository, examService, resultWebsocketService, exampleSubmissionRepository,
                submissionRepository, singleUserNotificationService);
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    /**
     * Get the result of the file upload submission with the given id. See {@link AssessmentResource#getAssessmentBySubmissionId}.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the assessment of the given submission
     */
    @GetMapping("/file-upload-submissions/{submissionId}/result")
    @EnforceAtLeastStudent
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        return super.getAssessmentBySubmissionId(submissionId);
    }

    /**
     * PUT file-upload-submissions/:submissionId/feedback : save or submit manual assessment for file upload exercise. See {@link AssessmentResource#saveAssessment}.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @param submit       defines if assessment is submitted or saved
     * @param feedbacks    list of feedbacks to be saved to the database
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/file-upload-submissions/{submissionId}/feedback")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> saveFileUploadAssessment(@PathVariable Long submissionId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestBody List<Feedback> feedbacks) {
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
        // if a result exists, we want to override it, otherwise create a new one
        var resultId = submission.getLatestResult() != null ? submission.getLatestResult().getId() : null;
        return super.saveAssessment(submission, submit, feedbacks, resultId);
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
    @EnforceAtLeastTutor
    public ResponseEntity<Result> updateFileUploadAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdate assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        FileUploadSubmission fileUploadSubmission = fileUploadSubmissionRepository.findByIdWithEagerResultAndAssessorAndFeedbackElseThrow(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        FileUploadExercise fileUploadExercise = fileUploadExerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(fileUploadExercise, user);

        Result result = assessmentService.updateAssessmentAfterComplaint(fileUploadSubmission.getLatestResult(), fileUploadExercise, assessmentUpdate);

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(fileUploadExercise)) {
            ((StudentParticipation) result.getParticipation()).setParticipant(null);
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
    @EnforceAtLeastTutor
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param participationId - the id of the participation to the submission
     * @param submissionId    - the id of the submission for which the current assessment should be deleted
     * @param resultId        - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @DeleteMapping("/participations/{participationId}/file-upload-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}
