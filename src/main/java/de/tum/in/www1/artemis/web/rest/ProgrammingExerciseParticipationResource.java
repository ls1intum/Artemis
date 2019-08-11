package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@RestController
@RequestMapping("/api")
public class ProgrammingExerciseParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationResource.class);

    private ParticipationService participationService;

    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private ResultService resultService;

    private ProgrammingSubmissionService submissionService;

    private Optional<ContinuousIntegrationService> continuousIntegrationService;

    private SimpMessageSendingOperations messagingTemplate;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ParticipationService participationService,
            ResultService resultService, ProgrammingSubmissionService submissionService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            SimpMessageSendingOperations messagingTemplate) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.participationService = participationService;
        this.resultService = resultService;
        this.submissionService = submissionService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Get the given student participation with its latest result and feedbacks.
     *
     * @return the ResponseEntity with status 200 (OK) and the participation with its latest result in the body.
     */
    @GetMapping(value = "/programming-exercise-participations/{participationId}/student-participation-with-latest-result-and-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipationWithLatestResultForStudentParticipation(@PathVariable Long participationId) {
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseParticipationService
                .findStudentParticipationWithLatestResultAndFeedbacks(participationId);
        if (!participation.isPresent()) {
            return notFound();
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(participation.get())) {
            return forbidden();
        }
        return ResponseEntity.ok(participation.get());
    }

    /**
     * Get the latest result for a given programming exercise participation including it's result.
     *
     * @return the ResponseEntity with status 200 (OK) and the latest result with feedbacks in its body.
     */
    @GetMapping(value = "/programming-exercise-participations/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacksForProgrammingExerciseParticipation(@PathVariable Long participationId) {
        Participation participation;
        try {
            participation = participationService.findOne(participationId);
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        if (participation instanceof ProgrammingExerciseParticipation) {
            return getLatestResultWithFeedbacks((ProgrammingExerciseParticipation) participation);
        }
        else {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * GET /programming-exercise-participation/:id/latest-pending-submission : get the latest pending submission for the participation.
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @return the ResponseEntity with the last pending submission if it exists or null with status Ok (200). Will return notFound (404) if there is no participation for the given id and forbidden (403) if the user is not allowed to access the participation.
     */
    @GetMapping("/programming-exercise-participations/{participationId}/latest-pending-submission")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingSubmission> getLatestPendingSubmission(@PathVariable Long participationId) {
        ProgrammingSubmission submission;
        try {
            submission = submissionService.getLatestPendingSubmission(participationId);
        }
        catch (EntityNotFoundException | IllegalArgumentException ex) {
            return notFound();
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        return ResponseEntity.ok(submission);
    }

    /**
     * Trigger the CI build of the given participation.
     *
     * @param participationId of the participation.
     * @return ok if the participation could be found and has permissions, otherwise forbidden (403) or notFound (404).
     */
    @PostMapping("/programming-exercise-participations/{participationId}/trigger-build")
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
        ProgrammingSubmission submission = submissionService.createManualSubmission(programmingExerciseParticipation);
        // notify the user via websocket.
        messagingTemplate.convertAndSend("/topic/participation/" + participationId + "/newSubmission", submission);
        continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
        return ResponseEntity.ok().build();
    }

    /**
     * Util method for retrieving the latest result with feedbacks of participation. Generates the appropriate response type (ok, forbidden, notFound).
     *
     * @param participation
     * @return the appropriate ResponseEntity for the result request.
     */
    private ResponseEntity<Result> getLatestResultWithFeedbacks(ProgrammingExerciseParticipation participation) {
        if (!programmingExerciseParticipationService.canAccessParticipation(participation)) {
            return forbidden();
        }
        Result result;
        try {
            result = resultService.findLatestResultWithFeedbacksForParticipation(participation.getId());
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        return ResponseEntity.ok(result);
    }
}
