package de.tum.cit.aet.artemis.jenkins.connector.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.jenkins.connector.domain.BuildRecord;
import de.tum.cit.aet.artemis.jenkins.connector.domain.JenkinsProject;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildStatusResponseDTO;
import de.tum.cit.aet.artemis.jenkins.connector.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.jenkins.connector.repository.BuildRecordRepository;
import de.tum.cit.aet.artemis.jenkins.connector.repository.JenkinsProjectRepository;

/**
 * Service for managing Jenkins builds in the connector microservice.
 * 
 * EXTRACTED FROM: JenkinsBuildPlanService.java in Artemis core
 * Contains core build management logic moved to the stateless connector.
 */
@Service
public class JenkinsBuildService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsBuildService.class);

    private final BuildRecordRepository buildRecordRepository;
    private final JenkinsProjectRepository jenkinsProjectRepository;
    private final JenkinsJobService jenkinsJobService;

    public JenkinsBuildService(BuildRecordRepository buildRecordRepository,
                              JenkinsProjectRepository jenkinsProjectRepository,
                              JenkinsJobService jenkinsJobService) {
        this.buildRecordRepository = buildRecordRepository;
        this.jenkinsProjectRepository = jenkinsProjectRepository;
        this.jenkinsJobService = jenkinsJobService;
    }

    /**
     * Triggers a build for the given request in a stateless manner.
     * Will create Jenkins projects/jobs if they don't exist.
     * 
     * EXTRACTED FROM: createBuildPlanForExercise() and configureBuildPlanForParticipation()
     * 
     * @param request the build trigger request
     * @return the build UUID for tracking
     */
    public UUID triggerBuild(BuildTriggerRequestDTO request) {
        log.debug("Triggering build for exercise {} participation {}", request.exerciseId(), request.participationId());

        // Generate build UUID
        UUID buildUuid = UUID.randomUUID();

        // Find or create Jenkins project
        JenkinsProject jenkinsProject = findOrCreateJenkinsProject(request);

        // Generate job name for this participation
        String jobName = generateJobName(jenkinsProject.getJenkinsFolderName(), request.participationId());

        // Create/configure Jenkins job if needed
        ensureJenkinsJobExists(jenkinsProject, jobName, request);

        // Create build record
        BuildRecord buildRecord = new BuildRecord(buildUuid, request.exerciseId(), request.participationId(), jobName, request.programmingLanguage());
        buildRecord.setCommitHash(request.exerciseRepository().commitHash());
        buildRecordRepository.save(buildRecord);

        // Trigger actual Jenkins build
        Integer jenkinsBuildNumber = jenkinsJobService.triggerBuild(jenkinsProject.getJenkinsFolderName(), jobName, request);

        // Update build record with Jenkins info
        buildRecord.setJenkinsBuildNumber(jenkinsBuildNumber);
        buildRecordRepository.save(buildRecord);

        log.info("Triggered build {} for exercise {} participation {} -> Jenkins job: {}/{}",
                buildUuid, request.exerciseId(), request.participationId(), jenkinsProject.getJenkinsFolderName(), jobName);

        return buildUuid;
    }

    /**
     * Gets the current build status from Jenkins.
     * 
     * @param buildUuid the build UUID
     * @return the current status from Jenkins, or null if not found
     */
    public BuildStatusResponseDTO getBuildStatus(UUID buildUuid) {
        return buildRecordRepository.findByBuildUuid(buildUuid)
                .map(buildRecord -> {
                    // Fetch live status from Jenkins
                    return jenkinsJobService.getBuildStatus(buildRecord.getJenkinsJobName(), buildRecord.getJenkinsBuildNumber());
                })
                .orElse(null);
    }

    /**
     * Finds existing Jenkins project or creates a new one.
     * 
     * EXTRACTED FROM: createProjectForExercise() logic
     */
    private JenkinsProject findOrCreateJenkinsProject(BuildTriggerRequestDTO request) {
        return jenkinsProjectRepository.findByExerciseId(request.exerciseId())
                .orElseGet(() -> {
                    String projectKey = "EX" + request.exerciseId(); // Simple project key generation
                    String folderName = projectKey;
                    
                    // Create Jenkins folder
                    jenkinsJobService.createFolder(folderName);
                    
                    // Create and save project record
                    JenkinsProject project = new JenkinsProject(request.exerciseId(), projectKey, folderName, request.programmingLanguage());
                    return jenkinsProjectRepository.save(project);
                });
    }

    /**
     * Generates a job name for a participation.
     */
    private String generateJobName(String folderName, Long participationId) {
        return folderName + "-" + participationId;
    }

    /**
     * Ensures a Jenkins job exists for the participation.
     * 
     * EXTRACTED FROM: configureBuildPlanForParticipation() and related methods
     */
    private void ensureJenkinsJobExists(JenkinsProject project, String jobName, BuildTriggerRequestDTO request) {
        if (!jenkinsJobService.jobExists(project.getJenkinsFolderName(), jobName)) {
            log.debug("Creating Jenkins job: {}/{}", project.getJenkinsFolderName(), jobName);
            jenkinsJobService.createJob(project.getJenkinsFolderName(), jobName, request);
        } else {
            log.debug("Jenkins job already exists: {}/{}", project.getJenkinsFolderName(), jobName);
            // Update repositories if needed
            jenkinsJobService.updateJobRepositories(project.getJenkinsFolderName(), jobName, request);
        }
    }
}