package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.UrlService;

public abstract class AbstractVersionControlService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(AbstractVersionControlService.class);

    @Value("${server.url}")
    protected String ARTEMIS_SERVER_URL;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    protected Optional<String> userPrefixEdx;

    @Value("${artemis.lti.user-prefix-u4i:#{null}}")
    protected Optional<String> userPrefixU4I;

    private final ApplicationContext applicationContext;

    private final GitService gitService;

    protected final UrlService urlService;

    public AbstractVersionControlService(ApplicationContext applicationContext, GitService gitService, UrlService urlService) {
        this.applicationContext = applicationContext;
        this.gitService = gitService;
        this.urlService = urlService;
    }

    /**
     * Adds a webhook for the specified repository to the given notification URL.
     *
     * @param repositoryUrl The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName Any arbitrary name for the webhook
     */
    protected abstract void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName);

    /**
     * Adds an authenticated webhook for the specified repository to the given notification URL.
     *
     * @param repositoryUrl The repository to which the webhook should get added to
     * @param notificationUrl The URL the hook should notify
     * @param webHookName Any arbitrary name for the webhook
     * @param secretToken A secret token that authenticates the webhook against the system behind the notification URL
     */
    protected abstract void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken);

    protected ContinuousIntegrationService getContinuousIntegrationService() {
        // We need to get the CI service from the context, because Bamboo and Bitbucket would end up in a circular dependency otherwise
        return applicationContext.getBean(ContinuousIntegrationService.class);
    }

    @Override
    public void addWebHooksForExercise(ProgrammingExercise exercise) {
        final var artemisTemplateHookPath = ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + exercise.getTemplateParticipation().getId();
        final var artemisSolutionHookPath = ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + exercise.getSolutionParticipation().getId();
        final var artemisTestsHookPath = ARTEMIS_SERVER_URL + TEST_CASE_CHANGED_API_PATH + exercise.getId();
        // first add web hooks from the version control service to Artemis, so that Artemis is notified and can create ProgrammingSubmission when instructors push their template or
        // solution code
        addWebHook(exercise.getVcsTemplateRepositoryUrl(), artemisTemplateHookPath, "Artemis WebHook");
        addWebHook(exercise.getVcsSolutionRepositoryUrl(), artemisSolutionHookPath, "Artemis WebHook");
        addWebHook(exercise.getVcsTestRepositoryUrl(), artemisTestsHookPath, "Artemis WebHook");
    }

    @Override
    public void addWebHookForParticipation(ProgrammingExerciseParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            // first add a web hook from the version control service to Artemis, so that Artemis is notified can create a ProgrammingSubmission when students push their code
            addWebHook(participation.getVcsRepositoryUrl(), ARTEMIS_SERVER_URL + PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participation.getId(), "Artemis WebHook");
        }
    }

    @Override
    public VcsRepositoryUrl copyRepository(String sourceProjectKey, String sourceRepositoryName, String targetProjectKey, String targetRepositoryName)
            throws VersionControlException {
        sourceRepositoryName = sourceRepositoryName.toLowerCase();
        targetRepositoryName = targetRepositoryName.toLowerCase();
        final String targetRepoSlug = targetProjectKey.toLowerCase() + "-" + targetRepositoryName;
        // get the remote url of the source repo
        final var sourceRepoUrl = getCloneRepositoryUrl(sourceProjectKey, sourceRepositoryName);
        // get the remote url of the target repo
        final var targetRepoUrl = getCloneRepositoryUrl(targetProjectKey, targetRepoSlug);
        Repository targetRepo = null;
        try {
            // create the new target repo
            createRepository(targetProjectKey, targetRepoSlug, null);
            // clone the source repo to the target directory
            targetRepo = gitService.getOrCheckoutRepositoryIntoTargetDirectory(sourceRepoUrl, targetRepoUrl, true);
            // copy by pushing the source's content to the target's repo
            gitService.pushSourceToTargetRepo(targetRepo, targetRepoUrl, getDefaultBranchOfRepository(sourceRepoUrl));
        }
        catch (InterruptedException | GitAPIException e) {
            Path localPath = gitService.getDefaultLocalPathOfRepo(targetRepoUrl);
            try {
                if (targetRepo != null) {
                    // delete the target repo if an error occurs
                    gitService.deleteLocalRepository(targetRepo);
                }
                else {
                    // or delete the folder if it exists
                    FileUtils.deleteDirectory(localPath.toFile());
                }
            }
            catch (IOException ex) {
                // ignore
                log.error("Could not delete directory of the failed cloned repository in: {}", localPath);
            }
            throw new VersionControlException("Could not copy repository " + sourceRepositoryName + " to the target repository " + targetRepositoryName, e);
        }

        return targetRepoUrl;
    }

    @Override
    public String getRepositoryName(VcsRepositoryUrl repositoryUrl) {
        return urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
    }
}
