package de.tum.cit.aet.artemis.web.rest.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.domain.Commit;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.participation.Participation;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for receiving updates for a ProgrammingSubmission.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicProgrammingSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(PublicProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ParticipationRepository participationRepository;

    public PublicProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService, ProgrammingMessagingService programmingMessagingService,
            Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService,
            ProgrammingTriggerService programmingTriggerService, ParticipationRepository participationRepository) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingTriggerService = programmingTriggerService;
        this.participationRepository = participationRepository;
    }

    /**
     * POST /programming-submissions/:participationId : Notify the application about a new push to the VCS for the participation with Id participationId This API is invoked by the
     * VCS Server at the push of a new commit
     *
     * @param participationId the participationId of the participation the repository is linked to
     * @param requestBody     the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was already notified about
     */
    @PostMapping("programming-submissions/{participationId}")
    @EnforceNothing
    public ResponseEntity<Void> processNewProgrammingSubmission(@PathVariable("participationId") Long participationId, @RequestBody Object requestBody) {
        log.debug("REST request to inform about new commit+push for participation: {}", participationId);

        try {
            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore, a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();

            Participation participation = participationRepository.findWithEagerSubmissionsByIdWithTeamStudentsElseThrow(participationId);
            if (!(participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation)) {
                throw new BadRequestAlertException("The referenced participation " + participationId + " is not of type ProgrammingExerciseParticipation", "ProgrammingSubmission",
                        "participationWrongType");
            }

            ProgrammingSubmission newProgrammingSubmission = programmingSubmissionService.processNewProgrammingSubmission(programmingExerciseParticipation, requestBody);
            var exerciseId = participation.getExercise().getId();
            // Remove unnecessary information from the new programming submission, in particular submission and exercise (avoid sending too much information over websocket)
            newProgrammingSubmission.getParticipation().setSubmissions(null);
            newProgrammingSubmission.getParticipation().setExercise(null);
            programmingMessagingService.notifyUserAboutSubmission(newProgrammingSubmission, exerciseId);
        }
        catch (IllegalArgumentException ex) {
            log.error("Exception encountered when trying to extract the commit hash from the request body: processing submission for participation {} failed with request body {}",
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
            log.error("Participation with id {} not found: processing submission failed for request body {}", participationId, requestBody, ex);
            throw ex;
        }
        catch (BadRequestAlertException ex) {
            log.error("Participation with id {} is not a ProgrammingExerciseParticipation: processing submission failed for request body {}", participationId, requestBody, ex);
            throw ex;
        }
        catch (VersionControlException ex) {
            log.warn("User committed to the wrong branch for participation {}", participationId);
            return ResponseEntity.status(HttpStatus.OK).build();
        }

        // Note: we should not really return status code other than 200, because Gitlab might kill the webhook, if there are too many errors
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
     * @param exerciseId  the id of the programmingExercise where the test cases got changed
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
            Commit commit = versionControlService.orElseThrow().getLastCommitDetails(requestBody);
            lastCommitHash = commit.commitHash();
            log.info("create new programmingSubmission with commitHash: {} for exercise {}", lastCommitHash, exerciseId);
        }
        catch (Exception ex) {
            log.debug(
                    "Commit hash could not be parsed from test repository from exercise {}, the submission will be created with the latest commit hash of the solution repository.",
                    exerciseId, ex);
        }

        // When the tests were changed, the solution repository will be built. We therefore create a submission for the solution participation.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exerciseId, lastCommitHash);
        programmingMessagingService.notifyUserAboutSubmission(submission, exerciseId);
        // It is possible that there is now a new test case or an old one has been removed. We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChanged(exerciseId, true);

        // Artemis should trigger the solution build when tests change
        try {
            var solutionParticipation = (SolutionProgrammingExerciseParticipation) submission.getParticipation();
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(solutionParticipation);
        }
        catch (ContinuousIntegrationException ex) {
            // TODO: This case is currently not handled. The correct handling would be creating the submission and informing the user that the build trigger failed.
        }

        return ResponseEntity.ok().build();
    }
}
