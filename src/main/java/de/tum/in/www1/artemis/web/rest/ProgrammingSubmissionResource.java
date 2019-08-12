package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private static final String ENTITY_NAME = "programmingSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    public ProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService, ExerciseService exerciseService,
            ProgrammingExerciseService programmingExerciseService, SimpMessageSendingOperations messagingTemplate, AuthorizationCheckService authCheckService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.exerciseService = exerciseService;
        this.programmingExerciseService = programmingExerciseService;
        this.messagingTemplate = messagingTemplate;
        this.authCheckService = authCheckService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * POST /programming-submissions/:participationId : Notify the application about a new push to the VCS for the participation with Id participationId This API is invoked by the
     * VCS Server at the push of a new commit
     *
     * @param participationId the participationId of the participation the repository is linked to
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was already notified about
     */
    @PostMapping(value = Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}")
    public ResponseEntity<?> notifyPush(@PathVariable("participationId") Long participationId, @RequestBody Object requestBody) {
        log.info("REST request to inform about new commit+push for participation: {}", participationId);

        try {
            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();
            ProgrammingSubmission submission = programmingSubmissionService.notifyPush(participationId, requestBody);
            // Remove unnecessary information from the new submission.
            submission.getParticipation().setExercise(null);
            submission.getParticipation().setSubmissions(null);
            // notify the user via websocket.
            messagingTemplate.convertAndSend("/topic/participation/" + participationId + "/newSubmission", submission);
        }
        catch (IllegalArgumentException ex) {
            log.error(
                    "Exception encountered when trying to extract the commit hash from the request body: processing submission for participation {} failed with request object {}: {}",
                    participationId, requestBody, ex);
            return badRequest();
        }
        catch (IllegalStateException ex) {
            log.error("Tried to create another submission for the same commitHash and participation: processing submission for participation {} failed with request object {}: {}",
                    participationId, requestBody, ex);
            return badRequest();
        }
        catch (EntityNotFoundException ex) {
            log.error("Participation with id {} is not a ProgrammingExerciseParticipation: processing submission for participation {} failed with request object {}: {}",
                    participationId, participationId, requestBody, ex);
            return notFound();
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * Trigger the CI build of the given participation.
     * The build result will be treated as if the user would have done a commit.
     *
     * @param participationId of the participation.
     * @return ok if the participation could be found and has permissions, otherwise forbidden (403) or notFound (404). Will also return notFound if the user's git repository is not available.
     */
    @PostMapping(Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}/trigger-build")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> triggerBuild(@PathVariable Long participationId) {
        Participation participation = programmingExerciseParticipationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            return notFound();
        }
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;
        if (!programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation)) {
            return forbidden();
        }
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.createSubmissionForParticipation(programmingExerciseParticipation, SubmissionType.MANUAL);
        }
        catch (IllegalStateException ex) {
            return notFound();
        }
        // notify the user via websocket.
        messagingTemplate.convertAndSend("/topic/participation/" + participationId + Constants.PROGRAMMING_SUBMISSION_TOPIC, submission);
        continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
        return ResponseEntity.ok().build();
    }

    /**
     * Trigger the CI build of the given participation.
     * The build result will become rated regardless of the due date as the submission type is INSTRUCTOR.
     *
     * @param participationId of the participation.
     * @return ok if the participation could be found and has permissions, otherwise forbidden (403) or notFound (404). Will also return notFound if the user's git repository is not available.
     */
    @PostMapping(Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}/trigger-instructor-build")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> triggerInstructorBuild(@PathVariable Long participationId) {
        Participation participation = programmingExerciseParticipationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            return notFound();
        }
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;
        if (!authCheckService.isAtLeastInstructorForExercise(participation.getExercise())) {
            return forbidden();
        }
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.createSubmissionForParticipation(programmingExerciseParticipation, SubmissionType.INSTRUCTOR);
        }
        catch (IllegalStateException ex) {
            return notFound();
        }
        // notify the user via websocket.
        messagingTemplate.convertAndSend("/topic/participation/" + participationId + Constants.PROGRAMMING_SUBMISSION_TOPIC, submission);
        continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /programming-exercises/test-cases-changed/:exerciseId : informs Artemis about changed test cases for the "id" programmingExercise.
     * 
     * @param exerciseId the id of the programmingExercise where the test cases got changed
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping(Constants.TEST_CASE_CHANGED_PATH + "{exerciseId}")
    public ResponseEntity<Void> testCaseChanged(@PathVariable Long exerciseId, @RequestBody Object requestBody) {
        log.info("REST request to inform about changed test cases of ProgrammingExercise : {}", exerciseId);
        // This is needed as a request using a custom query is made using the ExerciseRepository, but the user is not authenticated
        // as the VCS-server performs the request
        SecurityUtils.setAuthorizationObject();

        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!(exercise instanceof ProgrammingExercise)) {
            log.warn("REST request to inform about changed test cases of non existing ProgrammingExercise : {}", exerciseId);
            return ResponseEntity.notFound().build();
        }

        List<ProgrammingSubmission> submissions = programmingExerciseService.notifyChangedTestCases(exerciseId, requestBody);

        // notify users via websocket.
        for (ProgrammingSubmission submission : submissions) {
            messagingTemplate.convertAndSend("/topic/participation/" + submission.getParticipation().getId() + "/newSubmission", submission);
        }

        return ResponseEntity.ok().build();
    }
}
