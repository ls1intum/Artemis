package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.io.IOException;

import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.service.connectors.ci.CIMigrationService;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

/**
 * Services for executing migration tasks for Jenkins.
 */
@Service
@Profile("jenkins")
public class JenkinsMigrationService implements CIMigrationService {

    private final Logger log = LoggerFactory.getLogger(JenkinsMigrationService.class);

    @Value("${server.url}")
    private String artemisServerUrl;

    private final JenkinsJobService jenkinsJobService;

    public JenkinsMigrationService(JenkinsJobService jenkinsJobService) {
        this.jenkinsJobService = jenkinsJobService;
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
}
