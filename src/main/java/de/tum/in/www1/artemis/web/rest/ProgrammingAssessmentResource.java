package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;
import java.util.Comparator;

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
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/** REST controller for managing ProgrammingAssessment. */
@RestController
@RequestMapping("/api")
public class ProgrammingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private static final String ENTITY_NAME = "programmingAssessment";

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final LtiService ltiService;

    private final ParticipationService participationService;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ProgrammingAssessmentService programmingAssessmentService,
            ProgrammingSubmissionService programmingSubmissionService, ExerciseService exerciseService, ResultRepository resultRepository, ExamService examService,
            WebsocketMessagingService messagingService, LtiService ltiService, ParticipationService participationService, ExampleSubmissionService exampleSubmissionService) {
        super(authCheckService, userService, exerciseService, programmingSubmissionService, programmingAssessmentService, resultRepository, examService, messagingService,
                exampleSubmissionService);
        this.programmingAssessmentService = programmingAssessmentService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.ltiService = ltiService;
        this.participationService = participationService;
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
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateProgrammingManualResultAfterComplaint(@RequestBody AssessmentUpdate assessmentUpdate, @PathVariable long submissionId) {
        log.debug("REST request to update the assessment of manual result for submission {} after complaint.", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        ProgrammingSubmission programmingSubmission = programmingSubmissionService.findByIdWithEagerResultsFeedbacksAssessor(submissionId);
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
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Save or submit feedback for programming exercise.
     *
     * @param participationId the id of the participation that should be sent to the client
     * @param submit       defines if assessment is submitted or saved
     * @param newManualResult    result with list of feedbacks to be saved to the database
     * @param correctionRound the correction round of the assessment
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/participations/{participationId}/manual-results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: either remove correctionRound or actually use it
    public ResponseEntity<Result> saveProgrammingAssessment(@PathVariable Long participationId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestBody Result newManualResult) {
        log.debug("REST request to save a new result : {}", newManualResult);
        final var participation = participationService.findOneWithEagerSubmissionsResultsFeedback(participationId);

        User user = userService.getUserWithGroupsAndAuthorities();

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

        final var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(programmingExercise, user);
        if (!assessmentService.isAllowedToCreateOrOverrideResult(existingManualResult, programmingExercise, participation, user, isAtLeastInstructor)) {
            log.debug("The user " + user.getLogin() + " is not allowed to override the assessment for the participation " + participation.getId() + " for User " + user.getLogin());
            return forbidden("assessment", "assessmentSaveNotAllowed", "The user is not allowed to override the assessment");
        }

        if (!programmingExercise.areManualResultsAllowed()) {
            return forbidden();
        }

        if (Boolean.FALSE.equals(newManualResult.isRated())) {
            throw new BadRequestAlertException("Result is not rated", ENTITY_NAME, "resultNotRated");
        }
        if (newManualResult.getResultString() == null) {
            throw new BadRequestAlertException("Result string is required.", ENTITY_NAME, "resultStringNull");
        }
        else if (newManualResult.getResultString().length() > 255) {
            throw new BadRequestAlertException("Result string is too long.", ENTITY_NAME, "resultStringNull");
        }
        else if (newManualResult.getScore() == null) {
            throw new BadRequestAlertException("Score is required.", ENTITY_NAME, "scoreNull");
        }
        else if (newManualResult.getScore() < 100 && newManualResult.isSuccessful()) {
            throw new BadRequestAlertException("Only result with score 100% can be successful.", ENTITY_NAME, "scoreAndSuccessfulNotMatching");
        }
        // All not automatically generated result must have a detail text
        else if (!newManualResult.getFeedbacks().isEmpty()
                && newManualResult.getFeedbacks().stream().anyMatch(feedback -> feedback.getType() != FeedbackType.AUTOMATIC && feedback.getDetailText() == null)) {
            throw new BadRequestAlertException("In case tutor feedback is present, a feedback detail text is mandatory.", ENTITY_NAME, "feedbackDetailTextNull");
        }
        else if (!newManualResult.getFeedbacks().isEmpty() && newManualResult.getFeedbacks().stream().anyMatch(feedback -> feedback.getCredits() == null)) {
            throw new BadRequestAlertException("In case feedback is present, a feedback must contain points.", ENTITY_NAME, "feedbackCreditsNull");
        }

        // TODO: move this logic into a service

        // make sure that the submission cannot be manipulated on the client side
        var submission = (ProgrammingSubmission) existingManualResult.getSubmission();
        newManualResult.setSubmission(submission);
        newManualResult = programmingAssessmentService.saveManualAssessment(newManualResult);

        if (submission.getParticipation() == null) {
            newManualResult.setParticipation(submission.getParticipation());
        }
        var savedResult = resultRepository.save(newManualResult);
        savedResult.setSubmission(submission);

        if (submit) {
            newManualResult = programmingAssessmentService.submitManualAssessment(existingManualResult.getId());
        }
        // remove information about the student for tutors to ensure double-blind assessment
        if (!isAtLeastInstructor) {
            ((StudentParticipation) newManualResult.getParticipation()).filterSensitiveInformation();
        }
        // Note: we always need to report the result over LTI, otherwise it might never become visible in the external system
        ltiService.onNewResult((StudentParticipation) newManualResult.getParticipation());
        if (submit && ((newManualResult.getParticipation()).getExercise().getAssessmentDueDate() == null
                || newManualResult.getParticipation().getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingService.broadcastNewResult(newManualResult.getParticipation(), newManualResult);
        }
        return ResponseEntity.ok(newManualResult);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}
