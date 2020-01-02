package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.config.Constants.TEST_CASE_CHANGED_API_PATH;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;

public abstract class ArtemisVersionControlService implements VersionControlService {

    @Value("${server.url}")
    protected String ARTEMIS_BASE_URL;

    @Value("${artemis.version-control.ci-token:}")
    private String CI_TOKEN;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    protected ArtemisVersionControlService(Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * Adds a webhook for teh specified repository to the given notification URL.
     *
     * @param repositoryUrl The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName Any arbitrary name for the webhook
     */
    protected abstract void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName);

    /**
     * Adds an authenticated webhook for teh specified repository to the given notification URL.
     *
     * @param repositoryUrl The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName Any arbitrary name for the webhook
     * @param secretToken A secret token that authenticates the webhook against the system behind the notification URL
     */
    protected abstract void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName, String secretToken);

    @Override
    public void addWebHooksForExercise(ProgrammingExercise exercise) {
        final var projectKey = exercise.getProjectKey();
        final var artemisTemplateHookPath = ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + exercise.getTemplateParticipation().getId();
        final var artemisSolutionHookPath = ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + exercise.getSolutionParticipation().getId();
        final var artemisTestsHookPath = ARTEMIS_BASE_URL + TEST_CASE_CHANGED_API_PATH + exercise.getId();
        addWebHook(exercise.getTemplateRepositoryUrlAsUrl(), artemisTemplateHookPath, "Artemis WebHook");
        addWebHook(exercise.getSolutionRepositoryUrlAsUrl(), artemisSolutionHookPath, "Artemis WebHook");
        addWebHook(exercise.getTestRepositoryUrlAsUrl(), artemisTestsHookPath, "Artemis WebHook");

        // Depending on the activated VCS/CI systems, the VCS system pushes commit notifications to the CI, or the CI pulls
        final var templatePlanNotificationUrl = continuousIntegrationService.get().getWebhookUrl(projectKey, exercise.getTemplateParticipation().getBuildPlanId());
        final var solutionPlanNotificationUrl = continuousIntegrationService.get().getWebhookUrl(projectKey, exercise.getSolutionParticipation().getBuildPlanId());
        if (templatePlanNotificationUrl.isPresent() && solutionPlanNotificationUrl.isPresent()) {
            addWebHook(exercise.getTemplateRepositoryUrlAsUrl(), templatePlanNotificationUrl.get(), "Artemis Exercise WebHook", CI_TOKEN);
            addWebHook(exercise.getSolutionRepositoryUrlAsUrl(), solutionPlanNotificationUrl.get(), "Artemis Solution WebHook", CI_TOKEN);
            addWebHook(exercise.getTestRepositoryUrlAsUrl(), solutionPlanNotificationUrl.get(), "Artemis Tests WebHook", CI_TOKEN);
        }
    }

    @Override
    public void addWebHookForParticipation(ProgrammingExerciseParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            addWebHook(participation.getRepositoryUrlAsUrl(), ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participation.getId(), "Artemis WebHook");
            // Optional webhook from the VCS to the CI (needed for some systems such as GitLab + Jenkins)
            final var ciHookUrl = continuousIntegrationService.get().getWebhookUrl(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());
            ciHookUrl.ifPresent(hookUrl -> addWebHook(participation.getRepositoryUrlAsUrl(), hookUrl, "Artemis trigger to CI", CI_TOKEN));
        }
    }
}
