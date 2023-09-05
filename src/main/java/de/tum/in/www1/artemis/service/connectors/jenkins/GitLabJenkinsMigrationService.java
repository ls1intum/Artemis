package de.tum.in.www1.artemis.service.connectors.jenkins;

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
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.ci.CIMigrationService;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabException;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

/**
 * Services for executing migration tasks for Jenkins and GitLab
 */
@Service
@Profile("jenkins")
public class GitLabJenkinsMigrationService implements CIMigrationService {

    private final Logger log = LoggerFactory.getLogger(GitLabJenkinsMigrationService.class);

    @Value("${server.url}")
    private String artemisServerUrl;

    @Value("${artemis.continuous-integration.url}")
    protected URL jenkinsServerUrl;

    private final JenkinsJobService jenkinsJobService;

    private final UrlService urlService;

    private final GitLabApi gitlab;

    public GitLabJenkinsMigrationService(JenkinsJobService jenkinsJobService, GitLabApi gitlab) {
        this.jenkinsJobService = jenkinsJobService;
        this.urlService = new UrlService();
        this.gitlab = gitlab;
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
    public void overrideBuildPlanRepository(String buildPlanKey, String name, String repositoryUrl) {
        // not needed for Jenkins
    }

    @Override
    public void overrideRepositoriesToCheckout(String buildPlanKey, List<AuxiliaryRepository> auxiliaryRepositoryList) {
        // not needed for Jenkins
    }

    protected void removeWebHook(VcsRepositoryUrl repositoryUrl) {
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
                if (url.contains(jenkinsServerUrl.toString()) && !url.contains(artemisServerUrl)) {
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
}
