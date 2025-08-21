package de.tum.cit.aet.artemis.jenkins.connector.service;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Service for managing Jenkins jobs and folders.
 */
@Service
public class JenkinsJobService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsJobService.class);

    private final RestTemplate restTemplate;

    @Value("${jenkins.url}")
    private URI jenkinsServerUri;

    public JenkinsJobService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Retrieves the job inside a folder job or null if it doesn't exist.
     *
     * @param folderJobName the name of the folder job
     * @param jobName       the name of the job
     * @return the job with details
     */
    public JobWithDetails getJob(String folderJobName, String jobName) {
        if (folderJobName == null || jobName == null) {
            log.warn("Cannot get the job, because projectKey {} or jobName {} is null", folderJobName, jobName);
            return null;
        }

        try {
            URI uri = JenkinsEndpoints.GET_JOB.buildEndpoint(jenkinsServerUri, folderJobName, jobName).build(true).toUri();
            return restTemplate.getForObject(uri, JobWithDetails.class);
        }
        catch (HttpClientErrorException.NotFound notFound) {
            log.warn("Cannot get the job {} in folder {} because it doesn't exist.", jobName, folderJobName);
            return null;
        }
        catch (RestClientException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record JobWithDetails(String name, String description, boolean inQueue) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FolderJob(String name, String description, String url) {
    }

    /**
     * Gets the folder job or null if it doesn't exist
     *
     * @param folderName the name of the folder job
     * @return the folder job
     */
    public FolderJob getFolderJob(String folderName) {
        try {
            URI uri = JenkinsEndpoints.GET_FOLDER_JOB.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
            return restTemplate.getForObject(uri, FolderJob.class);
        }
        catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
        catch (RestClientException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Creates a folder with the given name in Jenkins
     *
     * @param folderName the name of the folder to create
     */
    public void createFolder(String folderName) throws JenkinsException {
        try {
            String folderXml = createFolderXmlConfig();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> entity = new HttpEntity<>(folderXml, headers);

            URI uri = JenkinsEndpoints.NEW_FOLDER.buildEndpoint(jenkinsServerUri)
                    .queryParam("name", folderName)
                    .queryParam("mode", "com.cloudbees.hudson.plugins.folder.Folder")
                    .build(true).toUri();
                    
            restTemplate.postForObject(uri, entity, String.class);
            log.debug("Created folder {} in Jenkins", folderName);
        }
        catch (RestClientException e) {
            log.error("Failed to create folder {} in Jenkins", folderName, e);
            throw new JenkinsException("Failed to create folder: " + folderName, e);
        }
    }

    /**
     * Deletes a folder job
     *
     * @param folderName the name of the folder job
     */
    public void deleteFolderJob(String folderName) {
        try {
            URI uri = JenkinsEndpoints.DELETE_FOLDER.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
            restTemplate.postForObject(uri, null, String.class);
            log.debug("Deleted folder {} from Jenkins", folderName);
        }
        catch (HttpClientErrorException.NotFound notFound) {
            log.warn("Folder {} not found, nothing to delete", folderName);
        }
        catch (RestClientException e) {
            log.error("Failed to delete folder {} from Jenkins", folderName, e);
            throw new JenkinsException("Failed to delete folder: " + folderName, e);
        }
    }

    /**
     * Deletes a job within a folder
     *
     * @param folderName the name of the folder
     * @param jobName    the name of the job
     */
    public void deleteJob(String folderName, String jobName) {
        try {
            URI uri = JenkinsEndpoints.DELETE_JOB.buildEndpoint(jenkinsServerUri, folderName, jobName).build(true).toUri();
            restTemplate.postForObject(uri, null, String.class);
            log.debug("Deleted job {}/{} from Jenkins", folderName, jobName);
        }
        catch (HttpClientErrorException.NotFound notFound) {
            log.warn("Job {}/{} not found, nothing to delete", folderName, jobName);
        }
        catch (RestClientException e) {
            log.error("Failed to delete job {}/{} from Jenkins", folderName, jobName, e);
            throw new JenkinsException("Failed to delete job: " + folderName + "/" + jobName, e);
        }
    }

    /**
     * Triggers a build for the specified job
     *
     * @param folderName the name of the folder
     * @param jobName    the name of the job
     * @return the build number if available
     */
    public Integer triggerBuild(String folderName, String jobName) {
        try {
            URI uri = JenkinsEndpoints.TRIGGER_BUILD.buildEndpoint(jenkinsServerUri, folderName, jobName).build(true).toUri();
            restTemplate.postForObject(uri, null, String.class);
            log.debug("Triggered build for job {}/{}", folderName, jobName);
            
            // TODO: Extract build number from queue location header if needed
            return null;
        }
        catch (RestClientException e) {
            log.error("Failed to trigger build for job {}/{}", folderName, jobName, e);
            throw new JenkinsException("Failed to trigger build for job: " + folderName + "/" + jobName, e);
        }
    }

    private String createFolderXmlConfig() {
        return """
            <?xml version='1.1' encoding='UTF-8'?>
            <com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.815.v0dd5a_cb_40e0e">
              <description></description>
              <properties/>
              <folderViews class="com.cloudbees.hudson.plugins.folder.views.DefaultFolderViewHolder">
                <views>
                  <hudson.model.AllView>
                    <owner class="com.cloudbees.hudson.plugins.folder.Folder" reference="../../../.."/>
                    <name>All</name>
                    <filterExecutors>false</filterExecutors>
                    <filterQueue>false</filterQueue>
                    <properties class="hudson.model.View$PropertyList"/>
                  </hudson.model.AllView>
                </views>
                <tabBar class="hudson.views.DefaultViewsTabBar"/>
              </folderViews>
              <healthMetrics/>
            </com.cloudbees.hudson.plugins.folder.Folder>
            """;
    }

    public static class JenkinsException extends RuntimeException {
        public JenkinsException(String message) {
            super(message);
        }

        public JenkinsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}