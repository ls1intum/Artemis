package de.tum.cit.aet.artemis.programming.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HADES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_STATELESS_JENKINS;
import static de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils.hashSha256;

import java.security.MessageDigest;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.service.ci.StatelessCIService;

/**
 * REST controller for receiving build results for external CI systems. At the moment, only Jenkins is supported.
 */
@Profile({ PROFILE_JENKINS, PROFILE_STATELESS_JENKINS, PROFILE_HADES })
@Lazy
@RestController
@RequestMapping("api/programming/public/")
public class PublicProgrammingExerciseResultResource {

    private static final Logger log = LoggerFactory.getLogger(PublicProgrammingExerciseResultResource.class);

    private final Optional<StatelessCIService> continuousIntegrationService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final byte[] artemisAuthenticationTokenHash;

    public PublicProgrammingExerciseResultResource(Optional<StatelessCIService> continuousIntegrationService, ProgrammingExerciseGradingService programmingExerciseGradingService,
            ProgrammingTriggerService programmingTriggerService, ProgrammingMessagingService programmingMessagingService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            @Value("${artemis.continuous-integration.artemis-authentication-token-value}") String artemisAuthenticationTokenValue) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingTriggerService = programmingTriggerService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        // Validates the length of the artemisAuthenticationTokenValue on startup.
        if (artemisAuthenticationTokenValue == null || artemisAuthenticationTokenValue.length() < 12) {
            throw new IllegalArgumentException("The artemisAuthenticationTokenValue is not set or too short. Please check the configuration.");
        }
        this.artemisAuthenticationTokenHash = hashSha256(artemisAuthenticationTokenValue);
    }

    @PostMapping("programming-exercises/new-result/{participationId}")
    @EnforceNothing
    public ResponseEntity<Void> processNewProgrammingExerciseResultWithParticipationID(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationToken,
            @PathVariable Long participationId, @RequestBody Object requestBody) {
        log.debug("Received new programming exercise result from Hades");
        if (!matches(authorizationToken)) {
            log.info("Cancelling request with invalid authorizationToken {}", authorizationToken);
            throw new AccessForbiddenException(); // Only allow endpoint when using correct authorizationToken
        }

        ProgrammingExerciseParticipation participation = null;

        if (participationId != null) {
            try {
                participation = programmingExerciseParticipationService.findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(participationId);
                log.info("Successfully retrieved participation via ID: {}", participationId);
            }
            catch (ContinuousIntegrationException cISException) {
                log.warn("Could not retrieve participation ID either: {}", cISException.getMessage());
                throw new EntityNotFoundException("Participation could not be found via plan key or participation ID");
            }
        }

        // Process the new result from the build result.
        assert participation != null;
        Result result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, requestBody);

        // Only notify the user about the new result if the result was created successfully.
        if (result != null) {
            if (participation instanceof SolutionProgrammingExerciseParticipation) {
                // If the solution participation was updated, also trigger the template participation build.
                // This method will return without triggering the build if the submission is not of type TEST.
                var programmingSubmission = (ProgrammingSubmission) result.getSubmission();
                triggerTemplateBuildIfTestCasesChanged(participation.getProgrammingExercise().getId(), programmingSubmission);
            }

            programmingMessagingService.notifyUserAboutNewResult(result, participation);

            log.info("The new result for {} was saved successfully", participation);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * This method is used by the CI system to inform Artemis about a new programming exercise build result.
     * It will make sure to:
     * - Create a result from the build result including its feedbacks
     * - Assign the result to an existing submission OR create a new submission if needed
     * - Update the result's score based on the exercise's test cases (weights, etc.)
     * - Update the exercise's test cases if the build is from a solution participation
     *
     * @param authorizationToken CI auth authorizationToken coming from the external CI system (Jenkins)
     * @param requestBody        build result of CI system
     * @return a ResponseEntity to the CI system
     */
    @PostMapping("programming-exercises/new-result")
    @EnforceNothing
    public ResponseEntity<Void> processNewProgrammingExerciseResult(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationToken, @RequestBody Object requestBody) {
        log.debug("Received new programming exercise result from Jenkins");
        if (!matches(authorizationToken)) {
            log.info("Cancelling request with invalid authorizationToken {}", authorizationToken);
            throw new AccessForbiddenException(); // Only allow endpoint when using correct authorizationToken
        }

        // No 'user' is properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();

        // Retrieving the plan key can fail if e.g. the requestBody is malformed. In this case nothing else can be done.
        String planKey;
        try {
            planKey = continuousIntegrationService.orElseThrow().getPlanKey(requestBody);
        }
        catch (ContinuousIntegrationException cISException) {
            log.error("Exception encountered when trying to retrieve the plan key from a request a new programming exercise result: {}, {} :"
                    + "Your CIS encountered an Exception while trying to retrieve the build plan ", cISException, requestBody);
            throw new BadRequestAlertException(
                    "The continuous integration server encountered an exception when trying to retrieve the plan key from a request a new programming exercise result", "BuildPlan",
                    "ciExceptionForBuildPlanKey");
        }
        log.info("Artemis received a new result for build plan {}", planKey);

        // Try to retrieve the participation with the build plan key.
        var participation = programmingExerciseParticipationService.getParticipationWithResults(planKey);
        if (participation == null) {
            log.warn("Participation is missing for notifyResultNew (PlanKey: {}).", planKey);
            throw new EntityNotFoundException("Participation for build plan " + planKey + " does not exist");
        }

        // Process the new result from the build result.
        Result result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, requestBody);

        // Only notify the user about the new result if the result was created successfully.
        if (result != null) {

            if (participation instanceof SolutionProgrammingExerciseParticipation) {
                // If the solution participation was updated, also trigger the template participation build.
                // This method will return without triggering the build if the submission is not of type TEST.
                var programmingSubmission = (ProgrammingSubmission) result.getSubmission();
                triggerTemplateBuildIfTestCasesChanged(participation.getProgrammingExercise().getId(), programmingSubmission);
            }

            programmingMessagingService.notifyUserAboutNewResult(result, participation);

            log.info("The new result for {} was saved successfully", planKey);
        }
        return ResponseEntity.ok().build();
    }

    private boolean matches(String incomingToken) {
        return MessageDigest.isEqual(artemisAuthenticationTokenHash, hashSha256(incomingToken));
    }

    /**
     * Trigger the build of the template repository, if the submission of the provided result is of type TEST.
     * Will use the commitHash of the submission for triggering the template build.
     * <p>
     * If the submission of the provided result is not of type TEST, the method will return without triggering the build.
     *
     * @param programmingExerciseId ProgrammingExercise id that belongs to the result.
     * @param submission            ProgrammingSubmission
     */
    private void triggerTemplateBuildIfTestCasesChanged(long programmingExerciseId, ProgrammingSubmission submission) {
        log.info("triggerTemplateBuildIfTestCasesChanged programmingExerciseId {}, submission {}, results {}", programmingExerciseId, submission, submission.getResults());
        // We only trigger the template build when the test repository was changed.
        // If the submission is from type TEST but already has a result, this build was not triggered by a test repository change
        if (!submission.belongsToTestRepository()) {
            return;
        }
        try {
            programmingTriggerService.triggerTemplateBuildAndNotifyUser(programmingExerciseId, submission.getCommitHash(), SubmissionType.TEST);
        }
        catch (EntityNotFoundException ex) {
            // If for some reason the programming exercise does not have a template participation, we can only log and abort.
            log.error(
                    "Could not trigger the build of the template repository for the programming exercise id {} because no template participation could be found for the given exercise",
                    programmingExerciseId);
        }
    }
}
