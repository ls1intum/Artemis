package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.config.Constants.TEST_CASE_CHANGED_API_PATH;

import java.net.URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;

public abstract class AbstractVersionControlService implements VersionControlService {

    @Value("${server.url}")
    protected String ARTEMIS_BASE_URL;

    private ApplicationContext applicationContext;

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Adds a webhook for the specified repository to the given notification URL.
     *
     * @param repositoryUrl The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName Any arbitrary name for the webhook
     */
    protected abstract void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName);

    /**
     * Adds an authenticated webhook for the specified repository to the given notification URL.
     *
     * @param repositoryUrl The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName Any arbitrary name for the webhook
     * @param secretToken A secret token that authenticates the webhook against the system behind the notification URL
     */
    protected abstract void addAuthenticatedWebHook(URL repositoryUrl, String notificationUrl, String webHookName, String secretToken);

    protected ContinuousIntegrationService getContinuousIntegrationService() {
        // We need to get the CI service from the context, because Bamboo and Bitbucket would end up in a circular dependency otherwise
        return applicationContext.getBean(ContinuousIntegrationService.class);
    }

    @Override
    public void addWebHooksForExercise(ProgrammingExercise exercise) {
        final var projectKey = exercise.getProjectKey();
        final var artemisTemplateHookPath = ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + exercise.getTemplateParticipation().getId();
        final var artemisSolutionHookPath = ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + exercise.getSolutionParticipation().getId();
        final var artemisTestsHookPath = ARTEMIS_BASE_URL + TEST_CASE_CHANGED_API_PATH + exercise.getId();
        // first add web hooks from the version control service to Artemis, so that Artemis is notified and can create ProgrammingSubmission when instructors push their template or
        // solution code
        addWebHook(exercise.getTemplateRepositoryUrlAsUrl(), artemisTemplateHookPath, "Artemis WebHook");
        addWebHook(exercise.getSolutionRepositoryUrlAsUrl(), artemisSolutionHookPath, "Artemis WebHook");
        addWebHook(exercise.getTestRepositoryUrlAsUrl(), artemisTestsHookPath, "Artemis WebHook");
    }

    @Override
    public void addWebHookForParticipation(ProgrammingExerciseParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            // first add a web hook from the version control service to Artemis, so that Artemis is notified can create a ProgrammingSubmission when students push their code
            addWebHook(participation.getRepositoryUrlAsUrl(), ARTEMIS_BASE_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participation.getId(), "Artemis WebHook");
        }
    }
}
