package de.tum.in.www1.artemis.web.rest.publicc;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for receiving build results.
 */
@RestController
@RequestMapping("api/public/")
public class PublicResultResource {

    private final Logger log = LoggerFactory.getLogger(PublicResultResource.class);

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String artemisAuthenticationTokenValue = "";

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final WebsocketMessagingService messagingService;

    private final LtiNewResultService ltiNewResultService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ResultService resultService;

    public PublicResultResource(Optional<ContinuousIntegrationService> continuousIntegrationService, WebsocketMessagingService messagingService,
            LtiNewResultService ltiNewResultService, ProgrammingExerciseGradingService programmingExerciseGradingService, ResultService resultService) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.messagingService = messagingService;
        this.ltiNewResultService = ltiNewResultService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.resultService = resultService;
    }

    /**
     * This method is used by the CI system to inform Artemis about a new programming exercise build result.
     * It will make sure to:
     * - Create a result from the build result including its feedbacks
     * - Assign the result to an existing submission OR create a new submission if needed
     * - Update the result's score based on the exercise's test cases (weights, etc.)
     * - Update the exercise's test cases if the build is from a solution participation
     *
     * @param token CI auth token
     * @param requestBody build result of CI system
     * @return a ResponseEntity to the CI system
     */
    @PostMapping("programming-exercises/new-result")
    @EnforceNothing
    public ResponseEntity<?> processNewProgrammingExerciseResult(@RequestHeader("Authorization") String token, @RequestBody Object requestBody) {
        log.debug("Received result notify (NEW)");
        if (token == null || !token.equals(artemisAuthenticationTokenValue)) {
            log.info("Cancelling request with invalid token {}", token);
            throw new AccessForbiddenException(); // Only allow endpoint when using correct token
        }

        // No 'user' is properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();

        // Retrieving the plan key can fail if e.g. the requestBody is malformed. In this case nothing else can be done.
        String planKey;
        try {
            planKey = continuousIntegrationService.get().getPlanKey(requestBody);
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
        var participation = resultService.getParticipationWithResults(planKey);
        if (participation == null) {
            log.warn("Participation is missing for notifyResultNew (PlanKey: {}).", planKey);
            throw new EntityNotFoundException("Participation for build plan " + planKey + " does not exist");
        }

        // Process the new result from the build result.
        Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, requestBody);

        // Only notify the user about the new result if the result was created successfully.
        if (optResult.isPresent()) {
            Result result = optResult.get();
            log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
            // notify user via websocket
            messagingService.broadcastNewResult((Participation) participation, result);
            if (participation instanceof StudentParticipation) {
                // do not try to report results for template or solution participations
                ltiNewResultService.onNewResult((ProgrammingExerciseStudentParticipation) participation);
            }
            log.info("The new result for {} was saved successfully", planKey);
        }
        return ResponseEntity.ok().build();
    }
}
