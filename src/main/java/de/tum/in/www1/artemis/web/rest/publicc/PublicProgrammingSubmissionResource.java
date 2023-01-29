package de.tum.in.www1.artemis.web.rest.publicc;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingTriggerService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for receiving updates for a ProgrammingSubmission.
 */
@RestController
@RequestMapping("api/public/")
public class PublicProgrammingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(PublicProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingTriggerService programmingTriggerService;

    public PublicProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService, ProgrammingMessagingService programmingMessagingService,
            Optional<VersionControlService> versionControlService, ProgrammingTriggerService programmingTriggerService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.versionControlService = versionControlService;
        this.programmingTriggerService = programmingTriggerService;
    }

    /**
     * POST /programming-submissions/:participationId : Notify the application about a new push to the VCS for the participation with Id participationId This API is invoked by the
     * VCS Server at the push of a new commit
     *
     * @param participationId the participationId of the participation the repository is linked to
     * @param requestBody the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was already notified about
     */
    @PostMapping("programming-submissions/{participationId}")
    @EnforceNothing
    public ResponseEntity<?> processNewProgrammingSubmission(@PathVariable("participationId") Long participationId, @RequestBody Object requestBody) {
        log.debug("REST request to inform about new commit+push for participation: {}", participationId);

        try {
            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore, a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();
            ProgrammingSubmission submission = programmingSubmissionService.processNewProgrammingSubmission(participationId, requestBody);
            // Remove unnecessary information from the new submission.
            submission.getParticipation().setSubmissions(null);
            programmingMessagingService.notifyUserAboutSubmission(submission);
        }
        catch (IllegalArgumentException ex) {
            log.error(
                    "Exception encountered when trying to extract the commit hash from the request body: processing submission for participation {} failed with request object {}: {}",
                    participationId, requestBody, ex);
            throw new BadRequestAlertException("Exception encountered when trying to extract the commit hash from the request body " + ex.getMessage(), "ProgrammingSubmission",
                    "extractCommitHashNotPossible");
        }
        catch (IllegalStateException ex) {
            if (!ex.getMessage().contains("empty setup commit")) {
                log.warn("Processing submission for participation {} failed: {}", participationId, ex.getMessage());
            }
            // we return ok, because the problem is not on the side of the VCS Server and we don't want the VCS Server to kill the webhook if there are too many errors
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        catch (EntityNotFoundException ex) {
            log.error("Participation with id {} is not a ProgrammingExerciseParticipation: processing submission for participation {} failed with request object {}: {}",
                    participationId, participationId, requestBody, ex);
            throw ex;
        }

        // Note: we should not really return status code other than 200, because Bitbucket might kill the webhook, if there are too many errors
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * POST /programming-exercises/test-cases-changed/:exerciseId : informs Artemis about changed test cases for the "exerciseId" programmingExercise.
     * <p>
     * Problem with legacy programming exercises:
     * The repositories (solution, template, student) are built automatically when a commit is pushed into the test repository.
     * We have removed this trigger for newly created exercises, but can't remove it from legacy ones.
     * This means that legacy exercises will trigger the repositories to be built, but we won't create submissions here anymore.
     * Therefore, incoming build results will have to create new submissions with SubmissionType.OTHER.
     *
     * @param exerciseId the id of the programmingExercise where the test cases got changed
     * @param requestBody the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("programming-exercises/test-cases-changed/{exerciseId}")
    @EnforceNothing
    public ResponseEntity<Void> testCaseChanged(@PathVariable Long exerciseId, @RequestBody Object requestBody) {
        log.info("REST request to inform about changed test cases of ProgrammingExercise : {}", exerciseId);
        // This is needed as a request using a custom query is made using the ExerciseRepository, but the user is not authenticated
        // as the VCS-server performs the request
        SecurityUtils.setAuthorizationObject();

        String lastCommitHash = null;
        try {
            Commit commit = versionControlService.get().getLastCommitDetails(requestBody);
            lastCommitHash = commit.getCommitHash();
            log.info("create new programmingSubmission with commitHash: {} for exercise {}", lastCommitHash, exerciseId);
        }
        catch (Exception ex) {
            log.debug(
                    "Commit hash could not be parsed from test repository from exercise {}, the submission will be created with the latest commit hash of the solution repository.",
                    exerciseId, ex);
        }

        // When the tests were changed, the solution repository will be built. We therefore create a submission for the solution participation.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exerciseId, lastCommitHash);
        programmingMessagingService.notifyUserAboutSubmission(submission);
        // It is possible that there is now a new test case or an old one has been removed. We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChanged(exerciseId, true);

        return ResponseEntity.ok().build();
    }
}
