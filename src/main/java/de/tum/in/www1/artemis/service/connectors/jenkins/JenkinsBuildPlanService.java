package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;

@Service
@Profile("jenkins")
public class JenkinsBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsBuildPlanService.class);

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final RestTemplate restTemplate;

    private final JenkinsServer jenkinsServer;

    private final JenkinsBuildPlanCreator jenkinsBuildPlanCreator;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsJobPermissionsService jenkinsJobPermissionsService;

    private final UserRepository userRepository;

    public JenkinsBuildPlanService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsServer jenkinsServer, JenkinsBuildPlanCreator jenkinsBuildPlanCreator,
            JenkinsJobService jenkinsJobService, JenkinsJobPermissionsService jenkinsJobPermissionsService, UserRepository userRepository) {
        this.restTemplate = restTemplate;
        this.jenkinsServer = jenkinsServer;
        this.jenkinsBuildPlanCreator = jenkinsBuildPlanCreator;
        this.jenkinsJobService = jenkinsJobService;
        this.userRepository = userRepository;
        this.jenkinsJobPermissionsService = jenkinsJobPermissionsService;
    }

    public void createBuildPlanForProgrammingLanguage(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL) {
        try {
            // TODO support sequential test runs
            var programmingLanguage = exercise.getProgrammingLanguage();
            final var configBuilder = builderFor(programmingLanguage);
            Document jobConfig = configBuilder.buildBasicConfig(programmingLanguage, testRepositoryURL, repositoryURL, Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()));

            planKey = exercise.getProjectKey() + "-" + planKey;
            jenkinsServer.createJob(jenkinsJobService.getFolderJob(exercise.getProjectKey()), planKey, jenkinsJobService.writeXmlToString(jobConfig), useCrumb);
            jenkinsJobService.getJobInFolder(exercise.getProjectKey(), planKey).build(useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Unable to create new build plan :" + planKey, e);
        }
    }

    /**
     * Gives a Jenkins plan builder, that is able to build plan configurations for the specified programming language
     *
     * @param programmingLanguage The programming language for which a build plan should get created
     * @return The configuration builder for the specified language
     * @see JenkinsBuildPlanCreator
     */
    private JenkinsXmlConfigBuilder builderFor(ProgrammingLanguage programmingLanguage) {
        return switch (programmingLanguage) {
            case JAVA, KOTLIN, PYTHON, C, HASKELL, SWIFT -> jenkinsBuildPlanCreator;
            case VHDL -> throw new UnsupportedOperationException("VHDL templates are not available for Jenkins.");
            case ASSEMBLER -> throw new UnsupportedOperationException("Assembler templates are not available for Jenkins.");
        };
    }

    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        final var cleanTargetName = getCleanPlanName(targetPlanName);
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        final var targetPlanKey = targetProjectKey + "-" + cleanTargetName;
        final var jobXml = jenkinsJobService.getJobConfigForJobInFolder(sourceProjectKey, sourcePlanKey);
        jenkinsJobService.createJobInFolder(jobXml, targetProjectKey, targetPlanKey);

        return targetPlanKey;
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public void triggerBuild(String projectKey, String planKey) {
        try {
            jenkinsJobService.getJobInFolder(projectKey, planKey).build(useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error triggering build: " + planKey, e);
        }
    }

    public void deleteBuildPlan(String projectKey, String planKey) {
        try {
            jenkinsServer.deleteJob(jenkinsJobService.getFolderJob(projectKey), planKey, useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to delete job in Jenkins: " + planKey, e);
        }
    }

    public ContinuousIntegrationService.BuildStatus getBuildStatusOfPlan(String projectKey, String planKey) throws JenkinsException {
        var job = jenkinsJobService.getJobInFolder(projectKey, planKey);
        if (job == null) {
            // TODO: Throw exception or fail silently?
            // Plan doesn't exist.
            return null;
        }

        if (job.isInQueue()) {
            return ContinuousIntegrationService.BuildStatus.QUEUED;
        }

        try {
            var uri = UriComponentsBuilder.fromHttpUrl(serverUrl.toString()).pathSegment("job", projectKey, "job", planKey, "lastBuild", "api", "json").build().toUri();
            var response = restTemplate.getForObject(uri, JsonNode.class);
            if (response != null) {
                var isJobBuilding = response.get("building").asBoolean();
                return isJobBuilding ? ContinuousIntegrationService.BuildStatus.BUILDING : ContinuousIntegrationService.BuildStatus.INACTIVE;
            }
            else {
                // TODO: Throw exception or fail silently?
                // Couldn't fetch build status
                return null;
            }
        }
        catch (HttpClientErrorException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to fetch build status from Jenkins for " + planKey, e);
        }
    }

    public boolean isBuildPlanEnabled(String projectKey, String planId) {
        return jenkinsJobService.getJobInFolder(projectKey, planId).isBuildable();
    }

    public boolean buildPlanExists(String projectKey, String buildPlanId) {
        try {
            var planExists = jenkinsJobService.getJobInFolder(projectKey, buildPlanId);
            return planExists != null;
        }
        catch (Exception emAll) {
            return false;
        }
    }

    /**
     * Assigns access permissions to instructors and TAs for the specified build plan.
     * This is done by getting all users that belong to the instructor and TA groups of
     * the exercise' course and adding permissions to the Jenkins job.
     *
     * @param programmingExercise the programming exercise
     * @param planName
     */
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        try {
            // Retrieve the TAs and instructors that will be given access to the plan of the programming exercise
            Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
            var teachingAssistants = userRepository.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
            var instructors = userRepository.findAllInGroupWithAuthorities(course.getInstructorGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());

            // The build plan of the exercise is inside the course folder
            var jobFolder = programmingExercise.getProjectKey();
            var jobName = jobFolder + "-" + planName;
            jenkinsJobPermissionsService.addInstructorAndTAPermissionsToUsersForJob(teachingAssistants, instructors, jobFolder, jobName);
        }
        catch (IOException e) {
            throw new JenkinsException("Cannot give assign permissions to plan" + planName, e);
        }
    }

    public void enablePlan(String projectKey, String planKey) {
        try {
            var uri = UriComponentsBuilder.fromHttpUrl(serverUrl.toString()).pathSegment("job", projectKey, "job", planKey, "enable").build(true).toUri();
            var response = restTemplate.postForEntity(uri, null, String.class);
            if (response.getStatusCode() != HttpStatus.FOUND) {
                throw new JenkinsException(
                        "Unable to enable plan " + planKey + "; statusCode=" + response.getStatusCode() + "; headers=" + response.getHeaders() + "; body=" + response.getBody());
            }
        }
        catch (HttpClientErrorException e) {
            throw new JenkinsException("Unable to enable plan " + planKey, e);
        }
    }
}
