package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.exception.BambooException;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.apache.http.HttpException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.swift.bamboo.cli.BambooClient;
import org.swift.common.cli.Base;
import org.swift.common.cli.CliClient;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.tum.in.www1.artemis.config.Constants.ASSIGNMENT_REPO_NAME;
import static de.tum.in.www1.artemis.config.Constants.TEST_REPO_NAME;

@Service
@Profile("bamboo")
public class BambooService implements ContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(BambooService.class);

    @Value("${artemis.bamboo.url}")
    private URL BAMBOO_SERVER_URL;

    @Value("${artemis.bamboo.empty-commit-necessary}")
    private Boolean BAMBOO_EMPTY_COMMIT_WORKAROUND_NECESSARY;

    @Value("${artemis.bamboo.user}")
    private String BAMBOO_USER;

    @Value("${artemis.bamboo.password}")
    private String BAMBOO_PASSWORD;

    private final GitService gitService;
    private final ResultRepository resultRepository;
    private final FeedbackRepository feedbackRepository;
    private final ParticipationRepository participationRepository;
    private final ProgrammingSubmissionRepository programmingSubmissionRepository;
    private final Optional<VersionControlService> versionControlService;
    private final Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService;
    private final BambooBuildPlanService bambooBuildPlanService;
    private final RestTemplate restTemplate;

    public BambooService(GitService gitService, ResultRepository resultRepository, FeedbackRepository feedbackRepository, ParticipationRepository participationRepository,
                         ProgrammingSubmissionRepository programmingSubmissionRepository, Optional<VersionControlService> versionControlService,
                         Optional<ContinuousIntegrationUpdateService> continuousIntegrationUpdateService, BambooBuildPlanService bambooBuildPlanService, RestTemplate restTemplate) {
        this.gitService = gitService;
        this.resultRepository = resultRepository;
        this.feedbackRepository = feedbackRepository;
        this.participationRepository = participationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.versionControlService = versionControlService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
        this.bambooBuildPlanService = bambooBuildPlanService;
        this.restTemplate = restTemplate;
    }

    /**
     * Creates the base build plan for the given programming exercise in Bamboo.
     *
     * @param programmingExercise   a programming exercise with the required information to create the base build plan
     * @param planKey               the key of the plan
     * @param repositoryName        the slug of the assignment repository (used to separate between exercise and solution), i.e. the unique identifier
     * @param testRepositoryName    the slug of the test repository, i.e. the unique identifier
     */
    @Override
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, String repositoryName, String testRepositoryName) {
        bambooBuildPlanService.createBuildPlanForExercise(programmingExercise, planKey, repositoryName, testRepositoryName);
    }

    /**
     * Create a BambooClient for communication with the Bamboo server.
     *
     * @return BambooClient instance for the Bamboo server that is defined in the environment yml files.
     */
    private BambooClient getBambooClient() {
        //TODO: we might prevent console log message by passing a Settings object into Base
        final BambooClient bambooClient = new BambooClient(new Base());
        //setup the Bamboo Client to use the correct username and password

        String[] args = new String[]{
            "-s", BAMBOO_SERVER_URL.toString(),
            "--user", BAMBOO_USER,
            "--password", BAMBOO_PASSWORD,
        };

        bambooClient.doWork(args); //only invoke this to set server address, username and password so that the following action will work
        return bambooClient;
    }

    /**
     * Parse the project key from the repoUrl of the given repositoryUrl.
     *
     * @param repositoryUrl of the repo on the VCS server.
     * @return the project key that was parsed.
     */
    //TODO: this method has moved to BitbucketService, but missed the toUpperCase() there, so we reactivated it here
    private String getProjectKeyFromUrl(URL repositoryUrl) {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        return repositoryUrl.getFile().split("/")[2].toUpperCase();
    }

    /**
     * Configure the build plan with the given participation on the Bamboo.
     * For Bamboo to be set up correctly an empty commit needs to be made.
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy.
     */
    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        String buildPlanId = participation.getBuildPlanId();
        URL repositoryUrl = participation.getRepositoryUrlAsUrl();
        String planProject = getProjectKeyFromBuildPlanId(buildPlanId);
        String planKey = participation.getBuildPlanId();
        updatePlanRepository(
            planProject,
            planKey,
            ASSIGNMENT_REPO_NAME,
            getProjectKeyFromUrl(repositoryUrl),
            versionControlService.get().getRepositoryName(repositoryUrl)
        );
        enablePlan(planKey);
        // We need to trigger an initial update in order for Gitlab to work correctly
        continuousIntegrationUpdateService.get().triggerUpdate(buildPlanId, true);

        // Empty commit - Bamboo bug workaround

        if (BAMBOO_EMPTY_COMMIT_WORKAROUND_NECESSARY) {
            try {
                Repository repo = gitService.getOrCheckoutRepository(repositoryUrl, true);
                gitService.commitAndPush(repo, "Setup");

                if (exercise == null) {
                    log.warn("Cannot access exercise in 'configureBuildPlan' to determine if deleting the repo after cloning make sense. Will decide to delete the repo");
                    gitService.deleteLocalRepository(repo);
                } else {
                    //only delete the git repository, if the online editor is NOT allowed
                    //this saves some performance on the server, when the student opens the online editor, because the repo does not need to be cloned again
                    //Note: the null check is necessary, because otherwise we might get a null pointer exception
                    if (exercise.isAllowOnlineEditor() == null || exercise.isAllowOnlineEditor() == Boolean.FALSE) {
                        gitService.deleteLocalRepository(repo);
                    }
                }
            } catch (GitAPIException ex) {
                log.error("Git error while doing empty commit", ex);
            } catch (IOException ex) {
                log.error("IOError while doing empty commit", ex);
            } catch (InterruptedException ex) {
                log.error("InterruptedException while doing empty commit", ex);
            } catch (NullPointerException ex) {
                log.error("NullPointerException while doing empty commit", ex);
            }
        }
    }

    /**
     * Triggers a build for the build plan in the given participation.
     *
     * @param participation the participation with the id of the build plan that should be triggered.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws HttpException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(
                BAMBOO_SERVER_URL + "/rest/api/latest/queue/" + participation.getBuildPlanId(),
                HttpMethod.POST,
                entity,
                Map.class);
        } catch (RestClientException e) {
            log.error("HttpError while triggering build", e);
            throw new HttpException("Communication failed when trying to trigger the Bamboo build for participationId " + participation.getId() + " with the following error: " + e.getMessage());
        }
    }

    @Override
    public boolean isBuildPlanEnabled(final String planId) {
        final var headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        final var entity = new HttpEntity<>(null, headers);
        final var planInfo = restTemplate.exchange(BAMBOO_SERVER_URL + "/rest/api/latest/plan/" + planId, HttpMethod.GET, entity, Map.class, new HashMap<>()).getBody();
        return planInfo.containsKey("enabled") && ((boolean) planInfo.get("enabled"));
    }

    /**
     * Delete the build plan with given identifier from Bamboo.
     *
     * @param buildPlanId unique identifier for the build plan on Bamboo.
     */
    @Override
    public void deleteBuildPlan(String buildPlanId) {
        deletePlan(buildPlanId);
    }

    /**
     * Delete project with given identifier from CI system.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    @Override
    public void deleteProject(String projectKey) {
        try {
            log.info("Delete project " + projectKey);
            //TODO: use Bamboo REST API: DELETE "/rest/api/latest/project/{projectKey}"
            String message = getBambooClient().getProjectHelper().deleteProject(projectKey);
            log.info("Delete project was successful. " + message);
        } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        Map<String, Boolean> status = retrieveBuildStatus(participation.getBuildPlanId());
        if (status == null) {
            return BuildStatus.INACTIVE;
        }
        if (status.get("isActive") && !status.get("isBuilding")) {
            return BuildStatus.QUEUED;
        } else if (status.get("isActive") && status.get("isBuilding")) {
            return BuildStatus.BUILDING;
        } else {
            return BuildStatus.INACTIVE;
        }
    }

    /**
     * Fetch the latest build result from Bamboo and attach it to the given result.
     *
     * @param result the result for which to get details.
     * @return List of automatic feedback by the continuous integration server. contains the test methods and their results:
     */
    @Override
    public List<Feedback> getLatestBuildResultDetails(Result result) {
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) result.getParticipation();
        if (result.getParticipation() == null || programmingExerciseParticipation.getBuildPlanId() == null) {
            // most probably the build was cleaned and we do not have access to it any more.
            return null;
        }
        Map<String, Object> buildResultDetails = retrieveLatestBuildResult(programmingExerciseParticipation.getBuildPlanId());
        List<Feedback> feedbackItems = addFeedbackToResult(result, buildResultDetails);
        return feedbackItems;
    }

    /**
     * Get the build logs of the latest Bamboo build for the given build plan.
     *
     * @param buildPlanId to get the latest build logs.
     * @return list of build log entries.
     */
    @Override
    public List<BuildLogEntry> getLatestBuildLogs(String buildPlanId) {
        return retrieveLatestBuildLogs(buildPlanId);
    }

    /**
     * Get the public URL to the build plan. Used for the "Go to Build Plan" button, if this feature is enabled for the exercise.
     *
     * @param participation participation for which to get the build plan URL.
     * @return build plan url.
     */
    @Override
    public URL getBuildPlanWebUrl(ProgrammingExerciseParticipation participation) {
        try {
            return new URL(BAMBOO_SERVER_URL + "/browse/" + participation.getBuildPlanId().toUpperCase());
        } catch (MalformedURLException e) {
            log.error("Couldn't construct build plan web URL");
        }
        return BAMBOO_SERVER_URL;
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName) {
        final var cleanPlanName = getCleanPlanName(targetPlanName);
        final var targetPlanKey = targetProjectKey + "-" + cleanPlanName;
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        try {
            log.debug("Clone build plan " + sourcePlanKey + " to " + targetPlanKey);
            //TODO use REST API PUT "/rest/api/latest/clone/{projectKey}-{buildKey}"
            String message = getBambooClient().getPlanHelper().clonePlan(sourcePlanKey, targetPlanKey, cleanPlanName, "", targetProjectName, true);
            log.info("Clone build plan " + sourcePlanKey + " was successful." + message);
        } catch (CliClient.ClientException clientException) {
            if (clientException.getMessage().contains("already exists")) {
                log.info("Build Plan already exists. Going to recover build plan information...");
                return targetPlanKey;
            } else {
                log.error(clientException.getMessage(), clientException);
            }
        } catch (CliClient.RemoteRestException e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while cloning build plan", e);
        }

        return targetPlanKey;
    }

    @Override
    public String enablePlan(String planKey) throws BambooException {
        try {
            log.debug("Enable build plan " + planKey);
            //TODO use REST API PUT "/rest/api/latest/clone/{projectKey}-{buildKey}"
            String message = getBambooClient().getPlanHelper().enablePlan(planKey, true);
            log.info("Enable build plan " + planKey + " was successful. " + message);
            return message;
        } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            log.error(e.getMessage(), e);
            throw new BambooException("Something went wrong while enabling the build plan", e);
        }
    }

    @Override
    public String updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String repoProjectName, String repoName) throws BambooException {
        return continuousIntegrationUpdateService.get().updatePlanRepository(bambooProject, bambooPlan, bambooRepositoryName, repoProjectName, repoName);
    }

    /**
     * Deletes the given plan.
     *
     * @param planKey to identify the Bamboo plan to delete
     */
    private void deletePlan(String planKey) {
        try {
            log.info("Delete build plan " + planKey);
            //TODO use REST API DELETE "/rest/api/latest/clone/{projectKey}-{buildKey}"
            String message = getBambooClient().getPlanHelper().deletePlan(planKey);
            log.info("Delete build plan was successful. " + message);
        } catch (CliClient.ClientException | CliClient.RemoteRestException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Retrieves the latest build result for the given plan key and saves it as result.
     * It checks if the build result is the current one. If not, it waits for a configurable delay and then tries again.
     *
     * @param participation the ProgrammingExerciseParticipation that received a new build result
     * @return the created result.
     */
    @Override
    @Transactional
    @Deprecated
    public Result onBuildCompletedOld(ProgrammingExerciseParticipation participation) {
        log.debug("Retrieving build result...");
        Boolean isOldBuildResult = true;
        Map buildResults = new HashMap<>();
        try {
            buildResults = retrieveLatestBuildResult(participation.getBuildPlanId());
            isOldBuildResult = TimeUnit.SECONDS.toMillis(ZonedDateTime.now().toEpochSecond() - ((ZonedDateTime) buildResults.get("buildCompletedDate")).toEpochSecond()) > (60 * 1000);     // older than 60s
        } catch (Exception ex) {
            log.warn("Exception when retrieving a Bamboo build result for build plan " + participation.getBuildPlanId() + ": " + ex.getMessage());
        }

        if (isOldBuildResult) {
            log.warn("It seems we got an old build result from Bamboo for build plan " + participation.getBuildPlanId() + ". Waiting 1s to retrieve build result...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Sleep error", e);
            }
            log.debug("Retrieving build result (second try)...");
            buildResults = retrieveLatestBuildResult(participation.getBuildPlanId());
        }

        if (buildResults != null && buildResults.containsKey("buildReason")) {
            String buildReason = (String) buildResults.get("buildReason");
            if (buildReason.contains("First build for this plan")) {
                //Filter the first build plan that was automatically executed when the build plan was created
                return null;
            }
        }

        Result result = new Result();
        result.setRatedIfNotExceeded(participation.getProgrammingExercise().getDueDate(), ZonedDateTime.now());
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful((boolean) buildResults.get("successful"));
        result.setResultString((String) buildResults.get("buildTestSummary"));
        result.setCompletionDate((ZonedDateTime) buildResults.get("buildCompletedDate"));
        result.setScore(calculateScoreForResult(result));
        result.setBuildArtifact(buildResults.containsKey("artifact"));
        result.setParticipation((Participation) participation);

        addFeedbackToResult(result, buildResults);
        // save result, otherwise the next database access programmingSubmissionRepository.findByCommitHash will throw an exception
        resultRepository.save(result);

        if (buildResults.containsKey("vcsRevisionKey") || buildResults.containsKey("changesetId")) {
            //we prefer 'changesetId', because it should be correct for multiple commits leading to a build or when test cases have changed.
            // In case it does not exist (e.g. due to a manual build), we fall back to vcsRevisionKey which should be correct in most cases
            String commitHash = (String) buildResults.get("changesetId");
            if (commitHash == null || "".equals(commitHash)) {
                commitHash = (String) buildResults.get("vcsRevisionKey");
            }

            //Due to test case changes there might be multiple submissions with the same commit hash, but with different participations, therefore we have to take the participation_id into account
            ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHash(participation.getId(), commitHash);
            if (programmingSubmission == null) { // no matching programming submission
                log.warn("Could not find ProgrammingSubmission for Commit-Hash {} (Participation {}, Build-Plan {}). Will create it subsequently...", commitHash, participation.getId(), participation.getBuildPlanId());
                // this might be a wrong build (what could be the reason), or this might be due to test changes
                // what happens if only the test has changes? should we then create a new submission?
                programmingSubmission = new ProgrammingSubmission();
                programmingSubmission.setParticipation((Participation) participation);
                programmingSubmission.setSubmitted(true);
                programmingSubmission.setType(SubmissionType.OTHER);
                programmingSubmission.setCommitHash(commitHash);
                programmingSubmission.setSubmissionDate(result.getCompletionDate());
            } else {
                log.info("Found corresponding submission to build result with Commit-Hash {}", commitHash);
            }
            // connect submission and result
            result.setSubmission(programmingSubmission);
            programmingSubmission.setResult(result);
            programmingSubmissionRepository.save(programmingSubmission); // result gets saved later, no need to save it now

        } else { // No commit hash in build result
            log.warn("Could not find Commit-Hash (Participation {}, Build-Plan {})", participation.getId(), participation.getBuildPlanId());
        }

        resultRepository.save(result);
        return result;
    }

    /**
     * Extract the plan key from the Bamboo requestBody.
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key or null if it can't be found.
     * @throws BambooException is thrown on casting errors.
     */
    @Override
    @SuppressWarnings("unchecked")
    public String getPlanKey(Object requestBody) throws BambooException {
        try {
            Map<String, Object> requestBodyMap = (Map<String, Object>) requestBody;
            Map<String, Object> planMap = (Map<String, Object>) requestBodyMap.get("plan");
            return (String) planMap.get("key");
        } catch (Exception e) {
            // TODO: Not sure when this is triggered, the method would return null if the planMap does not have a 'key'.
            log.error("Error when getting plan key");
            throw new BitbucketException("Could not get plan key", e);
        }
    }

    /**
     * React to a new build result from Bamboo, create the result and feedbacks and link the result to the submission and participation.
     *
     * @param participation The participation for which the build finished.
     * @param requestBody   The request Body received from the CI-Server.
     * @return the created result.
     * @throws Exception when the request body cannot be parsed, this method throws an exception
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Result onBuildCompletedNew(ProgrammingExerciseParticipation participation, Object requestBody) throws Exception {
        log.debug("Retrieving build result (NEW) ...");
        try {
            Map<String, Object> requestBodyMap = (Map<String, Object>) requestBody;
            Map<String, Object> buildMap = (Map<String, Object>) requestBodyMap.get("build");

            // Filter the first build plan that was automatically executed when the build plan was created.
            if (isFirstBuildForThisPlan(buildMap)) return null;

            List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findByParticipationIdAndResultIsNullOrderBySubmissionDateDesc(participation.getId());
            Optional<ProgrammingSubmission> latestMatchingPendingSubmission = submissions.stream().filter(submission -> {
                String matchingCommitHashInBuildMap = getCommitHash(buildMap, submission.getType());
                return matchingCommitHashInBuildMap.equals(submission.getCommitHash());
            }).findFirst();

            Result result = createResultFromBuildResult(buildMap, participation);
            // Save result, otherwise the next database access programmingSubmissionRepository.findByCommitHash will throw an exception
            resultRepository.save(result);

            ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
            ProgrammingSubmission programmingSubmission;
            if (latestMatchingPendingSubmission.isPresent()) {
                programmingSubmission = latestMatchingPendingSubmission.get();
            } else {
                // There can be two reasons for the case that there is no programmingSubmission:
                // 1) Manual build triggered from Bamboo.
                // 2) An unknown error that caused the programming submission not to be created when the code commits have been pushed
                // we can still get the commit has from the payload of the Bamboo REST Call and "reverse engineer" the programming submission object to be consistent
                String commitHash = getCommitHash(buildMap, SubmissionType.MANUAL);
                log.warn("Could not find ProgrammingSubmission for Commit-Hash {} (Participation {}, Build-Plan {}). Will create it subsequently...", commitHash, participation.getId(), participation.getBuildPlanId());
                programmingSubmission = new ProgrammingSubmission();
                programmingSubmission.setParticipation((Participation) participation);
                programmingSubmission.setSubmitted(true);
                programmingSubmission.setType(SubmissionType.OTHER);
                programmingSubmission.setCommitHash(commitHash);
                // In this case we don't know the submission time, so we use the result completion time as a fallback.
                programmingSubmission.setSubmissionDate(result.getCompletionDate());
                // Save to avoid TransientPropertyValueException.
                programmingSubmissionRepository.save(programmingSubmission);
            }
            programmingSubmission.setResult(result);
            result.setSubmission(programmingSubmission);
            result.setRatedIfNotExceeded(programmingExercise.getDueDate(), programmingSubmission);
            return resultRepository.save(result);
        } catch (Exception e) {
            log.error("Error when getting build result: " + e.getMessage());
            throw new BambooException("Could not get build result", e);
        }
    }

    /**
     * Check if the build result received is the initial build of the plan.
     *
     * @param buildMap data provided by build.
     * @return true if build is the first build.
     */
    private boolean isFirstBuildForThisPlan(Map<String, Object> buildMap) {
        String buildReason = (String) buildMap.get("reason");
        return buildReason != null && buildReason.contains("First build for this plan");
    }

    /**
     * Generate an Artemis result object from the CI build result. Will use the test case feedback as result feedback.
     *
     * @param buildMap data provided by build.
     * @param participation to attach result to.
     * @return the created result (is not persisted in this method, only constructed!)
     */
    private Result createResultFromBuildResult(Map<String, Object> buildMap, ProgrammingExerciseParticipation participation) {
        Result result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful((Boolean) buildMap.get("successful"));

        Map<String, Object> testSummary = (Map<String, Object>) buildMap.get("testSummary");
        result.setResultString((String) testSummary.get("description"));

        result.setCompletionDate(ZonedDateTime.parse((String) buildMap.get("buildCompletedDate")));
        result.setScore(calculateScoreForResult(result));
        result.setBuildArtifact((Boolean) buildMap.get("artifact"));
        result.setParticipation((Participation) participation);

        return addFeedbackToResultNew(result, (List<Object>) buildMap.get("jobs"));
    }

    /**
     * Get the commit hash from the build map, the commit hash will be different for submission types or null.
     *
     * @param buildMap data provided by build.
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise null.
     */
    private String getCommitHash(Map<String, Object> buildMap, SubmissionType submissionType) {
        List<Object> vcsList = (List<Object>) buildMap.get("vcs");

        String commitHash = null;
        for (Object changeSet : vcsList) {
            Map<String, Object> changeSetMap = (Map<String, Object>) changeSet;
            String repositoryName = (String) changeSetMap.get("repositoryName");
            if ((submissionType.equals(SubmissionType.MANUAL) || submissionType.equals(SubmissionType.INSTRUCTOR)) && repositoryName.equalsIgnoreCase(ASSIGNMENT_REPO_NAME)) {
                // We are only interested in the last commit hash of the assignment repo, not the test repo
                commitHash = (String) changeSetMap.get("id");
            } else if (submissionType.equals(SubmissionType.TEST) && repositoryName.equalsIgnoreCase(TEST_REPO_NAME)) {
                commitHash = (String) changeSetMap.get("id");
            }
        }
        return commitHash;
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result to which to add the feedback.
     * @param buildResultDetails returned build result details from the rest API of bamboo
     * @return a list of feedbacks itemsstored in a result
     */
    public List<Feedback> addFeedbackToResult(Result result, Map<String, Object> buildResultDetails) {
        if (buildResultDetails == null) {
            return null;
        }

        try {
            List<Map<String, Object>> details = (List<Map<String, Object>>) buildResultDetails.get("details");
            final ProgrammingLanguage programmingLanguage = ((ProgrammingExercise) result.getParticipation().getExercise()).getProgrammingLanguage();
            if (!details.isEmpty()) {
                result.setHasFeedback(true);
            }
            //breaking down the Bamboo API answer to get all the relevant details
            for (Map<String, Object> detail : details) {
                String className = (String) detail.get("className");
                String methodName = (String) detail.get("methodName");

                Map<String, Object> errorsMap = (Map<String, Object>) detail.get("errors");
                List<Map<String, Object>> errors = (List<Map<String, Object>>) errorsMap.get("error");

                final String errorMessageString = errors.stream()
                    .map(error -> (String) error.get("message"))
                    .map(errorString -> processResultErrorMessage(programmingLanguage, errorString))
                    .reduce("", String::concat);

                createAutomaticFeedback(result, methodName, false, errorMessageString);
            }
        } catch (Exception failedToParse) {
            log.error("Parsing from bamboo to feedback failed" + failedToParse);
        }

        return result.getFeedbacks();
    }

    /**
     * Filters and processes a feedback error message, thereby removing any unwanted strings depending on
     * the programming language, or just reformatting it to only show the most important details.
     *
     * @param programmingLanguage The programming language for which the feedback was generated
     * @param message The raw error message in the feedback
     * @return A filtered and better formatted error message
     */
    private String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final String message) {
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            // Splitting string at the first linebreak to only get the first line of the Exception
            return message.split("\\n", 2)[0]
                //junit 4
                .replace("java.lang.AssertionError: ", "")
                //junit 5
                .replace("org.opentest4j.AssertionFailedError: ", "");
        }

        return message;
    }

    /**
     * Create an automatic feedback object from the given parameter.
     * @param result to which the feedback belongs.
     * @param methodName test case method name.
     * @param positive if the test case was successful.
     * @param errorMessageString if there was an error what the error is.
     */
    private void createAutomaticFeedback(Result result, String methodName, boolean positive, String errorMessageString) {
        Feedback feedback = new Feedback();
        feedback.setText(methodName);
        feedback.setDetailText(errorMessageString);
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setPositive(positive);
        feedback = feedbackRepository.save(feedback);
        result.addFeedback(feedback);
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result the result for which the feedback should be added
     * @param jobs   the jobs list of the requestBody
     * @return a list of feedbacks itemsstored in a result
     */
    @SuppressWarnings("unchecked")
    private Result addFeedbackToResultNew(Result result, List<Object> jobs) {
        if (jobs == null) {
            return null;
        }

        try {
            List<Map<String, Object>> castedJobs = (List<Map<String, Object>>) (Object) jobs;
            final ProgrammingLanguage programmingLanguage = ((ProgrammingExercise) result.getParticipation().getExercise())
                .getProgrammingLanguage();

            for (Map<String, Object> job : castedJobs) {

                // 1) add feedback for failed test cases
                List<Map<String, Object>> failedTests = (List<Map<String, Object>>) job.get("failedTests");
                for (Map<String, Object> failedTest : failedTests) {
                    String methodName = (String) failedTest.get("name"); // in the attribute "methodName", bamboo seems to apply some unwanted logic

                    List<String> errors = (List<String>) failedTest.get("errors");
                    final String errorMessageString = errors.stream()
                        .map(errorString -> processResultErrorMessage(programmingLanguage, errorString))
                        .reduce("", String::concat);

                    log.debug("errorMSGString is {}", errorMessageString);

                    createAutomaticFeedback(result, methodName, false, errorMessageString);
                }

                // 2) add feedback for passed test cases
                List<Map<String, Object>> succuessfulTests = (List<Map<String, Object>>) job.get("successfulTests");
                for (Map<String, Object> succuessfulTest : succuessfulTests) {
                    String methodName = (String) succuessfulTest.get("name"); // in the attribute "methodName", bamboo seems to apply some unwanted logic

                    createAutomaticFeedback(result, methodName, true, null);
                }

                if (!failedTests.isEmpty()) {
                    result.setHasFeedback(true);
                }
            }

        } catch (Exception e) {
            log.error("Could not get feedback from jobs " + e);
        }

        return result;
    }

    /**
     * Calculates the score for a result. Therefore is uses the number of successful tests in the latest build.
     *
     * @param result to calculate score for.
     * @return the score calculated.
     */
    private Long calculateScoreForResult(Result result) {

        if (result.isSuccessful()) {
            return (long) 100;
        }

        if (result.getResultString() != null && !result.getResultString().isEmpty()) {

            Pattern pattern = Pattern.compile("^([0-9]+) of ([0-9]+) failed");
            Matcher matcher = pattern.matcher(result.getResultString());

            if (matcher.find()) {
                float failedTests = Float.parseFloat(matcher.group(1));
                float totalTests = Float.parseFloat(matcher.group(2));
                float score = (totalTests - failedTests) / totalTests;
                return (long) (score * 100);
            }
        }
        return (long) 0;
    }

    /**
     * Performs a request to the Bamboo REST API to retrive the latest result for the given plan.
     *
     * @param planKey the key of the plan for which to retrieve the latest result
     * @return a map containing the following data:
     * - successful:            if the build was successful
     * - buildTestSummary:      a string generated by Bamboo summarizing the build result
     * - buildCompletedDate:    the completion date of the build
     */
    private Map<String, Object> retrieveLatestBuildResult(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER_URL + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json?expand=testResults.failedTests.testResult.errors,artifacts,changes",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving latest build results from Bamboo for planKey " + planKey + ":" + e.getMessage());
        }
        if (response != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("successful", response.getBody().get("buildState").equals("Successful"));
            result.put("buildTestSummary", response.getBody().get("buildTestSummary"));
            if (response.getBody().containsKey("buildReason")) {
                result.put("buildReason", response.getBody().get("buildReason"));
            }
            String dateString = (String) response.getBody().get("buildCompletedDate");
            ZonedDateTime buildCompletedDate = ZonedDateTime.parse(dateString);
            result.put("buildCompletedDate", buildCompletedDate);

            //search for test case results
            List resultDetails = (List) ((Map) ((Map) response.getBody().get("testResults")).get("failedTests")).get("testResult");
            //might be empty
            result.put("details", resultDetails);

            //search for version control information
            if (response.getBody().containsKey("vcsRevisionKey")) {
                result.put("vcsRevisionKey", response.getBody().get("vcsRevisionKey"));
            }
            if (response.getBody().containsKey("changes")) {
                Map<String, Object> changesEntry = (Map<String, Object>) response.getBody().get("changes");
                int size = (int) changesEntry.get("size");
                if (changesEntry.containsKey("change")) {
                    List<Object> changesList = (List<Object>) changesEntry.get("change");
                    if (changesList.size() > 0) {
                        //Take the latest change, i.e. size - 1
                        Map<String, Object> change = (Map<String, Object>) changesList.get(size - 1);
                        if (change.containsKey("changesetId")) {
                            result.put("changesetId", change.get("changesetId"));
                        }
                    }
                }
            }

            //search for artifacts: take the first one that is not a build log
            if (response.getBody().containsKey("artifacts")) {
                Map<String, Object> artifactsEntity = (Map<String, Object>) response.getBody().get("artifacts");
                if ((int) artifactsEntity.get("size") > 0 && artifactsEntity.containsKey("artifact")) {
                    List<Map<String, Object>> artifacts = (List<Map<String, Object>>) artifactsEntity.get("artifact");
                    for (Map<String, Object> artifact : artifacts) {
                        if (((String) artifact.get("name")).equalsIgnoreCase("Build log")) {
                            continue;
                        } else {
                            String link = (String) ((Map<String, Object>) artifact.get("link")).get("href");
                            result.put("artifact", link);
                        }
                    }
                }
            }

            return result;
        }
        return null;
    }

    /**
     * Performs a request to the Bamboo REST API to retrieve the build log of the latest build.
     *
     * @param planKey to identify the build logs with.
     * @return the list of retrieved build logs.
     */
    //TODO: save this on the Artemis server, e.g. in the result class so that Artemis does not need to retrieve it every time
    public List<BuildLogEntry> retrieveLatestBuildLogs(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER_URL + "/rest/api/latest/result/" + planKey.toUpperCase() + "-JOB1/latest.json?expand=logEntries&max-results=250",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build result logs from Bamboo: " + e.getMessage());
        }

        List logs = new ArrayList<BuildLogEntry>();

        if (response != null) {
            for (Map<String, Object> logEntry : (List<Map>) ((Map) response.getBody().get("logEntries")).get("logEntry")) {
                String logString = (String) logEntry.get("log");
                boolean compilationErrorFound = true;

                if (logString.contains("COMPILATION ERROR")) {
                    compilationErrorFound = true;
                }

                if (compilationErrorFound && logString.contains("BUILD FAILURE")) {
                    // hide duplicated information that is displayed in the section COMPILATION ERROR and in the section BUILD FAILURE and stop here
                    break;
                }

                //filter unnecessary logs
                if ((logString.startsWith("[INFO]") && !logString.contains("error")) ||
                    logString.startsWith("[WARNING]") ||
                    logString.startsWith("[ERROR] [Help 1]") ||
                    logString.startsWith("[ERROR] For more information about the errors and possible solutions") ||
                    logString.startsWith("[ERROR] Re-run Maven using") ||
                    logString.startsWith("[ERROR] To see the full stack trace of the errors") ||
                    logString.startsWith("[ERROR] -> [Help 1]")
                ) {
                    continue;
                }

                //Replace some unnecessary information and hide complex details to make it easier to read the important information
                logString = logString.replaceAll("/opt/bamboo-agent-home/xml-data/build-dir/", "");

                Instant instant = Instant.ofEpochMilli((long) logEntry.get("date"));
                ZonedDateTime logDate = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
                BuildLogEntry log = new BuildLogEntry(logDate, logString);
                logs.add(log);
            }
        }
        return logs;
    }

    /**
     * Gets the latest available artifact for the given participation.
     *
     * @param participation to use its buildPlanId to find the artifact.
     * @return the html representation of the artifact page.
     */
    public ResponseEntity retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO: It would be better to directly pass the buildPlanId.
        String planKey = participation.getBuildPlanId();
        Map<String, Object> latestResult = retrieveLatestBuildResult(planKey);
        // If the build has an artifact, the response contains an artifact key.
        // It seems this key is only available if the "Share" checkbox in Bamboo was used.
        if (latestResult.containsKey("artifact")) {
            // The URL points to the directory. Bamboo returns an "Index of" page.
            // Recursively walk through the responses until we get the actual artifact.
            return retrieveArtifactPage((String) latestResult.get("artifact"));
        } else {
            throw new BambooException("No build artifact available for this plan");
        }
    }

    /**
     * Queries Bamboo to find out if the project already exists.
     *
     * @param projectKey to check if a project with this unique key already exists.
     * @param projectName to check if a project with the same name already exists.
     * @return true if the project is already existing on Bamboo.
     */
    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER_URL + "/rest/api/latest/project/" + projectKey,
                HttpMethod.GET,
                entity,
                Map.class);
            log.warn("Bamboo project " + projectKey + " already exists");
            return "The project " + projectKey + " already exists in the CI Server. Please choose a different short name!";
        } catch (HttpClientErrorException e) {
            log.debug("Bamboo project " + projectKey + " does not exit");
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                //only if this is the case, we additionally check that the project name is unique
                response = restTemplate.exchange(
                    BAMBOO_SERVER_URL + "/rest/api/latest/search/projects?searchTerm=" + projectName,
                    HttpMethod.GET,
                    entity,
                    Map.class);
                if ((Integer) response.getBody().get("size") != 0) {
                    List<Object> ciProjects = (List<Object>) response.getBody().get("searchResults");
                    for (Object ciProject : ciProjects) {
                        String ciProjectName = (String) ((Map) ((Map) ciProject).get("searchEntity")).get("projectName");
                        if (ciProjectName.equalsIgnoreCase(projectName)) {
                            log.warn("Bamboo project with name" + projectName + " already exists");
                            return "The project " + projectName + " already exists in the CI Server. Please choose a different title!";
                        }
                    }
                }
                return null;
            }
        }
        return "The project already exists in the CI Server. Please choose a different title and short name!";
    }

    /**
     * Gets the content from a Bamboo artifact link
     * Follows links on HTML directory pages, if necessary
     *
     * @param url of the artifact page.
     * @return the build artifact as html.
     */
    private ResponseEntity retrieveArtifactPage(String url) throws BambooException {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> response;

        try {
            response = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
        } catch (Exception e) {
            log.error("HttpError while retrieving build artifact", e);
            throw new BambooException("HttpError while retrieving build artifact");
        }

        //Note: Content-Type might contain additional elements such as the UTF-8 encoding, therefore we now use contains instead of equals
        if (response.getHeaders().containsKey("Content-Type") && response.getHeaders().get("Content-Type").get(0).contains("text/html")) {
            // This is an "Index of" HTML page.
            String html = new String(response.getBody(), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile("href=\"(.*?)\"", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                url = matcher.group(1);
                // Recursively walk through the responses until we get the actual artifact.
                return retrieveArtifactPage(BAMBOO_SERVER_URL + url);
            } else {
                throw new BambooException("No artifact link found on artifact page");
            }
        } else {
            // Actual artifact file
            return response;
        }
    }

    /**
     * Retrieves the current build status of the given plan.
     *
     * @param planKey the key of the plan for which to retrieve the status
     * @return a map containing the following data:
     * - isActive: true if the plan is queued or building
     * - isBuilding: true if the plan is building
     */
    public Map<String, Boolean> retrieveBuildStatus(String planKey) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER_URL + "/rest/api/latest/plan/" + planKey.toUpperCase() + ".json",
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            log.error("Bamboo HttpError '" + e.getMessage() + "' while retrieving build status for plan " + planKey, e);
        }
        if (response != null) {
            Map<String, Boolean> result = new HashMap<>();
            boolean isActive = (boolean) response.getBody().get("isActive");
            boolean isBuilding = (boolean) response.getBody().get("isBuilding");
            result.put("isActive", isActive);
            result.put("isBuilding", isBuilding);
            return result;
        }
        return null;
    }

    /**
     * Check if the given build plan is valid and accessible on Bamboo.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if the build plan id is valid.
     */
    @Override
    public Boolean buildPlanIdIsValid(String buildPlanId) {
        HttpHeaders headers = HeaderUtil.createAuthorization(BAMBOO_USER, BAMBOO_PASSWORD);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = null;
        try {
            response = restTemplate.exchange(
                BAMBOO_SERVER_URL + "/rest/api/latest/plan/" + buildPlanId.toUpperCase(),
                HttpMethod.GET,
                entity,
                Map.class);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    private String getProjectKeyFromBuildPlanId(String buildPlanId) {
        return buildPlanId.split("-")[0];
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
