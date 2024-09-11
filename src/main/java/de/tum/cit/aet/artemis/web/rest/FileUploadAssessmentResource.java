package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import de.tum.cit.aet.artemis.exercise.web.AssessmentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;
import de.tum.cit.aet.artemis.web.rest.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.web.rest.dto.FileUploadAssessmentDTO;

/**
 * REST controller for managing FileUploadAssessment.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class FileUploadAssessmentResource extends AssessmentResource {

    private static final Logger log = LoggerFactory.getLogger(FileUploadAssessmentResource.class);

    private static final String ENTITY_NAME = "fileUploadAssessment";

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    public FileUploadAssessmentResource(AuthorizationCheckService authCheckService, AssessmentService assessmentService, UserRepository userRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository, ExerciseRepository exerciseRepository,
            ResultRepository resultRepository, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository) {
        super(authCheckService, userRepository, exerciseRepository, assessmentService, resultRepository, exampleSubmissionRepository, submissionRepository);
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    /**
     * Get the result of the file upload submission with the given id. See {@link AssessmentResource#getAssessmentBySubmissionId}.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the assessment of the given submission
     */
    @Override
    @GetMapping("file-upload-submissions/{submissionId}/result")
    @EnforceAtLeastStudent
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        return super.getAssessmentBySubmissionId(submissionId);
    }

    /**
     * PUT file-upload-submissions/:submissionId/feedback : save or submit manual assessment for file upload exercise. See {@link AssessmentResource#saveAssessment}.
     *
     * @param submissionId         the id of the submission that should be sent to the client
     * @param submit               defines if assessment is submitted or saved
     * @param fileUploadAssessment the assessment containing both feedback and the assessment note, if it exists
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("file-upload-submissions/{submissionId}/feedback")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> saveFileUploadAssessment(@PathVariable Long submissionId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestBody FileUploadAssessmentDTO fileUploadAssessment) {
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
        // if a result exists, we want to override it, otherwise create a new one
        var resultId = submission.getLatestResult() != null ? submission.getLatestResult().getId() : null;
        return super.saveAssessment(submission, submit, fileUploadAssessment.feedbacks(), resultId, fileUploadAssessment.assessmentNote());
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("file-upload-submissions/{submissionId}/assessment-after-complaint")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> updateFileUploadAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdateDTO assessmentUpdate) {
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
    @PutMapping("file-upload-submissions/{submissionId}/cancel-assessment")
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
    @Override
    @DeleteMapping("participations/{participationId}/file-upload-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }
}
