package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.programming.ProgrammingAssessmentService;

/** REST controller for managing ProgrammingAssessment. */
@RestController
@RequestMapping("/api")
public class ProgrammingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private static final String ENTITY_NAME = "programmingAssessment";

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ProgrammingAssessmentService programmingAssessmentService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository, ExamService examService,
            WebsocketMessagingService messagingService, LtiService ltiService, StudentParticipationRepository studentParticipationRepository,
            ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository) {
        super(authCheckService, userRepository, exerciseRepository, programmingAssessmentService, resultRepository, examService, messagingService, exampleSubmissionRepository,
                submissionRepository, ltiService);
        this.programmingAssessmentService = programmingAssessmentService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the programing assessment update
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/programming-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> updateProgrammingManualResultAfterComplaint(@RequestBody AssessmentUpdate assessmentUpdate, @PathVariable long submissionId) {
        log.debug("REST request to update the assessment of manual result for submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessor(submissionId);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) programmingSubmission.getParticipation().getExercise();
        checkAuthorization(programmingExercise, user);
        if (!programmingExercise.areManualResultsAllowed()) {
            return forbidden();
        }

        Result result = programmingAssessmentService.updateAssessmentAfterComplaint(programmingSubmission.getLatestResult(), programmingExercise, assessmentUpdate);
        // make sure the submission is reconnected with the result to prevent problems when the object is used for other calls in the client
        result.setSubmission(programmingSubmission);
        // remove circular dependencies if the results of the participation are there
        if (result.getParticipation() != null && Hibernate.isInitialized(result.getParticipation().getResults()) && result.getParticipation().getResults() != null) {
            result.getParticipation().setResults(null);
        }

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
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
    @PutMapping("/programming-submissions/{submissionId}/cancel-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Save or submit feedback for programming exercise.
     *
     * @param submissionId the id of the submission
     * @param submit       defines if assessment is submitted or saved
     * @param feedbacks    list of feedbacks to be saved to the database
     * @param resultId      id of the manual result
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/programming-submissions/{submissionId}/result/{resultId}/assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> saveProgrammingAssessment(@PathVariable long submissionId, @PathVariable long resultId,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody List<Feedback> feedbacks) {

        Submission submission = submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
        // Resultstring has to be calculated on server as well as score
        // add that result has always to be rated
        // if score of 100% then set also result successful
        return super.saveAssessment(submission, submit, feedbacks, resultId);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param submissionId - the id of the submission for which the current assessment should be deleted
     * @param resultId     - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @DeleteMapping("/programming-submissions/{submissionId}/delete/{resultId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(submissionId, resultId);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}
