package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingAssessmentService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/** REST controller for managing ProgrammingAssessment. */
@RestController
@RequestMapping("/api")
public class ProgrammingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private static final String ENTITY_NAME = "programmingAssessment";

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final LtiService ltiService;

    private final StudentParticipationRepository studentParticipationRepository;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ProgrammingAssessmentService programmingAssessmentService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository, ExamService examService,
            WebsocketMessagingService messagingService, LtiService ltiService, StudentParticipationRepository studentParticipationRepository,
            ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, SingleUserNotificationService singleUserNotificationService) {
        super(authCheckService, userRepository, exerciseRepository, programmingAssessmentService, resultRepository, examService, messagingService, exampleSubmissionRepository,
                submissionRepository, singleUserNotificationService);
        this.programmingAssessmentService = programmingAssessmentService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.ltiService = ltiService;
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
            throw new AccessForbiddenException();
        }

        Result result = programmingAssessmentService.updateAssessmentAfterComplaint(programmingSubmission.getLatestResult(), programmingExercise, assessmentUpdate);
        // make sure the submission is reconnected with the result to prevent problems when the object is used for other calls in the client
        result.setSubmission(programmingSubmission);
        // remove circular dependencies if the results of the participation are there
        if (result.getParticipation() != null && Hibernate.isInitialized(result.getParticipation().getResults()) && result.getParticipation().getResults() != null) {
            result.getParticipation().setResults(null);
        }

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation studentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
            studentParticipation.filterSensitiveInformation();
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
     * @param participationId the id of the participation that should be sent to the client
     * @param submit       defines if assessment is submitted or saved
     * @param newManualResult    result with list of feedbacks to be saved to the database
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/participations/{participationId}/manual-results")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> saveProgrammingAssessment(@PathVariable Long participationId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestBody Result newManualResult) {
        log.debug("REST request to save a new result : {}", newManualResult);
        final var participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);

        User user = userRepository.getUserWithGroupsAndAuthorities();

        // based on the locking mechanism we take the most recent manual result
        Result existingManualResult = participation.getResults().stream().filter(Result::isManual).max(Comparator.comparing(Result::getId))
                .orElseThrow(() -> new EntityNotFoundException("Manual result for participation with id " + participationId + " does not exist"));

        // prevent that tutors create multiple manual results
        newManualResult.setId(existingManualResult.getId());
        // load assessor
        existingManualResult = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(existingManualResult.getId()).get();

        // make sure that the participation and submission cannot be manipulated on the client side
        newManualResult.setParticipation(participation);
        newManualResult.setSubmission(existingManualResult.getSubmission());

        var programmingExercise = (ProgrammingExercise) participation.getExercise();
        checkAuthorization(programmingExercise, user);

        final boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(programmingExercise, user);
        if (!assessmentService.isAllowedToCreateOrOverrideResult(existingManualResult, programmingExercise, participation, user, isAtLeastInstructor)) {
            log.debug("The user {} is not allowed to override the assessment for the participation {} for User {}", user.getLogin(), participation.getId(), user.getLogin());
            throw new AccessForbiddenException("The user is not allowed to override the assessment");
        }

        if (!programmingExercise.areManualResultsAllowed()) {
            throw new AccessForbiddenException("Creating manual results is disabled for this exercise!");
        }
        if (Boolean.FALSE.equals(newManualResult.isRated())) {
            throw new BadRequestAlertException("Result is not rated", ENTITY_NAME, "resultNotRated");
        }
        if (newManualResult.getScore() == null) {
            throw new BadRequestAlertException("Score is required.", ENTITY_NAME, "scoreNull");
        }
        if (newManualResult.getScore() < 100 && newManualResult.isSuccessful()) {
            throw new BadRequestAlertException("Only result with score 100% can be successful.", ENTITY_NAME, "scoreAndSuccessfulNotMatching");
        }
        // All not automatically generated result must have a detail text
        if (!newManualResult.getFeedbacks().isEmpty() && newManualResult.getFeedbacks().stream()
                .anyMatch(feedback -> feedback.getType() == FeedbackType.MANUAL_UNREFERENCED && feedback.getGradingInstruction() == null && feedback.getDetailText() == null)) {
            // if unreferenced feedback is associated with grading instruction, detail text is not needed
            throw new BadRequestAlertException("In case tutor feedback is present, a feedback detail text is mandatory.", ENTITY_NAME, "feedbackDetailTextNull");
        }
        if (!newManualResult.getFeedbacks().isEmpty() && newManualResult.getFeedbacks().stream().anyMatch(feedback -> feedback.getCredits() == null)) {
            throw new BadRequestAlertException("In case feedback is present, a feedback must contain points.", ENTITY_NAME, "feedbackCreditsNull");
        }

        // TODO: move this logic into a service

        // make sure that the submission cannot be manipulated on the client side
        var submission = (ProgrammingSubmission) existingManualResult.getSubmission();
        newManualResult.setSubmission(submission);
        newManualResult.setHasComplaint(existingManualResult.getHasComplaint().isPresent() && existingManualResult.getHasComplaint().get());
        newManualResult = programmingAssessmentService.saveManualAssessment(newManualResult);

        if (submission.getParticipation() == null) {
            newManualResult.setParticipation(submission.getParticipation());
        }
        Result savedResult = resultRepository.save(newManualResult);
        savedResult.setSubmission(submission);

        if (submit) {
            newManualResult = resultRepository.submitManualAssessment(existingManualResult.getId());
            Optional<User> optionalStudent = ((StudentParticipation) submission.getParticipation()).getStudent();
            if (optionalStudent.isPresent()) {
                singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(programmingExercise, optionalStudent.get(), newManualResult);
            }
        }
        // remove information about the student for tutors to ensure double-blind assessment
        if (!isAtLeastInstructor) {
            newManualResult.getParticipation().filterSensitiveInformation();
        }
        // Note: we always need to report the result over LTI, otherwise it might never become visible in the external system
        ltiService.onNewResult((StudentParticipation) newManualResult.getParticipation());
        if (submit && ((newManualResult.getParticipation()).getExercise().getAssessmentDueDate() == null
                || newManualResult.getParticipation().getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingService.broadcastNewResult(newManualResult.getParticipation(), newManualResult);
        }
        return ResponseEntity.ok(newManualResult);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param participationId  - the id of the participation to the submission
     * @param submissionId - the id of the submission for which the current assessment should be deleted
     * @param resultId     - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @DeleteMapping("/participations/{participationId}/programming-submissions/{submissionId}/results/{resultId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}
