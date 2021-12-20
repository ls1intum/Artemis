package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringEscapeUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.*;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.*;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

@Service
@Profile("bamboo")
public class BambooService extends AbstractContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(BambooService.class);

    @Value("${artemis.continuous-integration.empty-commit-necessary}")
    private Boolean isEmptyCommitNecessary;

    private final GitService gitService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService;

    private final BambooBuildPlanService bambooBuildPlanService;

    private final ObjectMapper mapper;

    private final UrlService urlService;

    private final ExerciseDateService exerciseDateService;

    public BambooService(GitService gitService, ProgrammingSubmissionRepository programmingSubmissionRepository, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, BambooBuildPlanService bambooBuildPlanService, FeedbackRepository feedbackRepository,
            @Qualifier("bambooRestTemplate") RestTemplate restTemplate, @Qualifier("shortTimeoutBambooRestTemplate") RestTemplate shortTimeoutRestTemplate, ObjectMapper mapper,
            UrlService urlService, BuildLogEntryService buildLogService, ExerciseDateService exerciseDateService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, restTemplate, shortTimeoutRestTemplate);
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.bambooBuildPlanService = bambooBuildPlanService;
        this.mapper = mapper;
        this.urlService = urlService;
        this.exerciseDateService = exerciseDateService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUrl sourceCodeRepositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        var additionalRepositories = programmingExercise.getAuxiliaryRepositoriesForBuildPlan().stream()
                .map(repo -> new AuxiliaryRepository.AuxRepoNameWithSlug(repo.getName(), urlService.getRepositorySlugFromRepositoryUrl(repo.getVcsRepositoryUrl())))
                .collect(Collectors.toList());
        bambooBuildPlanService.createBuildPlanForExercise(programmingExercise, planKey, urlService.getRepositorySlugFromRepositoryUrl(sourceCodeRepositoryURL),
                urlService.getRepositorySlugFromRepositoryUrl(testRepositoryURL), urlService.getRepositorySlugFromRepositoryUrl(solutionRepositoryURL), additionalRepositories);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        deleteBuildPlan(exercise.getProjectKey(), exercise.getTemplateBuildPlanId());
        deleteBuildPlan(exercise.getProjectKey(), exercise.getSolutionBuildPlanId());
        createBuildPlanForExercise(exercise, BuildPlanType.TEMPLATE.getName(), exercise.getVcsTemplateRepositoryUrl(), exercise.getVcsTestRepositoryUrl(),
                exercise.getVcsSolutionRepositoryUrl());
        createBuildPlanForExercise(exercise, BuildPlanType.SOLUTION.getName(), exercise.getVcsSolutionRepositoryUrl(), exercise.getVcsTestRepositoryUrl(),
                exercise.getVcsSolutionRepositoryUrl());
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
        final var buildPlanId = participation.getBuildPlanId();
        final var repositoryUrl = participation.getVcsRepositoryUrl();
        final var projectKey = getProjectKeyFromBuildPlanId(buildPlanId);
        final var planKey = participation.getBuildPlanId();
        final var repoProjectName = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl);
        updatePlanRepository(projectKey, planKey, ASSIGNMENT_REPO_NAME, repoProjectName, participation.getRepositoryUrl(), null /* not needed */, Optional.empty());
        enablePlan(projectKey, planKey);
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Empty commit - Bamboo bug workaround

        if (isEmptyCommitNecessary) {
            try {
                ProgrammingExercise exercise = participation.getProgrammingExercise();
                var repositoryUrl = participation.getVcsRepositoryUrl();
                Repository repo = gitService.getOrCheckoutRepository(repositoryUrl, true);
                // we set user to null to make sure the Artemis user is used to create the setup commit, this is important to filter this commit later in
                // notifyPush in ProgrammingSubmissionService
                gitService.commitAndPush(repo, SETUP_COMMIT_MESSAGE, null);

                if (exercise == null) {
                    log.warn("Cannot access exercise in 'configureBuildPlan' to determine if deleting the repo after cloning make sense. Will decide to delete the repo");
                    gitService.deleteLocalRepository(repo);
                }
                else {
                    // only delete the git repository, if the online editor is NOT allowed
                    // this saves some performance on the server, when the student opens the online editor, because the repo does not need to be cloned again
                    // Note: the null check is necessary, because otherwise we might get a null pointer exception
                    if (exercise.isAllowOnlineEditor() == null || Boolean.FALSE.equals(exercise.isAllowOnlineEditor())) {
                        gitService.deleteLocalRepository(repo);
                    }
                }
            }
            catch (GitAPIException | IOException | InterruptedException | NullPointerException ex) {
                log.error("Exception while doing empty commit", ex);
            }
        }
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
        // Do nothing since Bamboo automatically creates projects
    }

    /**
     * Triggers a build for the build plan in the given participation.
     *
     * @param participation the participation with the id of the build plan that should be triggered.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws BambooException {
        var buildPlan = participation.getBuildPlanId();
        try {
            restTemplate.exchange(serverUrl + "/rest/api/latest/queue/" + buildPlan, HttpMethod.POST, null, Void.class);
        }
        catch (RestClientException e) {
            log.error("HttpError while triggering build plan {} with error: {}", buildPlan, e.getMessage());
            throw new BambooException("Communication failed when trying to trigger the Bamboo build plan " + buildPlan + " with the error: " + e.getMessage());
        }
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {

        var buildPlan = getBuildPlan(buildPlanId, false, false);
        if (buildPlan == null) {
            log.error("Cannot delete {}, because it does not exist!", buildPlanId);
            return;
        }

        // NOTE: we cannot use official the REST API, e.g. restTemplate.delete(serverUrl + "/rest/api/latest/plan/" + buildPlanId) here,
        // because then the build plan is not deleted directly and subsequent calls to create build plans with the same id might fail

        executeDelete("selectedBuilds", buildPlanId);
        log.info("Delete bamboo build plan {} was successful.", buildPlanId);
    }

    /**
     * NOTE: the REST call in this method fails silently with a 404 in case all build plans have already been deleted before
     *
     * @param projectKey the project which build plans should be retrieved
     * @return a list of build plans
     */
    private List<BambooBuildPlanDTO> getBuildPlans(String projectKey) {

        String requestUrl = serverUrl + "/rest/api/latest/project/" + projectKey;
        // we use 5000 just in case of exercises with really really many students ;-)
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("expand", "plans").queryParam("max-results", 5000);
            var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, BambooProjectDTO.class);

            if (response.getBody() != null && response.getBody().getPlans() != null) {
                return response.getBody().getPlans().getPlan();
            }
        }
        catch (HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                // return an empty list silently (without log), because this is the typical case when deleting projects
                return List.of();
            }
            log.warn(ex.getMessage());
        }
        catch (Exception ex) {
            log.warn(ex.getMessage());
        }
        return List.of();
    }

    /**
     * Delete project with given identifier from CI system.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    @Override
    public void deleteProject(String projectKey) {
        log.info("Try to delete bamboo project {}", projectKey);

        // TODO: check if the project actually exists, if not, we can immediately return

        // NOTE: we cannot use official the REST API, e.g. restTemplate.delete(serverUrl + "/rest/api/latest/project/" + projectKey) here,
        // because then the build plans are not deleted directly and subsequent calls to create build plans with the same id might fail

        // in normal cases this list should be empty, because all build plans have been deleted before
        final var buildPlans = getBuildPlans(projectKey);
        for (var buildPlan : buildPlans) {
            try {
                deleteBuildPlan(projectKey, buildPlan.getKey());
            }
            catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }

        executeDelete("selectedProjects", projectKey);
        log.info("Delete bamboo project {} was successful.", projectKey);
    }

    private void executeDelete(String elementKey, String elementValue) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(elementKey, elementValue);
        parameters.add("confirm", "true");
        parameters.add("bamboo.successReturnMode", "json");

        String requestUrl = serverUrl + "/admin/deleteBuilds.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        // TODO: in order to do error handling, we have to read the return value of this REST call
        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, String.class);
    }

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        if (participation.getBuildPlanId() == null) {
            log.warn("Cannot get the build status, because the build plan for the participation {} was cleaned up already!", participation);
            // The build plan does not exist, the build status cannot be retrieved
            return null;
        }
        final var buildPlan = getBuildPlan(participation.getBuildPlanId(), false, true);

        if (buildPlan == null) {
            return BuildStatus.INACTIVE;
        }
        if (buildPlan.getIsActive() && !buildPlan.getIsBuilding()) {
            return BuildStatus.QUEUED;
        }
        else if (buildPlan.getIsActive() && buildPlan.getIsBuilding()) {
            return BuildStatus.BUILDING;
        }
        else {
            return BuildStatus.INACTIVE;
        }
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        // Return the logs from Bamboo (and filter them now)
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) programmingSubmission.getParticipation();
        ProgrammingLanguage programmingLanguage = programmingExerciseParticipation.getProgrammingExercise().getProgrammingLanguage();

        var buildLogEntries = removeUnnecessaryLogsForProgrammingLanguage(retrieveLatestBuildLogsFromBamboo(programmingExerciseParticipation.getBuildPlanId()),
                programmingLanguage);
        var savedBuildLogs = buildLogService.saveBuildLogs(buildLogEntries, programmingSubmission);

        // Set the received logs in order to avoid duplicate entries (this removes existing logs) & save them into the database
        programmingSubmission.setBuildLogEntries(savedBuildLogs);
        programmingSubmissionRepository.save(programmingSubmission);

        return buildLogEntries;
    }

    /**
     * get the build plan for the given planKey
     *
     * @param planKey the unique Bamboo build plan identifier
     * @param expand  whether the expanded version of the build plan is needed
     * @return the build plan
     */
    private BambooBuildPlanDTO getBuildPlan(String planKey, boolean expand, boolean logNotFound) {
        if (planKey == null) {
            return null;
        }
        try {
            String requestUrl = serverUrl + "/rest/api/latest/plan/" + planKey;
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl);
            if (expand) {
                builder.queryParam("expand", "");
            }
            return restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, BambooBuildPlanDTO.class).getBody();
        }
        catch (HttpClientErrorException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                // in certain cases, not found is the desired behavior
                if (logNotFound) {
                    log.error("The build plan {} could not be found", planKey);
                }
                return null;
            }
            log.info(ex.getMessage());
            return null;
        }
        catch (Exception ex) {
            log.info(ex.getMessage());
            return null;
        }
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        final var cleanPlanName = getCleanPlanName(targetPlanName);
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        final var targetPlanKey = targetProjectKey + "-" + cleanPlanName;
        try {
            // execute get Plan so that Bamboo refreshes its internal list whether the build plan already exists. If this is the case, we could then also exit early
            var targetBuildPlan = getBuildPlan(targetPlanKey, false, false);
            if (targetBuildPlan != null) {
                log.info("Build Plan {} already exists. Going to recover build plan information...", targetPlanKey);
                return targetPlanKey;
            }
            log.info("Try to clone build plan {} to {}", sourcePlanKey, targetPlanKey);
            cloneBuildPlan(sourceProjectKey, sourcePlanName, targetProjectKey, cleanPlanName, targetProjectExists);
            log.info("Clone build plan {} to {} was successful", sourcePlanKey, targetPlanKey);
        }
        catch (RestClientException clientException) {
            if (clientException.getMessage() != null && clientException.getMessage().contains("already exists")) {
                // NOTE: this case cannot happen any more, because we get the build plan above. It might still be reported by Bamboo, then we still throw an exception,
                // because the build plan cannot exist (this might be a caching issue shortly after the participation / build plan was deleted).
                // It is important that we do not allow this here, because otherwise the subsequent actions won't succeed and the user might be in a wrong state that cannot be
                // solved any more
                log.warn("Edge case: Bamboo reports that the build Plan {} already exists. However the build plan was not found. The user should try again in a few minutes",
                        targetPlanKey);
            }
            throw new BambooException("Something went wrong while cloning build plan " + sourcePlanKey + " to " + targetPlanKey + ":" + clientException.getMessage(),
                    clientException);
        }
        catch (Exception serverException) {
            throw new BambooException("Something went wrong while cloning build plan: " + serverException.getMessage(), serverException);
        }

        return targetPlanKey;
    }

    private void cloneBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetPlanName, boolean targetProjectExists) {
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();

        if (targetProjectExists) {
            parameters.add("existingProjectKey", targetProjectKey);
        }
        else {
            parameters.add("existingProjectKey", "newProject");
            parameters.add("projectKey", targetProjectKey);
            parameters.add("projectName", targetProjectKey);
        }

        parameters.add("planKeyToClone", sourcePlanKey);
        parameters.add("chainName", targetPlanName);
        parameters.add("chainKey", targetPlanName);
        parameters.add("chainDescription", "Build plan for exercise " + sourceProjectKey);
        parameters.add("clonePlan", "true");
        parameters.add("tmp.createAsEnabled", "false");
        parameters.add("chainEnabled", "false");
        parameters.add("save", "Create");
        parameters.add("bamboo.successReturnMode", "json");

        String requestUrl = serverUrl + "/build/admin/create/performClonePlan.action";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParams(parameters);
        // TODO: in order to do error handling, we have to read the return value of this REST call
        var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.POST, null, String.class);
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        bambooBuildPlanService.setBuildPlanPermissionsForExercise(programmingExercise, planName);
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        // Bamboo always gives read rights
        final var permissionData = List.of(permissionToBambooPermission(CIPermission.READ));
        final var entity = new HttpEntity<>(permissionData, null);
        final var roles = List.of("ANONYMOUS", "LOGGED_IN");

        roles.forEach(role -> {
            final var url = serverUrl + "/rest/api/latest/permissions/project/" + projectKey + "/roles/" + role;
            final var response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            if (response.getStatusCode() != HttpStatus.NO_CONTENT && response.getStatusCode() != HttpStatus.NOT_MODIFIED) {
                throw new BambooException("Unable to remove default project permissions from exercise " + projectKey + "\n" + response.getBody());
            }
        });
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groupNames, List<CIPermission> permissions) {
        final var permissionData = permissions.stream().map(this::permissionToBambooPermission).collect(Collectors.toList());
        final var entity = new HttpEntity<>(permissionData, null);

        groupNames.forEach(group -> {
            final var url = serverUrl + "/rest/api/latest/permissions/project/" + projectKey + "/groups/" + group;
            final var response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            if (response.getStatusCode() != HttpStatus.NO_CONTENT && response.getStatusCode() != HttpStatus.NOT_MODIFIED) {
                final var errorMessage = "Unable to give permissions to project " + projectKey + "; error body: " + response.getBody() + "; headers: " + response.getHeaders()
                        + "; status code: " + response.getStatusCode();
                log.error(errorMessage);
                throw new BambooException(errorMessage);
            }
        });
    }

    private String permissionToBambooPermission(CIPermission permission) {
        return switch (permission) {
            case EDIT -> "WRITE";
            case CREATE -> "CREATE";
            case READ -> "READ";
            case ADMIN -> "ADMINISTRATION";
        };
    }

    @Override
    public void enablePlan(String projectKey, String planKey) throws BambooException {
        try {
            log.debug("Enable build plan {}", planKey);
            restTemplate.postForObject(serverUrl + "/rest/api/latest/plan/" + planKey + "/enable", null, Void.class);
            log.info("Enable build plan {} was successful.", planKey);
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while enabling the build plan", e);
        }
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            Optional<List<String>> optionalTriggeredByRepositories) throws BambooException {
        try {
            final var vcsRepoName = versionControlService.get().getRepositoryName(new VcsRepositoryUrl(newRepoUrl));
            String defaultBranchName = versionControlService.get().getDefaultBranchOfRepository(new VcsRepositoryUrl(newRepoUrl));
            continuousIntegrationUpdateService.get().updatePlanRepository(buildProjectKey, buildPlanKey, ciRepoName, repoProjectKey, vcsRepoName, defaultBranchName,
                    optionalTriggeredByRepositories);
        }
        catch (MalformedURLException e) {
            throw new BambooException(e.getMessage(), e);
        }
    }

    /**
     * Extract the plan key from the Bamboo requestBody.
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key or null if it can't be found.
     * @throws BambooException is thrown on casting errors.
     */
    @Override
    public String getPlanKey(Object requestBody) throws BambooException {
        try {
            final var buildResult = mapper.convertValue(requestBody, BambooBuildResultNotificationDTO.class);
            return buildResult.getPlan().getKey();
        }
        catch (Exception e) {
            // TODO: Not sure when this is triggered, the method would return null if the planMap does not have a 'key'.
            log.error("Error when getting plan key");
            throw new BambooException("Could not get plan key", e);
        }
    }

    /**
     * React to a new build result from Bamboo, create the result and feedbacks and link the result to the submission and participation.
     *
     * @param participation The participation for which the build finished.
     * @param requestBody   The result notification received from the CI-Server.
     * @return the created result.
     */
    @Override
    public Result onBuildCompleted(ProgrammingExerciseParticipation participation, Object requestBody) {
        log.debug("Retrieving build result (NEW) ...");
        try {
            final var buildResult = mapper.convertValue(requestBody, BambooBuildResultNotificationDTO.class);
            // Filter the first build plan in case it was automatically executed when the build plan was created.
            if (isFirstBuildForThisPlan(buildResult)) {
                return null;
            }

            var newResult = createResultFromBuildResult(buildResult, participation);

            // Fetch submission or create a fallback
            var latestSubmission = super.getSubmissionForBuildResult(participation.getId(), buildResult)
                    .orElseGet(() -> createAndSaveFallbackSubmission(participation, buildResult));
            latestSubmission.setBuildFailed(newResult.getResultString().equals("No tests found"));

            // Add artifacts to submission
            final var hasArtifact = buildResult.getBuild().isArtifact();
            latestSubmission.setBuildArtifact(hasArtifact);

            var programmingLanguage = participation.getProgrammingExercise().getProgrammingLanguage();
            var buildLogs = extractAndFilterBuildLogs(buildResult, programmingLanguage);
            var savedBuildLogs = buildLogService.saveBuildLogs(buildLogs, latestSubmission);

            // Set the received logs in order to avoid duplicate entries (this removes existing logs)
            latestSubmission.setBuildLogEntries(savedBuildLogs);

            // Note: we only set one side of the relationship because we don't know yet whether the result will actually be saved
            newResult.setSubmission(latestSubmission);

            newResult.setRatedIfNotExceeded(exerciseDateService.getDueDate(participation).orElse(null), latestSubmission);
            return newResult;
        }
        catch (Exception e) {
            log.error("Error when creating build result from Bamboo notification", e);
            throw new BambooException("Could not create build result from Bamboo notification", e);
        }
    }

    private List<BuildLogEntry> extractAndFilterBuildLogs(BambooBuildResultNotificationDTO buildResult, ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();

        // Store logs into database. Append logs of multiple jobs.
        for (var job : buildResult.getBuild().getJobs()) {
            for (var bambooLog : job.getLogs()) {
                // We have to unescape the HTML as otherwise symbols like '<' are not displayed correctly
                buildLogEntries.add(new BuildLogEntry(bambooLog.getDate(), StringEscapeUtils.unescapeHtml(bambooLog.getLog())));
            }
        }

        if (buildLogEntries.isEmpty()) {
            return buildLogEntries;
        }

        // Filter unwanted logs
        return removeUnnecessaryLogsForProgrammingLanguage(buildLogEntries, programmingLanguage);
    }

    @Override
    public ConnectorHealth health() {
        ConnectorHealth health;
        try {
            final var status = shortTimeoutRestTemplate.exchange(serverUrl + "/rest/api/latest/server", HttpMethod.GET, null, JsonNode.class);
            health = status.getBody().get("state").asText().equals("RUNNING") ? new ConnectorHealth(true) : new ConnectorHealth(false);
        }
        catch (Exception emAll) {
            health = new ConnectorHealth(emAll);
        }

        health.setAdditionalInfo(Map.of("url", serverUrl));
        return health;
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        // No webhooks needed between Bamboo and Bitbucket, so we return an empty Optional
        // See https://confluence.atlassian.com/bamboo/integrating-bamboo-with-bitbucket-server-779302772.html
        return Optional.empty();
    }

    /**
     * Check if the build result received is the initial build of the plan.
     * Note: this is an edge case and means it was not created with a commit+push by the user
     *
     * @param buildResult Build result data provided by build notification.
     * @return true if build is the first build.
     */
    private boolean isFirstBuildForThisPlan(BambooBuildResultNotificationDTO buildResult) {
        final var reason = buildResult.getBuild().getReason();
        return reason != null && reason.contains("First build for this plan");
    }

    @Override
    protected void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult) {
        final var jobs = ((BambooBuildResultNotificationDTO) buildResult).getBuild().getJobs();
        if (jobs == null) {
            return;
        }

        final ProgrammingExercise programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();
        final ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();

        for (final var job : jobs) {
            // 1) add feedback for failed test cases
            for (final var failedTest : job.getFailedTests()) {
                result.addFeedback(feedbackRepository.createFeedbackFromTestCase(failedTest.getName(), failedTest.getErrors(), false, programmingLanguage));
            }

            // 2) add feedback for passed test cases
            for (final var successfulTest : job.getSuccessfulTests()) {
                result.addFeedback(feedbackRepository.createFeedbackFromTestCase(successfulTest.getName(), successfulTest.getErrors(), true, programmingLanguage));
            }

            // 3) process static code analysis feedback
            if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
                var reports = job.getStaticCodeAnalysisReports();
                if (reports != null) {
                    var feedbackList = feedbackRepository.createFeedbackFromStaticCodeAnalysisReports(reports);
                    result.addFeedbacks(feedbackList);
                }
            }

            // Relevant feedback is negative
            result.setHasFeedback(result.getFeedbacks().stream().anyMatch(fb -> !fb.isPositive()));
        }
    }

    /**
     * Performs a request to the Bamboo REST API to retrieve the latest result for the given plan.
     *
     * @param planKey the key of the plan for which to retrieve the latest result
     * @return a map containing the following data:
     * - successful:            if the build was successful
     * - buildTestSummary:      a string generated by Bamboo summarizing the build result
     * - buildCompletedDate:    the completion date of the build
     */
    @Nullable
    public QueriedBambooBuildResultDTO queryLatestBuildResultFromBambooServer(String planKey) {
        ResponseEntity<QueriedBambooBuildResultDTO> response = null;
        try {
            response = restTemplate.exchange(
                    serverUrl + "/rest/api/latest/result/" + planKey.toUpperCase()
                            + "-JOB1/latest.json?expand=testResults.failedTests.testResult.errors,artifacts,changes,vcsRevisions",
                    HttpMethod.GET, null, QueriedBambooBuildResultDTO.class);
        }
        catch (Exception e) {
            log.warn("HttpError while retrieving latest build results from Bamboo for planKey {}: {}", planKey, e.getMessage());
        }
        if (response != null) {
            final var buildResult = response.getBody();

            // Filter out build log and static code analysis artifacts
            if (buildResult != null && buildResult.getArtifacts() != null) {
                List<String> artifactLabelFilter = StaticCodeAnalysisTool.getAllArtifactLabels();
                artifactLabelFilter.add("Build log");
                buildResult.getArtifacts().setArtifacts(
                        buildResult.getArtifacts().getArtifacts().stream().filter(artifact -> !artifactLabelFilter.contains(artifact.getName())).collect(Collectors.toList()));
            }
            return buildResult;
        }
        return null;
    }

    /**
     * Load the build log from the database.
     * Performs a request to the Bamboo REST API to retrieve the build log of the latest build, if the log is not available in the database.
     *
     * @param planKey to identify the build logs with.
     * @return the list of retrieved build logs.
     */
    private List<BuildLogEntry> retrieveLatestBuildLogsFromBamboo(String planKey) {
        var logs = new ArrayList<BuildLogEntry>();
        try {
            String requestUrl = serverUrl + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json";
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestUrl).queryParam("expand", "logEntries").queryParam("max-results", "2000");
            var response = restTemplate.exchange(builder.build().toUri(), HttpMethod.GET, null, BambooBuildResultDTO.class);

            if (response.getBody() != null && response.getBody().getLogEntries() != null) {

                for (var logEntry : response.getBody().getLogEntries().getLogEntry()) {
                    String logString = logEntry.getUnstyledLog();
                    // The log is provided in two attributes: with unescaped characters in unstyledLog and with escaped characters in log
                    // We want to have unescaped characters but fail back to the escaped characters in case no unescaped characters are present
                    if (logString == null) {
                        logString = logEntry.getLog();
                    }

                    BuildLogEntry log = new BuildLogEntry(logEntry.getDate(), logString);
                    logs.add(log);
                }
            }
        }
        catch (Exception e) {
            log.error("HttpError while retrieving build result logs from Bamboo", e);
        }
        return logs;
    }

    /**
     * Gets the latest available artifact for the given participation.
     *
     * @param participation to use its buildPlanId to find the artifact.
     * @return the html representation of the artifact page.
     */
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        String planKey = participation.getBuildPlanId();
        final var latestResult = queryLatestBuildResultFromBambooServer(planKey);
        // If the build has an artifact, the response contains an artifact key.
        // It seems this key is only available if the "Share" checkbox in Bamboo was used.
        if (latestResult != null && latestResult.getArtifacts() != null && !latestResult.getArtifacts().getArtifacts().isEmpty()) {
            // The URL points to the directory. Bamboo returns an "Index of" page.
            // Recursively walk through the responses until we get the actual artifact.
            return retrieveArtifactPage(latestResult.getArtifacts().getArtifacts().get(0).getLink().getLinkToArtifact().toString());
        }
        else {
            throw new BambooException("No build artifact available for this plan");
        }
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        try {
            restTemplate.exchange(serverUrl + "/rest/api/latest/project/" + projectKey, HttpMethod.GET, null, Void.class);
            log.warn("Bamboo project {} already exists", projectKey);
            return "The project " + projectKey + " already exists in the CI Server. Please choose a different short name!";
        }
        catch (HttpClientErrorException e) {
            log.debug("Bamboo project {} does not exit", projectKey);
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // only if this is the case, we additionally check that the project name is unique
                final var response = restTemplate.exchange(serverUrl + "/rest/api/latest/search/projects?searchTerm=" + projectName, HttpMethod.GET, null,
                        BambooProjectsSearchDTO.class);
                if (response.getBody() != null && response.getBody().getSize() > 0) {
                    final var exists = response.getBody().getSearchResults().stream().map(BambooProjectsSearchDTO.SearchResultDTO::getSearchEntity)
                            .anyMatch(project -> project.getProjectName().equalsIgnoreCase(projectName));
                    if (exists) {
                        log.warn("Bamboo project with name {} already exists", projectName);
                        return "The project " + projectName + " already exists in the CI Server. Please choose a different title!";
                    }
                }

                return null;
            }
        }
        return "The project already exists on the Continuous Integration Server. Please choose a different title and short name!";
    }

    /**
     * Gets the content from a Bamboo artifact link
     * Follows links on HTML directory pages, if necessary
     *
     * @param url of the artifact page.
     * @return the build artifact as html.
     */
    private ResponseEntity<byte[]> retrieveArtifactPage(String url) throws BambooException {
        ResponseEntity<byte[]> response;

        try {
            response = restTemplate.exchange(url, HttpMethod.GET, null, byte[].class);
        }
        catch (Exception e) {
            log.error("HttpError while retrieving build artifact", e);
            throw new BambooException("HttpError while retrieving build artifact");
        }

        // Note: Content-Type might contain additional elements such as the UTF-8 encoding, therefore we now use contains instead of equals
        if (response.getHeaders().containsKey("Content-Type") && response.getHeaders().get("Content-Type").get(0).contains("text/html")) {
            // This is an "Index of" HTML page.
            String html = new String(response.getBody(), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile("href=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                url = matcher.group(1);
                // Recursively walk through the responses until we get the actual artifact.
                return retrieveArtifactPage(serverUrl + url);
            }
            else {
                throw new BambooException("No artifact link found on artifact page");
            }
        }
        else {
            // Actual artifact file
            return response;
        }
    }

    /**
     * Check if the given build plan is valid and accessible on Bamboo.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if the build plan exists.
     */
    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return getBuildPlan(buildPlanId.toUpperCase(), false, false) != null;
    }

    private String getProjectKeyFromBuildPlanId(String buildPlanId) {
        return buildPlanId.split("-")[0];
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
