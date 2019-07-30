package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
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

    public ProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService, ExerciseService exerciseService,
            ProgrammingExerciseService programmingExerciseService, SimpMessageSendingOperations messagingTemplate) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.exerciseService = exerciseService;
        this.programmingExerciseService = programmingExerciseService;
        this.messagingTemplate = messagingTemplate;
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
            ProgrammingSubmission submission = programmingSubmissionService.notifyPush(participationId, requestBody);
            // notify the user via websocket.
            messagingTemplate.convertAndSend("/topic/participation/" + participationId + "/newSubmission", submission);
        }
        catch (IllegalArgumentException ex) {
            log.error("Exception encountered when trying to extract the commit hash from the request body. Processing submission for participation {} with request object {}: {}",
                    participationId, requestBody, ex);
            return badRequest();
        }
        catch (IllegalStateException ex) {
            log.error("Tried to create another submission for the same commitHash and participation! Processing submission for participation {} with request object {}: {}",
                    participationId, requestBody, ex);
            return badRequest();
        }
        catch (EntityNotFoundException ex) {
            log.error("Participation with id {} is not a ProgrammingExerciseParticipation! Processing submission for participation {} with request object {}: {}", participationId,
                    participationId, requestBody, ex);
            return notFound();
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * POST /programming-exercises/test-cases-changed/:exerciseId : informs Artemis about changed test cases for the "id" programmingExercise. e
     * 
     * @param exerciseId the id of the programmingExercise where the test cases got changed
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping(Constants.TEST_CASE_CHANGED_PATH + "{exerciseId}")
    public ResponseEntity<Void> testCaseChanged(@PathVariable Long exerciseId, @RequestBody Object requestBody) {
        log.info("REST request to inform about changed test cases of ProgrammingExercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!(exercise instanceof ProgrammingExercise)) {
            log.warn("REST request to inform about changed test cases of non existing ProgrammingExercise : {}", exerciseId);
            return ResponseEntity.notFound().build();
        }

        programmingExerciseService.notifyChangedTestCases(exerciseId, requestBody);

        return ResponseEntity.ok().build();
    }
}
