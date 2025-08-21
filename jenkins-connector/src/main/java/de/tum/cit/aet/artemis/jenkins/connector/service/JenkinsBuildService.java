package de.tum.cit.aet.artemis.jenkins.connector.service;

import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.jenkins.connector.domain.BuildRecord;
import de.tum.cit.aet.artemis.jenkins.connector.domain.JenkinsProject;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO.BuildStatus;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.jenkins.connector.repository.BuildRecordRepository;
import de.tum.cit.aet.artemis.jenkins.connector.repository.JenkinsProjectRepository;
import de.tum.cit.aet.artemis.jenkins.connector.service.JenkinsJobService.JenkinsException;

/**
 * Service for managing Jenkins builds and integrating with the Jenkins API.
 */
@Service
@Transactional
public class JenkinsBuildService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsBuildService.class);

    private final JenkinsJobService jenkinsJobService;
    private final BuildRecordRepository buildRecordRepository;
    private final JenkinsProjectRepository jenkinsProjectRepository;
    private final RestTemplate restTemplate;

    @Value("${jenkins.url}")
    private URI jenkinsServerUri;

    public JenkinsBuildService(JenkinsJobService jenkinsJobService, 
                              BuildRecordRepository buildRecordRepository,
                              JenkinsProjectRepository jenkinsProjectRepository,
                              RestTemplate restTemplate) {
        this.jenkinsJobService = jenkinsJobService;
        this.buildRecordRepository = buildRecordRepository;
        this.jenkinsProjectRepository = jenkinsProjectRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Triggers a new build based on the given build trigger request.
     *
     * @param buildTriggerRequest the build trigger request containing all necessary information
     * @return the UUID of the created build
     * @throws JenkinsException if the build cannot be triggered
     */
    public UUID triggerBuild(BuildTriggerRequestDTO buildTriggerRequest) throws JenkinsException {
        log.info("Triggering build for exercise {} and participation {}", 
                buildTriggerRequest.exerciseId(), buildTriggerRequest.participationId());

        try {
            // Generate unique build ID
            UUID buildId = UUID.randomUUID();
            
            // Get or create Jenkins project
            JenkinsProject jenkinsProject = getOrCreateJenkinsProject(buildTriggerRequest.exerciseId());
            
            // Create build record
            BuildRecord buildRecord = new BuildRecord(buildId, buildTriggerRequest.exerciseId(), 
                    buildTriggerRequest.participationId(), BuildStatus.QUEUED);
            buildRecord.setJenkinsProject(jenkinsProject);
            
            // Generate job name based on participation
            String jobName = generateJobName(buildTriggerRequest.participationId());
            buildRecord.setJenkinsJobName(jobName);
            
            // Save build record
            buildRecord = buildRecordRepository.save(buildRecord);
            
            // Create the Jenkins job if it doesn't exist
            createJenkinsJobIfNeeded(jenkinsProject.getJenkinsFolderName(), jobName, buildTriggerRequest);
            
            // Trigger the build in Jenkins
            Integer jenkinsBuildNumber = jenkinsJobService.triggerBuild(jenkinsProject.getJenkinsFolderName(), jobName);
            if (jenkinsBuildNumber != null) {
                buildRecord.setJenkinsBuildNumber(jenkinsBuildNumber);
            }
            
            // Update status to running
            buildRecord.setStatus(BuildStatus.RUNNING);
            buildRecordRepository.save(buildRecord);
            
            log.info("Successfully triggered build with ID: {}", buildId);
            return buildId;
            
        } catch (Exception e) {
            log.error("Failed to trigger build for exercise {} and participation {}", 
                    buildTriggerRequest.exerciseId(), buildTriggerRequest.participationId(), e);
            throw new JenkinsException("Failed to trigger build: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the status of a build by its UUID.
     *
     * @param buildId the UUID of the build
     * @return the build status, or null if not found
     */
    @Transactional(readOnly = true)
    public BuildStatus getBuildStatus(UUID buildId) {
        return buildRecordRepository.findByBuildId(buildId)
                .map(BuildRecord::getStatus)
                .orElse(null);
    }

    /**
     * Gets the latest build status for a participation.
     *
     * @param participationId the participation ID
     * @return the build status, or null if no builds found
     */
    @Transactional(readOnly = true)
    public BuildStatus getLatestBuildStatusForParticipation(Long participationId) {
        return buildRecordRepository.findByParticipationIdOrderByCreatedAtDesc(participationId)
                .map(BuildRecord::getStatus)
                .orElse(null);
    }

    /**
     * Gets or creates a Jenkins project for the given exercise.
     */
    private JenkinsProject getOrCreateJenkinsProject(Long exerciseId) throws JenkinsException {
        return jenkinsProjectRepository.findByExerciseId(exerciseId)
                .orElseGet(() -> {
                    String projectKey = generateProjectKey(exerciseId);
                    String jenkinsFolderName = projectKey;
                    
                    // Create folder in Jenkins
                    jenkinsJobService.createFolder(jenkinsFolderName);
                    
                    // Save project in database
                    JenkinsProject project = new JenkinsProject(projectKey, exerciseId, jenkinsFolderName);
                    return jenkinsProjectRepository.save(project);
                });
    }

    /**
     * Creates a Jenkins job if it doesn't already exist.
     */
    private void createJenkinsJobIfNeeded(String folderName, String jobName, BuildTriggerRequestDTO buildRequest) throws JenkinsException {
        // Check if job already exists
        var existingJob = jenkinsJobService.getJob(folderName, jobName);
        if (existingJob != null) {
            log.debug("Job {}/{} already exists", folderName, jobName);
            return;
        }

        // Create the job
        // TODO: Implement job creation with proper build configuration
        // This would involve creating the Jenkins job XML configuration based on the build request
        log.info("Would create Jenkins job {}/{} with programming language {}", 
                folderName, jobName, buildRequest.programmingLanguage());
    }

    private String generateProjectKey(Long exerciseId) {
        return "EXERCISE-" + exerciseId;
    }

    private String generateJobName(Long participationId) {
        return "participation-" + participationId;
    }
}