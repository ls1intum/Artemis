package de.tum.cit.aet.artemis.jenkins.connector.service;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;

/**
 * Service for interacting with Jenkins jobs and builds.
 * 
 * EXTRACTED FROM: JenkinsJobService.java in Artemis core
 * Contains Jenkins job management logic moved to the connector microservice.
 */
@Service
public class JenkinsJobService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsJobService.class);

    private final RestTemplate restTemplate;

    @Value("${jenkins.url}")
    private URI jenkinsServerUri;

    @Value("${jenkins.username}")
    private String jenkinsUsername;

    @Value("${jenkins.password}")
    private String jenkinsPassword;

    public JenkinsJobService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Creates a folder in Jenkins.
     * 
     * EXTRACTED FROM: createFolder() method in original JenkinsJobService
     */
    public void createFolder(String folderName) {
        try {
            log.debug("Creating Jenkins folder: {}", folderName);
            
            URI uri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/createItem")
                .queryParam("name", folderName)
                .queryParam("mode", "com.cloudbees.hudson.plugins.folder.Folder")
                .queryParam("from", "")
                .queryParam("Submit", "OK")
                .build(true).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            restTemplate.postForEntity(uri, entity, Void.class);
            log.info("Successfully created Jenkins folder: {}", folderName);
        } catch (RestClientException e) {
            log.error("Failed to create Jenkins folder: {}", folderName, e);
            throw new RuntimeException("Failed to create Jenkins folder", e);
        }
    }

    /**
     * Checks if a Jenkins job exists.
     */
    public boolean jobExists(String folderName, String jobName) {
        try {
            log.debug("Checking if Jenkins job exists: {}/{}", folderName, jobName);
            
            URI uri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{folderName}/job/{jobName}/api/json")
                .buildAndExpand(folderName, jobName).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<JenkinsJobDetails> response = restTemplate.exchange(uri, HttpMethod.GET, entity, JenkinsJobDetails.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("Jenkins job does not exist: {}/{}", folderName, jobName);
            return false;
        } catch (RestClientException e) {
            log.error("Error checking job existence: {}/{}", folderName, jobName, e);
            return false;
        }
    }

    /**
     * Creates a Jenkins job for a participation.
     * 
     * EXTRACTED FROM: createJobInFolder() and related methods
     */
    public void createJob(String folderName, String jobName, BuildTriggerRequestDTO request) {
        try {
            log.debug("Creating Jenkins job: {}/{}", folderName, jobName);
            
            // Generate job configuration XML
            String jobConfig = generateJobConfig(request);
            
            // Create job via Jenkins API
            URI uri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{folderName}/createItem")
                .queryParam("name", jobName)
                .buildAndExpand(folderName).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            
            HttpEntity<String> entity = new HttpEntity<>(jobConfig, headers);
            restTemplate.postForEntity(uri, entity, Void.class);
            
            log.info("Created Jenkins job: {}/{}", folderName, jobName);
        } catch (RestClientException e) {
            log.error("Failed to create Jenkins job: {}/{}", folderName, jobName, e);
            throw new RuntimeException("Failed to create Jenkins job", e);
        }
    }

    /**
     * Updates repository configuration for an existing job.
     */
    public void updateJobRepositories(String folderName, String jobName, BuildTriggerRequestDTO request) {
        try {
            log.debug("Updating repositories for Jenkins job: {}/{}", folderName, jobName);
            
            // Get current job configuration
            URI getConfigUri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{folderName}/job/{jobName}/config.xml")
                .buildAndExpand(folderName, jobName).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            
            ResponseEntity<String> configResponse = restTemplate.exchange(getConfigUri, HttpMethod.GET, getEntity, String.class);
            String currentConfig = configResponse.getBody();
            
            // Generate updated configuration with new repositories
            String updatedConfig = generateJobConfig(request);
            
            // Update job configuration
            URI updateConfigUri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{folderName}/job/{jobName}/config.xml")
                .buildAndExpand(folderName, jobName).toUri();
            
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> updateEntity = new HttpEntity<>(updatedConfig, headers);
            
            restTemplate.postForEntity(updateConfigUri, updateEntity, Void.class);
            log.info("Updated repositories for Jenkins job: {}/{}", folderName, jobName);
        } catch (RestClientException e) {
            log.error("Failed to update repositories for job: {}/{}", folderName, jobName, e);
            throw new RuntimeException("Failed to update job repositories", e);
        }
    }

    /**
     * Triggers a Jenkins build.
     * 
     * EXTRACTED FROM: triggerBuild() methods
     */
    public Integer triggerBuild(String folderName, String jobName, BuildTriggerRequestDTO request) {
        try {
            log.debug("Triggering Jenkins build for job: {}/{}", folderName, jobName);
            
            URI uri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{folderName}/job/{jobName}/build")
                .buildAndExpand(folderName, jobName).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            restTemplate.postForEntity(uri, entity, Void.class);
            
            // Get the latest build number after triggering
            Integer latestBuildNumber = getLatestBuildNumber(folderName, jobName);
            
            // Return the build number
            log.info("Triggered Jenkins build for job: {}/{} -> build #{}", folderName, jobName, latestBuildNumber);
            return latestBuildNumber;
        } catch (RestClientException e) {
            log.error("Failed to trigger build for job: {}/{}", folderName, jobName, e);
            throw new RuntimeException("Failed to trigger Jenkins build", e);
        }
    }

    /**
     * Gets build status from Jenkins.
     */
    public BuildStatusResponseDTO getBuildStatus(String jobName, Integer buildNumber) {
        try {
            log.debug("Getting build status for Jenkins job: {} build #{}", jobName, buildNumber);
            
            // First check if the job is in queue
            URI jobUri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{jobName}/api/json")
                .buildAndExpand(jobName).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<JenkinsJobDetails> jobResponse = restTemplate.exchange(jobUri, HttpMethod.GET, entity, JenkinsJobDetails.class);
            
            if (jobResponse.getBody() != null && jobResponse.getBody().inQueue) {
                return new BuildStatusResponseDTO(
                    "build-" + buildNumber,
                    BuildStatusResponseDTO.BuildStatus.QUEUED,
                    null,
                    null,
                    null
                );
            }
            
            // Get specific build status
            URI buildUri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{jobName}/{buildNumber}/api/json")
                .buildAndExpand(jobName, buildNumber).toUri();
            
            ResponseEntity<JenkinsBuildStatus> buildResponse = restTemplate.exchange(buildUri, HttpMethod.GET, entity, JenkinsBuildStatus.class);
            JenkinsBuildStatus buildStatus = buildResponse.getBody();
            
            if (buildStatus == null) {
                return null;
            }
            
            BuildStatusResponseDTO.BuildStatus status;
            if (buildStatus.building) {
                status = BuildStatusResponseDTO.BuildStatus.RUNNING;
            } else if ("SUCCESS".equals(buildStatus.result)) {
                status = BuildStatusResponseDTO.BuildStatus.SUCCESS;
            } else if ("FAILURE".equals(buildStatus.result) || "UNSTABLE".equals(buildStatus.result)) {
                status = BuildStatusResponseDTO.BuildStatus.FAILED;
            } else {
                status = BuildStatusResponseDTO.BuildStatus.CANCELLED;
            }
            
            Instant startTime = buildStatus.timestamp != null ? Instant.ofEpochMilli(buildStatus.timestamp) : null;
            Instant endTime = !buildStatus.building && buildStatus.timestamp != null ? 
                Instant.ofEpochMilli(buildStatus.timestamp + 60000) : null; // Estimate end time
            
            return new BuildStatusResponseDTO(
                "build-" + buildNumber,
                status,
                startTime,
                endTime,
                "FAILURE".equals(buildStatus.result) ? "Build failed" : null
            );
        } catch (RestClientException e) {
            log.error("Failed to get build status for job: {} build #{}", jobName, buildNumber, e);
            return null;
        }
    }

    /**
     * Generates Jenkins job configuration XML.
     */
    private String generateJobConfig(BuildTriggerRequestDTO request) {
        // Generate pipeline job XML with the provided build script
        String pipelineScript = request.buildScript();
        
        // Escape XML characters in the script
        pipelineScript = pipelineScript
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
        
        return String.format("""
            <?xml version='1.1' encoding='UTF-8'?>
            <flow-definition plugin="workflow-job">
                <actions>
                    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin="pipeline-model-definition"/>
                </actions>
                <description>Artemis CI Build for Exercise %d, Participation %d</description>
                <keepDependencies>false</keepDependencies>
                <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps">
                    <script>%s</script>
                    <sandbox>true</sandbox>
                </definition>
                <triggers/>
                <disabled>false</disabled>
            </flow-definition>
            """, request.exerciseId(), request.participationId(), pipelineScript);
    }
    
    /**
     * Gets the latest build number for a job.
     */
    private Integer getLatestBuildNumber(String folderName, String jobName) {
        try {
            URI uri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/job/{folderName}/job/{jobName}/lastBuild/api/json")
                .buildAndExpand(folderName, jobName).toUri();
            
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<JenkinsBuildStatus> response = restTemplate.exchange(uri, HttpMethod.GET, entity, JenkinsBuildStatus.class);
            return response.getBody() != null ? response.getBody().number : 1;
        } catch (Exception e) {
            log.warn("Could not get latest build number for {}/{}, defaulting to 1: {}", folderName, jobName, e.getMessage());
            return 1;
        }
    }

    /**
     * Creates HTTP headers with Jenkins authentication.
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        // Add Basic Authentication
        String credentials = jenkinsUsername + ":" + jenkinsPassword;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        headers.set("Authorization", "Basic " + encodedCredentials);
        
        // Add CSRF protection (Jenkins Crumb)
        try {
            setCrumbHeader(headers);
        } catch (Exception e) {
            log.warn("Failed to set Jenkins crumb header: {}", e.getMessage());
        }
        
        return headers;
    }
    
    /**
     * Sets Jenkins CSRF protection header (crumb).
     */
    private void setCrumbHeader(HttpHeaders headers) {
        try {
            URI crumbUri = UriComponentsBuilder.fromUri(jenkinsServerUri)
                .path("/crumbIssuer/api/json")
                .build().toUri();
            
            HttpHeaders crumbHeaders = new HttpHeaders();
            String credentials = jenkinsUsername + ":" + jenkinsPassword;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            crumbHeaders.set("Authorization", "Basic " + encodedCredentials);
            
            HttpEntity<Void> entity = new HttpEntity<>(crumbHeaders);
            ResponseEntity<JsonNode> response = restTemplate.exchange(crumbUri, HttpMethod.GET, entity, JsonNode.class);
            
            if (response.getBody() != null && response.getBody().has("crumb")) {
                String crumb = response.getBody().get("crumb").asText();
                headers.add("Jenkins-Crumb", crumb);
                
                // Add session cookie if present
                if (response.getHeaders().containsKey("Set-Cookie")) {
                    String cookie = response.getHeaders().getFirst("Set-Cookie");
                    if (cookie != null) {
                        headers.add("Cookie", cookie);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Jenkins crumb not available or failed to retrieve: {}", e.getMessage());
        }
    }
    
    /**
     * DTO for Jenkins job details response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class JenkinsJobDetails {
        public boolean inQueue;
        public String name;
        public String url;
    }
    
    /**
     * DTO for Jenkins build status response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class JenkinsBuildStatus {
        public boolean building;
        public String result;
        public Integer number;
        public Long timestamp;
    }
}