package de.tum.cit.aet.artemis.programming.service.jenkins.build_plan;

import static de.tum.cit.aet.artemis.core.config.Constants.NEW_RESULT_RESOURCE_API_PATH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationBuildPlanException;
import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.programming.domain.AeolusTarget;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.aeolus.AeolusRepository;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.dto.aeolus.WindfileMetadata;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusBuildPlanService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.TestResultsDTO;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsEndpoints;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsInternalUrlService;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsXmlConfigBuilder;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;

@Lazy
@Service
@Profile(PROFILE_JENKINS)
// TODO: EXTRACTED TO MICROSERVICE - This class has been copied to jenkins-connector/src/main/java/de/tum/cit/aet/artemis/jenkins/connector/service/JenkinsBuildService.java
// This code will be removed once the microservice migration is complete
public class JenkinsBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsBuildPlanService.class);

    @Value("${artemis.continuous-integration.url}")
    private URI jenkinsServerUri;

    private final RestTemplate restTemplate;

    private final JenkinsBuildPlanCreator jenkinsBuildPlanCreator;

    private final JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsInternalUrlService jenkinsInternalUrlService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final BuildPlanRepository buildPlanRepository;

    private final Optional<AeolusBuildPlanService> aeolusBuildPlanService;

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Value("${artemis.continuous-integration.artemis-authentication-token-key}")
    private String artemisAuthenticationTokenKey;

    @Value("${artemis.continuous-integration.vcs-credentials}")
    private String vcsCredentials;

    public JenkinsBuildPlanService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsBuildPlanCreator jenkinsBuildPlanCreator,
            JenkinsJobService jenkinsJobService, JenkinsInternalUrlService jenkinsInternalUrlService, ProgrammingExerciseRepository programmingExerciseRepository,
            JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator, BuildPlanRepository buildPlanRepository, Optional<AeolusBuildPlanService> aeolusBuildPlanService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.restTemplate = restTemplate;
        this.jenkinsBuildPlanCreator = jenkinsBuildPlanCreator;
        this.jenkinsJobService = jenkinsJobService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.jenkinsInternalUrlService = jenkinsInternalUrlService;
        this.jenkinsPipelineScriptCreator = jenkinsPipelineScriptCreator;
        this.buildPlanRepository = buildPlanRepository;
        this.aeolusBuildPlanService = aeolusBuildPlanService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    /**
     * Creates a build plan for the programming exercise
     *
     * @param exercise      the programming exercise
     * @param planKey       the name of the plan
     * @param repositoryUri the uri of the vcs repository
     */
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri) {
        final JenkinsXmlConfigBuilder.InternalVcsRepositoryURLs internalRepositoryUris = getInternalRepositoryUris(exercise, repositoryUri);
        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(exercise);

        final ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        final var configBuilder = builderFor(programmingLanguage, exercise.getProjectType());
        final String buildPlanUrl = jenkinsPipelineScriptCreator.generateBuildPlanURL(exercise);
        final boolean checkoutSolution = exercise.getBuildConfig().getCheckoutSolutionRepository();
        final Document jobConfig = configBuilder.buildBasicConfig(programmingLanguage, internalRepositoryUris, checkoutSolution, buildPlanUrl);

        final String jobFolder = exercise.getProjectKey();
        String job = jobFolder + "-" + planKey;
        boolean couldCreateBuildPlan = false;

        if (aeolusBuildPlanService.isPresent() && exercise.getBuildConfig().getBuildPlanConfiguration() != null) {
            var createdJob = createCustomAeolusBuildPlanForExercise(exercise, jobFolder + "/" + job, internalRepositoryUris.assignmentRepositoryUri(),
                    internalRepositoryUris.testRepositoryUri(), internalRepositoryUris.solutionRepositoryUri());
            couldCreateBuildPlan = createdJob != null;
        }
        if (!couldCreateBuildPlan) {
            // create build plan in database first, otherwise the job in Jenkins cannot find it for the initial build
            jenkinsPipelineScriptCreator.createBuildPlanForExercise(exercise);
            jenkinsJobService.createJobInFolder(jobConfig, jobFolder, job);
        }

        triggerBuild(jobFolder, job);
    }

    private JenkinsXmlConfigBuilder.InternalVcsRepositoryURLs getInternalRepositoryUris(final ProgrammingExercise exercise, final VcsRepositoryUri assignmentRepositoryUri) {
        final VcsRepositoryUri assignmentUrl = jenkinsInternalUrlService.toInternalVcsUrl(assignmentRepositoryUri);
        final VcsRepositoryUri testUrl = jenkinsInternalUrlService.toInternalVcsUrl(exercise.getRepositoryURI(RepositoryType.TESTS));
        final VcsRepositoryUri solutionUrl = jenkinsInternalUrlService.toInternalVcsUrl(exercise.getRepositoryURI(RepositoryType.SOLUTION));

        return new JenkinsXmlConfigBuilder.InternalVcsRepositoryURLs(assignmentUrl, testUrl, solutionUrl);
    }

    /**
     * Gives a Jenkins plan builder, that is able to build plan configurations for the specified programming language
     *
     * @param programmingLanguage The programming language for which a build plan should get created
     * @return The configuration builder for the specified language
     * @see JenkinsBuildPlanCreator
     */
    private JenkinsXmlConfigBuilder builderFor(ProgrammingLanguage programmingLanguage, ProjectType projectType) {
        if (ProjectType.XCODE.equals(projectType)) {
            throw new UnsupportedOperationException("Xcode templates are not available for Jenkins.");
        }
        return switch (programmingLanguage) {
            case JAVA, KOTLIN, PYTHON, C, HASKELL, SWIFT, EMPTY, RUST, JAVASCRIPT, R, C_PLUS_PLUS, TYPESCRIPT, C_SHARP, GO, BASH, RUBY, DART -> jenkinsBuildPlanCreator;
            case VHDL, ASSEMBLER, OCAML, SQL, MATLAB, POWERSHELL, ADA, PHP ->
                throw new UnsupportedOperationException(programmingLanguage + " templates are not available for Jenkins.");
        };
    }

    /**
     * Creates a new build plan, configures it for the specified participations and saves it in Jenkins.
     * The plan is enabled after it has been saved.
     *
     * @param participation the programming exercise participation
     */
    public void configureBuildPlanForParticipation(ProgrammingExerciseParticipation participation) {
        // Refetch the programming exercise with the template participation and assign it to programmingExerciseParticipation to make sure it is initialized (and not a proxy)
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(participation.getProgrammingExercise().getId())
                .orElseThrow();
        participation.setProgrammingExercise(programmingExercise);

        String projectKey = programmingExercise.getProjectKey();
        String planKey = participation.getBuildPlanId();
        String templateRepoUri = programmingExercise.getTemplateRepositoryUri();
        updateBuildPlanRepositories(projectKey, planKey, participation.getRepositoryUri(), templateRepoUri);
        enablePlan(projectKey, planKey);

        // Students currently always have access to the build plan in Jenkins
    }

    /**
     * Updates the repositories that are configured within the build plan with the specified new values.
     *
     * @param buildProjectKey the project key of the programming exercise
     * @param buildPlanKey    the build plan id of the participation
     * @param newRepoUri      the repository uri that will replace the old url
     * @param existingRepoUri the old repository uri that will be replaced
     */
    public void updateBuildPlanRepositories(String buildProjectKey, String buildPlanKey, String newRepoUri, String existingRepoUri) {
        newRepoUri = jenkinsInternalUrlService.toInternalVcsUrl(newRepoUri);
        existingRepoUri = jenkinsInternalUrlService.toInternalVcsUrl(existingRepoUri);

        // remove potential username from repo URI. Jenkins uses the Artemis Admin user and will fail if other usernames are in the URI
        final var repoUri = newRepoUri.replaceAll("(https?://)(.*@)(.*)", "$1$3");
        final Document jobConfig = jenkinsJobService.getJobConfig(buildProjectKey, buildPlanKey);

        try {
            JenkinsBuildPlanUtils.replaceScriptParameters(jobConfig, existingRepoUri, repoUri);
        }
        catch (IllegalArgumentException e) {
            log.error("Pipeline Script not found", e);
        }

        jenkinsJobService.updateJob(buildProjectKey, buildPlanKey, jobConfig);
    }

    /**
     * Replaces the old build plan URL with a new one containing an updated exercise and access token.
     *
     * @param templateExercise The exercise containing the old build plan URL.
     * @param newExercise      The exercise of which the build plan URL is updated.
     * @param jobConfig        The job config in Jenkins for the new exercise.
     */
    private void updateBuildPlanURLs(ProgrammingExercise templateExercise, ProgrammingExercise newExercise, Document jobConfig) {
        final Long previousExerciseId = templateExercise.getId();
        final String previousBuildPlanAccessSecret = templateExercise.getBuildConfig().getBuildPlanAccessSecret();
        final Long newExerciseId = newExercise.getId();
        final String newBuildPlanAccessSecret = newExercise.getBuildConfig().getBuildPlanAccessSecret();

        String toBeReplaced = String.format("/%d/build-plan?secret=%s", previousExerciseId, previousBuildPlanAccessSecret);
        String replacement = String.format("/%d/build-plan?secret=%s", newExerciseId, newBuildPlanAccessSecret);

        try {
            JenkinsBuildPlanUtils.replaceScriptParameters(jobConfig, toBeReplaced, replacement);
        }
        catch (IllegalArgumentException e) {
            log.error("Pipeline Script not found", e);
        }
    }

    /**
     * Returns the build plan key from the specified test results.
     *
     * @param testResultsDTO the test results from Jenkins
     * @return the build plan key
     */
    public String getBuildPlanKeyFromTestResults(TestResultsDTO testResultsDTO) throws JsonProcessingException {
        final var nameParams = testResultsDTO.fullName().split(" ");
        /*
         * Jenkins gives the full name of a job as <FOLDER NAME> » <JOB NAME> <Build Number> E.g. the third build of an exercise (projectKey = TESTEXC) for its solution build
         * (TESTEXC-SOLUTION) would be: TESTEXC » TESTEXC-SOLUTION #3 ==> This would mean that at index 2, we have the actual job/plan key, i.e. TESTEXC-SOLUTION
         */
        if (nameParams.length != 4) {
            var requestBodyString = new ObjectMapper().writeValueAsString(testResultsDTO);
            log.error("Can't extract planKey from requestBody! Not a test notification result!: {}", requestBodyString);
            throw new JenkinsException("Can't extract planKey from requestBody! Not a test notification result!: " + requestBodyString);
        }

        return nameParams[2];
    }

    /**
     * Copies a build plan to another and replaces the old reference to the master and main branch with a reference to the default branch
     *
     * @param sourceExercise the source exercise
     * @param sourcePlanName the source plan name
     * @param targetExercise the target exercise
     * @param targetPlanName the target plan name
     * @return the key of the created build plan
     */
    public String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetPlanName) {
        buildPlanRepository.copyBetweenExercises(sourceExercise, targetExercise);

        String sourceProjectKey = sourceExercise.getProjectKey();
        String targetProjectKey = targetExercise.getProjectKey();

        final var cleanTargetName = getCleanPlanName(targetPlanName);
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        final var targetPlanKey = targetProjectKey + "-" + cleanTargetName;
        final var jobXml = jenkinsJobService.getJobConfig(sourceProjectKey, sourcePlanKey);

        updateBuildPlanURLs(sourceExercise, targetExercise, jobXml);

        jenkinsJobService.createJobInFolder(jobXml, targetProjectKey, targetPlanKey);

        return targetPlanKey;
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Triggers the build for the plan
     *
     * @param projectKey the project key of the plan
     * @param planKey    the plan key
     */
    public void triggerBuild(String projectKey, String planKey) {
        try {
            URI uri = JenkinsEndpoints.TRIGGER_BUILD.buildEndpoint(jenkinsServerUri, projectKey, planKey).build(true).toUri();
            restTemplate.postForEntity(uri, new HttpEntity<>(null, new HttpHeaders()), Void.class);
        }
        catch (RestClientException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error triggering build: " + planKey, e);
        }
    }

    /**
     * Retrieves the build status of the plan
     *
     * @param projectKey the project key of the plan
     * @param planKey    the plan key
     * @return the build status
     * @throws JenkinsException thrown in case of errors
     */
    public ContinuousIntegrationService.BuildStatus getBuildStatusOfPlan(String projectKey, String planKey) throws JenkinsException {
        var job = jenkinsJobService.getJob(projectKey, planKey);
        if (job == null) {
            // Plan doesn't exist.
            return ContinuousIntegrationService.BuildStatus.INACTIVE;
        }

        if (job.inQueue()) {
            return ContinuousIntegrationService.BuildStatus.QUEUED;
        }

        try {
            URI uri = JenkinsEndpoints.LAST_BUILD.buildEndpoint(jenkinsServerUri, projectKey, planKey).build(true).toUri();
            var buildStatus = restTemplate.getForObject(uri, JenkinsBuildStatusDTO.class);
            return buildStatus != null && buildStatus.building ? ContinuousIntegrationService.BuildStatus.BUILDING : ContinuousIntegrationService.BuildStatus.INACTIVE;
        }
        catch (HttpClientErrorException e) {
            log.error("Error while trying to fetch build status from Jenkins for {}: {}", planKey, e.getMessage());
            return ContinuousIntegrationService.BuildStatus.INACTIVE;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record JenkinsBuildStatusDTO(boolean building) {
    }

    /**
     * Checks if a project folder exists.
     *
     * @param projectKey The name of the project folder.
     * @return True, only if the folder exists.
     */
    public boolean projectFolderExists(String projectKey) {
        try {
            var project = jenkinsJobService.getFolderConfig(projectKey);
            return project != null;
        }
        catch (IOException e) {
            return false;
        }
    }

    /**
     * Returns true if the build plan exists.
     *
     * @param projectKey  the project key
     * @param buildPlanId the build plan id
     * @return whether the plan exists
     */
    public boolean buildPlanExists(String projectKey, String buildPlanId) {
        try {
            var planExists = jenkinsJobService.getJob(projectKey, buildPlanId);
            return planExists != null;
        }
        catch (JenkinsException emAll) {
            return false;
        }
    }

    /**
     * Enables the build plan
     *
     * @param projectKey the project key of the plan
     * @param planKey    the plan key
     */
    public void enablePlan(String projectKey, String planKey) {
        try {
            URI uri = JenkinsEndpoints.ENABLE.buildEndpoint(jenkinsServerUri, projectKey, planKey).build(true).toUri();
            restTemplate.postForEntity(uri, new HttpEntity<>(null, new HttpHeaders()), Void.class);
        }
        catch (HttpClientErrorException e) {
            throw new JenkinsException("Unable to enable plan " + planKey + "; statusCode=" + e.getStatusCode() + "; body=" + e.getResponseBodyAsString());
        }
    }

    /**
     * Creates a custom Build Plan for a Programming Exercise using Aeolus. If the build plan could not be created, null is
     * returned. Example: "PROJECT-BUILDPLANKEY" is created -> "PROJECT-BUILDPLANKEY" is returned, same as the
     * default build plan creation.
     *
     * @param programmingExercise   the programming exercise for which to create the build plan
     * @param buildPlanId           the id of the build plan
     * @param repositoryUri         the url of the assignment repository
     * @param testRepositoryUri     the url of the test repository
     * @param solutionRepositoryUri the url of the solution repository
     * @return the key of the created build plan, or null if it could not be created
     */
    private String createCustomAeolusBuildPlanForExercise(ProgrammingExercise programmingExercise, String buildPlanId, VcsRepositoryUri repositoryUri,
            VcsRepositoryUri testRepositoryUri, VcsRepositoryUri solutionRepositoryUri) throws ContinuousIntegrationBuildPlanException {
        if (aeolusBuildPlanService.isEmpty() || programmingExercise.getBuildConfig().getBuildPlanConfiguration() == null) {
            return null;
        }
        try {
            ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
            Windfile windfile = buildConfig.getWindfile();
            Map<String, AeolusRepository> repositories = aeolusBuildPlanService.get().createRepositoryMapForWindfile(programmingExercise.getProgrammingLanguage(),
                    buildConfig.getBranch(), buildConfig.getCheckoutSolutionRepository(), repositoryUri, testRepositoryUri, solutionRepositoryUri, List.of());

            String resultHookUrl = artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH;
            var metadata = new WindfileMetadata(programmingExercise.getProjectName(), buildPlanId, "planDescription", null, vcsCredentials, null, resultHookUrl,
                    artemisAuthenticationTokenKey);
            windfile = new Windfile(windfile, metadata, repositories);
            String generatedKey = aeolusBuildPlanService.get().publishBuildPlan(windfile, AeolusTarget.JENKINS);

            if (generatedKey != null && generatedKey.contains("-")) {
                return buildPlanId;
            }
            else {
                throw new ContinuousIntegrationBuildPlanException("Could not create custom build plan for exercise " + programmingExercise.getTitle());
            }
        }
        catch (ContinuousIntegrationBuildPlanException | JsonProcessingException e) {
            log.error("Custom build plan creation for exercise {} with id {} failed -> use default build plan", programmingExercise.getTitle(), programmingExercise.getId(), e);
        }
        return null;
    }
}
