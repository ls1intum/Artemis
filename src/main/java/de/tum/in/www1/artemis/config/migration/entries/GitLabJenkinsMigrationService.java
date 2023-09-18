package de.tum.in.www1.artemis.config.migration.entries;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.ProjectHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabException;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsService;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

/**
 * Services for executing migration tasks for Jenkins and GitLab
 */
@Service
@Profile("jenkins & gitlab")
public class GitLabJenkinsMigrationService implements CIVCSMigrationService {

    private final Logger log = LoggerFactory.getLogger(GitLabJenkinsMigrationService.class);

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.continuous-integration.url}")
    protected URL jenkinsServerUrl;

    @Value("${jenkins.internal-urls.ci-url:#{null}}")
    protected URL internalJenkinsUrl;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsService jenkinsService;

    private final UrlService urlService;

    private final GitLabApi gitlab;

    public GitLabJenkinsMigrationService(JenkinsJobService jenkinsJobService, GitLabApi gitlab, JenkinsService jenkinsService, UrlService urlService) {
        this.jenkinsJobService = jenkinsJobService;
        this.gitlab = gitlab;
        this.jenkinsService = jenkinsService;
        this.urlService = urlService;
    }

    @Override
    public void overrideBuildPlanNotification(String projectKey, String buildPlanKey, VcsRepositoryUrl repositoryUrl) {
        try {
            Document currentConfig = jenkinsJobService.getJobConfig(projectKey, buildPlanKey);
            Document newConfig = replaceNotificationUrlInJobConfig(currentConfig);
            jenkinsJobService.updateJob(projectKey, buildPlanKey, newConfig);
        }
        catch (IOException | TransformerException e) {
            log.error("Could not fix build plan notification for build plan " + buildPlanKey + " in project " + projectKey, e);
            throw new JenkinsException(e);
        }
    }

    @Override
    public void deleteBuildTriggers(String projectKey, String buildPlanKey, VcsRepositoryUrl repositoryUrl) {
        removeWebHook(repositoryUrl);

        if (projectKey != null && buildPlanKey != null) {
            try {
                Document currentConfig = jenkinsJobService.getJobConfig(projectKey, buildPlanKey);
                Document newConfig = removeTrigger(currentConfig);
                jenkinsJobService.updateJob(projectKey, buildPlanKey, newConfig);
            }
            catch (IOException | TransformerException e) {
                log.error("Could not remove build plan trigger in Jenkinsjob config.xml for buildPlanKey {} ", buildPlanKey, e);
                throw new JenkinsException(e);
            }
        }
    }

    @Override
    public void overrideBuildPlanRepository(String buildPlanKey, String name, String repositoryUrl, String defaultBranch) {
        // not needed for Jenkins
    }

    @Override
    public void overrideRepositoriesToCheckout(String buildPlanKey, List<AuxiliaryRepository> auxiliaryRepositoryList, ProgrammingLanguage programmingLanguage) {
        // not needed for Jenkins
    }

    @Override
    public Page<ProgrammingExerciseStudentParticipation> getPageableStudentParticipations(
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Pageable pageable) {
        return programmingExerciseStudentParticipationRepository.findAllWithRepositoryUrlOrBuildPlanId(pageable);
    }

    @Override
    public boolean supportsAuxiliaryRepositories() {
        return false;
    }

    @Override
    public boolean buildPlanExists(String projectKey, String buildPlanKey) {
        return jenkinsService.checkIfBuildPlanExists(projectKey, buildPlanKey);
    }

    @Override
    public void removeWebHook(VcsRepositoryUrl repositoryUrl) {
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);

        try {
            List<ProjectHook> hooks = gitlab.getProjectApi().getHooks(repositoryPath);
            for (ProjectHook projectHook : hooks) {
                var url = projectHook.getUrl();
                /*
                 * we can't use only the JenkinsServerUrl because and the buildPlanKey, as
                 * the tests repository also has a webhook for the solution repository, so
                 * we check if the hook contains the JenkinsServerUrl and ensure that not the artemisServerUrl
                 */
                boolean toJenkins = false;
                if (url.contains(artemisServerUrl)) {
                    continue;
                }
                if (url.contains(jenkinsServerUrl.toString())) {
                    toJenkins = true;
                }
                else if (internalJenkinsUrl != null) {
                    if (url.contains(internalJenkinsUrl.toString())) {
                        toJenkins = true;
                    }
                }
                if (toJenkins) {
                    gitlab.getProjectApi().deleteHook(projectHook);
                }
            }
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to remove webhook for " + repositoryUrl, e);
        }
    }

    /**
     * Replaces the current notification URL of the given config document with the current one in use by parsing the config document as string and replacing the URL using a regex.
     *
     * @param config the config document
     * @return the config document with the replaced notification URL
     * @throws TransformerException
     */
    private Document replaceNotificationUrlInJobConfig(Document config) throws TransformerException {
        String stringConfig = XmlFileUtils.writeToString(config);
        // Pattern captures the current notification URL and additionally everything around in order to replace the URL
        String newStringConfig = stringConfig.replaceAll("(.*?notificationUrl: ')(.+?)('.*?)", "$1" + artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH + "$3");
        return XmlFileUtils.readFromString(newStringConfig);
    }

    private Document removeTrigger(Document config) throws TransformerException {
        String stringConfig = XmlFileUtils.writeToString(config);
        // Pattern captures the current notification URL and additionally everything around in order to replace the URL
        String newStringConfig = stringConfig.replaceAll(
                "(?s)(<org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>)(.*?)(</org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>)",
                "");
        return XmlFileUtils.readFromString(newStringConfig);
    }

    @Override
    public void checkPrerequisites() throws ContinuousIntegrationException {
        // nothing special is needed for Jenkins and GitLab
    }
}
