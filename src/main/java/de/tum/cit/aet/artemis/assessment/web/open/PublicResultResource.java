package de.tum.cit.aet.artemis.assessment.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.service.hestia.TestwiseCoverageService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * REST controller for receiving build results.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicResultResource {

    private static final Logger log = LoggerFactory.getLogger(PublicResultResource.class);

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String artemisAuthenticationTokenValue = "";

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ResultService resultService;

    private final TestwiseCoverageService testwiseCoverageService;

    private final ProgrammingTriggerService programmingTriggerService;

    private final ProgrammingMessagingService programmingMessagingService;

    public PublicResultResource(Optional<ContinuousIntegrationService> continuousIntegrationService, ProgrammingExerciseGradingService programmingExerciseGradingService,
            ResultService resultService, TestwiseCoverageService testwiseCoverageService, ProgrammingTriggerService programmingTriggerService,
            ProgrammingMessagingService programmingMessagingService) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.resultService = resultService;
        this.testwiseCoverageService = testwiseCoverageService;
        this.programmingTriggerService = programmingTriggerService;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * This method is used by the CI system to inform Artemis about a new programming exercise build result.
     * It will make sure to:
     * - Create a result from the build result including its feedbacks
     * - Assign the result to an existing submission OR create a new submission if needed
     * - Update the result's score based on the exercise's test cases (weights, etc.)
     * - Update the exercise's test cases if the build is from a solution participation
     *
     * @param token       CI auth token
     * @param requestBody build result of CI system
     * @return a ResponseEntity to the CI system
     */
    @PostMapping("programming-exercises/new-result")
    @EnforceNothing
    public ResponseEntity<Void> processNewProgrammingExerciseResult(@RequestHeader("Authorization") String token, @RequestBody Object requestBody) {
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
        var participation = resultService.getParticipationWithResults(planKey);
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

                // the test cases and the submission have been saved to the database previously, therefore we can add the reference to the coverage reports
                if (Boolean.TRUE.equals(participation.getProgrammingExercise().getBuildConfig().isTestwiseCoverageEnabled()) && Boolean.TRUE.equals(result.isSuccessful())) {
                    testwiseCoverageService.createTestwiseCoverageReport(result.getCoverageFileReportsByTestCaseName(), participation.getProgrammingExercise(),
                            programmingSubmission);
                }
            }

            programmingMessagingService.notifyUserAboutNewResult(result, participation);

            log.info("The new result for {} was saved successfully", planKey);
        }
        return ResponseEntity.ok().build();
    }

    // TODO: Move to ResultService. Need to break circular dependencies for that
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
