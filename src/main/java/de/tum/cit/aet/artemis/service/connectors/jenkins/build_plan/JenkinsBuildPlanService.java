package de.tum.cit.aet.artemis.service.connectors.jenkins.build_plan;

import static de.tum.cit.aet.artemis.config.Constants.NEW_RESULT_RESOURCE_API_PATH;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.transform.TransformerException;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.AeolusTarget;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.ProjectType;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.exception.ContinuousIntegrationBuildPlanException;
import de.tum.cit.aet.artemis.exception.JenkinsException;
import de.tum.cit.aet.artemis.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusBuildPlanService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusRepository;
import de.tum.cit.aet.artemis.service.connectors.aeolus.Windfile;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.service.connectors.ci.notification.dto.TestResultsDTO;
import de.tum.cit.aet.artemis.service.connectors.jenkins.JenkinsEndpoints;
import de.tum.cit.aet.artemis.service.connectors.jenkins.JenkinsInternalUrlService;
import de.tum.cit.aet.artemis.service.connectors.jenkins.JenkinsXmlConfigBuilder;
import de.tum.cit.aet.artemis.service.connectors.jenkins.JenkinsXmlFileUtils;
import de.tum.cit.aet.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.cit.aet.artemis.service.connectors.jenkins.jobs.JenkinsJobService;

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

    private final JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsJobPermissionsService jenkinsJobPermissionsService;

    private final JenkinsInternalUrlService jenkinsInternalUrlService;

    private final UserRepository userRepository;

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

    public JenkinsBuildPlanService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsServer jenkinsServer, JenkinsBuildPlanCreator jenkinsBuildPlanCreator,
            JenkinsJobService jenkinsJobService, JenkinsJobPermissionsService jenkinsJobPermissionsService, JenkinsInternalUrlService jenkinsInternalUrlService,
            UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository, JenkinsPipelineScriptCreator jenkinsPipelineScriptCreator,
            BuildPlanRepository buildPlanRepository, Optional<AeolusBuildPlanService> aeolusBuildPlanService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.restTemplate = restTemplate;
        this.jenkinsServer = jenkinsServer;
        this.jenkinsBuildPlanCreator = jenkinsBuildPlanCreator;
        this.jenkinsJobService = jenkinsJobService;
        this.userRepository = userRepository;
        this.jenkinsJobPermissionsService = jenkinsJobPermissionsService;
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
        final Document jobConfig = configBuilder.buildBasicConfig(programmingLanguage, Optional.ofNullable(exercise.getProjectType()), internalRepositoryUris, checkoutSolution,
                buildPlanUrl);

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

        givePlanPermissions(exercise, planKey);
        triggerBuild(jobFolder, job);
    }

    private JenkinsXmlConfigBuilder.InternalVcsRepositoryURLs getInternalRepositoryUris(final ProgrammingExercise exercise, final VcsRepositoryUri assignmentRepositoryUri) {
        final VcsRepositoryUri assignmentUrl = jenkinsInternalUrlService.toInternalVcsUrl(assignmentRepositoryUri);
        final VcsRepositoryUri testUrl = jenkinsInternalUrlService.toInternalVcsUrl(exercise.getRepositoryURL(RepositoryType.TESTS));
        final VcsRepositoryUri solutionUrl = jenkinsInternalUrlService.toInternalVcsUrl(exercise.getRepositoryURL(RepositoryType.SOLUTION));

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
            case JAVA, KOTLIN, PYTHON, C, HASKELL, SWIFT, EMPTY, RUST -> jenkinsBuildPlanCreator;
            case VHDL, ASSEMBLER, OCAML, JAVASCRIPT, C_SHARP, C_PLUS_PLUS, SQL, R, TYPESCRIPT, GO, MATLAB, BASH, RUBY, POWERSHELL, ADA, DART, PHP ->
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
        final Document jobConfig = jenkinsJobService.getJobConfigForJobInFolder(buildProjectKey, buildPlanKey);

        try {
            JenkinsBuildPlanUtils.replaceScriptParameters(jobConfig, existingRepoUri, repoUri);
        }
        catch (IllegalArgumentException e) {
            log.error("Pipeline Script not found", e);
        }

        postBuildPlanConfigChange(buildPlanKey, buildProjectKey, jobConfig);
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

    private void postBuildPlanConfigChange(String buildPlanKey, String buildProjectKey, Document jobConfig) {
        final var errorMessage = "Error trying to configure build plan in Jenkins " + buildPlanKey;
        try {
            URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(serverUrl.toString(), buildProjectKey, buildPlanKey).build(true).toUri();

            final var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);

            String jobXmlString = JenkinsXmlFileUtils.writeToString(jobConfig);
            final var entity = new HttpEntity<>(jobXmlString, headers);

            restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
        }
        catch (RestClientException | TransformerException e) {
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }

    /**
     * Returns the build plan key from the specified test results.
     *
     * @param testResultsDTO the test results from Jenkins
     * @return the build plan key
     */
    public String getBuildPlanKeyFromTestResults(TestResultsDTO testResultsDTO) throws JsonProcessingException {
        final var nameParams = testResultsDTO.getFullName().split(" ");
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
        final var jobXml = jenkinsJobService.getJobConfigForJobInFolder(sourceProjectKey, sourcePlanKey);

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
            jenkinsJobService.getJobInFolder(projectKey, planKey).build(useCrumb);
        }
        catch (JenkinsException | IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error triggering build: " + planKey, e);
        }
    }

    /**
     * Deletes the build plan
     *
     * @param projectKey the project key of the plan
     * @param planKey    the plan key
     */
    public void deleteBuildPlan(String projectKey, String planKey) {
        try {
            var folderJob = jenkinsJobService.getFolderJob(projectKey);
            if (folderJob != null) {
                jenkinsServer.deleteJob(folderJob, planKey, useCrumb);
            }
        }
        catch (HttpResponseException e) {
            // We don't throw an exception if the build doesn't exist in Jenkins (404 status)
            if (e.getStatusCode() != HttpStatus.SC_NOT_FOUND) {
                log.error(e.getMessage(), e);
                throw new JenkinsException("Error while trying to delete job in Jenkins: " + planKey, e);
            }
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to delete job in Jenkins: " + planKey, e);
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
        var job = jenkinsJobService.getJobInFolder(projectKey, planKey);
        if (job == null) {
            // Plan doesn't exist.
            return ContinuousIntegrationService.BuildStatus.INACTIVE;
        }

        if (job.isInQueue()) {
            return ContinuousIntegrationService.BuildStatus.QUEUED;
        }

        try {
            var uri = UriComponentsBuilder.fromHttpUrl(serverUrl.toString()).pathSegment("job", projectKey, "job", planKey, "lastBuild", "api", "json").build().toUri();
            var response = restTemplate.getForObject(uri, JsonNode.class);
            var isJobBuilding = response.get("building").asBoolean();
            return isJobBuilding ? ContinuousIntegrationService.BuildStatus.BUILDING : ContinuousIntegrationService.BuildStatus.INACTIVE;
        }
        catch (NullPointerException | HttpClientErrorException e) {
            log.error("Error while trying to fetch build status from Jenkins for {}: {}", planKey, e.getMessage());
            return ContinuousIntegrationService.BuildStatus.INACTIVE;
        }
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
            var planExists = jenkinsJobService.getJobInFolder(projectKey, buildPlanId);
            return planExists != null;
        }
        catch (JenkinsException emAll) {
            return false;
        }
    }

    /**
     * Assigns access permissions to instructors and TAs for the specified build plan.
     * This is done by getting all users that belong to the instructor and TA groups of
     * the exercises' course and adding permissions to the Jenkins job.
     *
     * @param programmingExercise the programming exercise
     * @param planName            the name of the build plan
     */
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        try {
            // Retrieve the TAs and instructors that will be given access to the plan of the programming exercise
            Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
            var teachingAssistants = userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getTeachingAssistantGroupName()).stream()
                    .map(User::getLogin).collect(Collectors.toSet());
            var editors = userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getEditorGroupName()).stream().map(User::getLogin)
                    .collect(Collectors.toSet());
            var instructors = userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getInstructorGroupName()).stream().map(User::getLogin)
                    .collect(Collectors.toSet());

            // The build plan of the exercise is inside the course folder
            var jobFolder = programmingExercise.getProjectKey();
            var jobName = jobFolder + "-" + planName;
            jenkinsJobPermissionsService.addInstructorAndEditorAndTAPermissionsToUsersForJob(teachingAssistants, editors, instructors, jobFolder, jobName);
        }
        catch (IOException e) {
            throw new JenkinsException("Cannot give assign permissions to plan " + planName, e);
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
            var uri = UriComponentsBuilder.fromHttpUrl(serverUrl.toString()).pathSegment("job", projectKey, "job", planKey, "enable").build(true).toUri();
            restTemplate.postForEntity(uri, null, String.class);
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
            windfile.setPreProcessingMetadata(buildPlanId, programmingExercise.getProjectName(), this.vcsCredentials, resultHookUrl, "planDescription", repositories,
                    this.artemisAuthenticationTokenKey);
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
