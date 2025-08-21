package de.tum.cit.aet.artemis.programming.service.jenkins.jobs;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.io.IOException;
import java.net.URI;

import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsEndpoints;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsXmlFileUtils;

@Lazy
@Service
@Profile(PROFILE_JENKINS)
// TODO: EXTRACTED TO MICROSERVICE - This class has been copied to jenkins-connector/src/main/java/de/tum/cit/aet/artemis/jenkins/connector/service/JenkinsJobService.java
// This code will be removed once the microservice migration is complete
public class JenkinsJobService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsJobService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.continuous-integration.url}")
    private URI jenkinsServerUri;

    public JenkinsJobService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
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
     * Gets the xml config of the job that is inside a folder and replaces the old reference to the master and main branch by a reference to the default branch
     *
     * @param folderName the name of the folder
     * @param jobName    the name of the job
     * @return the xml document
     */
    public Document getJobConfig(String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            if (folder == null) {
                throw new JenkinsException("The folder " + folderName + " does not exist.");
            }

            URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, folderName, jobName).build(true).toUri();
            String xmlString = restTemplate.getForObject(uri, String.class);

            // Replace the old reference to the master and main branch by a reference to the default branch
            xmlString = xmlString.replace("*/master", "**");
            xmlString = xmlString.replace("*/main", "**");

            return JenkinsXmlFileUtils.readFromString(xmlString);
        }
        catch (HttpClientErrorException.NotFound notFound) {
            log.error("Job with jobName {} in folder {} does not exist in Jenkins", jobName, folderName);
            throw new JenkinsException(notFound.getMessage(), notFound);
        }
        catch (RestClientException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Gets the xml config of the folder job.
     *
     * @param folderName the name of the folder
     * @return the xml document or null if the folder doesn't exist
     * @throws IOException in case of errors
     */
    public Document getFolderConfig(String folderName) throws IOException {
        if (getFolderJob(folderName) == null) {
            return null;
        }

        URI uri = JenkinsEndpoints.FOLDER_CONFIG.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
        String folderXml = restTemplate.getForObject(uri, String.class);
        return JenkinsXmlFileUtils.readFromString(folderXml);
    }

    /**
     * Creates a new folder in Jenkins.
     *
     * @param projectKey The name of the folder.
     */
    public void createFolder(String projectKey) {
        //@formatter:off
        URI uri = JenkinsEndpoints.NEW_FOLDER.buildEndpoint(jenkinsServerUri)
            .queryParam("name", projectKey)
            .queryParam("mode", "com.cloudbees.hudson.plugins.folder.Folder")
            .queryParam("from", "")
            .queryParam("Submit", "OK")
            .build(true).toUri();
        //@formatter:on
        restTemplate.postForEntity(uri, new HttpEntity<>(null, new HttpHeaders()), Void.class);
    }

    /**
     * Creates a job inside a folder
     *
     * @param jobConfig  the config of the job to create
     * @param folderName the name of the folder
     * @param jobName    the name of the job
     */
    public void createJobInFolder(Document jobConfig, String folderName, String jobName) {
        try {
            var folder = getFolderJob(folderName);
            if (folder == null) {
                throw new JenkinsException("Cannot create job " + jobName + " because the folder " + folderName + " does not exist.");
            }

            var existingJob = getJob(folderName, jobName);
            if (existingJob != null) {
                log.info("Build Plan {} already exists. Skipping creation of job.", jobName);
                return;
            }

            URI uri = JenkinsEndpoints.NEW_PLAN.buildEndpoint(jenkinsServerUri, folderName).queryParam("name", jobName).build(true).toUri();

            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            String jobXmlString = JenkinsXmlFileUtils.writeToString(jobConfig);
            final var entity = new HttpEntity<>(jobXmlString, headers);

            restTemplate.postForEntity(uri, entity, Void.class);
        }
        catch (RestClientException | TransformerException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Updates a job.
     *
     * @param folderName optional folder name where the job resides (project key)
     * @param jobName    the name of the job (build plan key)
     * @param jobConfig  the updated job config
     */
    public void updateJob(String folderName, String jobName, Document jobConfig) {
        final var errorMessage = "Error trying to configure build plan in Jenkins " + jobName;
        try {
            URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, folderName, jobName).build(true).toUri();

            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            String jobXmlString = JenkinsXmlFileUtils.writeToString(jobConfig);
            final var entity = new HttpEntity<>(jobXmlString, headers);

            restTemplate.postForEntity(uri, entity, String.class);
        }
        catch (HttpClientErrorException.NotFound e) {
            // We don't throw an exception if the project doesn't exist in Jenkins (404 status)
            log.warn("updateJob {} does not exist in Jenkins: {}", jobName, e.getMessage());
            throw new JenkinsException(errorMessage, e);
        }
        catch (RestClientException | TransformerException e) {
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }

    /**
     * Updates the xml description of the folder job.
     *
     * @param folderName   the name of the folder
     * @param folderConfig the xml document of the folder
     * @throws IOException in case of errors
     */
    public void updateFolderJob(String folderName, Document folderConfig) throws IOException {
        try {
            URI uri = JenkinsEndpoints.FOLDER_CONFIG.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();

            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            String jobXmlString = JenkinsXmlFileUtils.writeToString(folderConfig);
            final var entity = new HttpEntity<>(jobXmlString, headers);

            restTemplate.postForEntity(uri, entity, Void.class);
        }
        catch (HttpClientErrorException.NotFound e) {
            // We don't throw an exception if the project doesn't exist in Jenkins (404 status)
            log.warn("updateFolderJob {} does not exist in Jenkins. Skipping deletion: {}", folderName, e.getMessage());
            throw new JenkinsException("Error while trying to update folder job in Jenkins for " + folderName, e);
        }
        catch (TransformerException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Deletes a job in Jenkins.
     *
     * @param folderName The name of the folder the job is in.
     * @param jobName    The name of the job itself.
     */
    public void deleteJob(String folderName, String jobName) {
        try {
            URI uri = JenkinsEndpoints.DELETE_JOB.buildEndpoint(jenkinsServerUri, folderName, jobName).build(true).toUri();
            restTemplate.postForEntity(uri, new HttpEntity<>(null, new HttpHeaders()), Void.class);
        }
        catch (HttpClientErrorException.NotFound e) {
            // We don't throw an exception if the project doesn't exist in Jenkins (404 status)
            log.warn("Job {} in folder {} does not exist in Jenkins. Skipping deletion: {}", jobName, folderName, e.getMessage());
        }
        catch (RestClientException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to delete folder job in Jenkins for " + folderName, e);
        }
    }

    /**
     * Deletes the job from Jenkins. Doesn't do anything if the job doesn't exist.
     *
     * @param folderName the name of the folder (project) to delete.
     */
    public void deleteFolderJob(String folderName) {
        try {
            URI uri = JenkinsEndpoints.DELETE_FOLDER.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
            restTemplate.postForEntity(uri, new HttpEntity<>(null, new HttpHeaders()), Void.class);
        }
        catch (HttpClientErrorException.NotFound e) {
            // We don't throw an exception if the project doesn't exist in Jenkins (404 status)
            log.warn("Folder job {} does not exist in Jenkins. Skipping deletion: {}", folderName, e.getMessage());
        }
        catch (RestClientException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to delete folder job in Jenkins for " + folderName, e);
        }
    }
}
