package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.offbytwo.jenkins.JenkinsServer;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.BuildLogStatisticsEntryService;
import de.tum.in.www1.artemis.service.connectors.AbstractContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestCaseDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

@Profile("jenkins")
@Service
public class JenkinsService extends AbstractContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    private final JenkinsServer jenkinsServer;

    private final JenkinsJobService jenkinsJobService;

    private final JenkinsInternalUrlService jenkinsInternalUrlService;

    private final TestwiseCoverageService testwiseCoverageService;

    public JenkinsService(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsServer jenkinsServer, ProgrammingSubmissionRepository programmingSubmissionRepository,
            FeedbackRepository feedbackRepository, @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate, BuildLogEntryService buildLogService,
            BuildLogStatisticsEntryService buildLogStatisticsEntryService, JenkinsBuildPlanService jenkinsBuildPlanService, JenkinsJobService jenkinsJobService,
            JenkinsInternalUrlService jenkinsInternalUrlService, TestwiseCoverageService testwiseCoverageService) {
        super(programmingSubmissionRepository, feedbackRepository, buildLogService, buildLogStatisticsEntryService, restTemplate, shortTimeoutRestTemplate);
        this.jenkinsServer = jenkinsServer;
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
        this.jenkinsJobService = jenkinsJobService;
        this.jenkinsInternalUrlService = jenkinsInternalUrlService;
        this.testwiseCoverageService = testwiseCoverageService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, planKey, repositoryURL);
    }

    @Override
    public void recreateBuildPlansForExercise(ProgrammingExercise exercise) {
        final String projectKey = exercise.getProjectKey();

        if (!jenkinsBuildPlanService.projectFolderExists(projectKey)) {
            createProjectForExercise(exercise);
        }

        deleteBuildPlan(projectKey, exercise.getTemplateBuildPlanId());
        deleteBuildPlan(projectKey, exercise.getSolutionBuildPlanId());

        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.TEMPLATE.getName(), exercise.getRepositoryURL(RepositoryType.TEMPLATE));
        jenkinsBuildPlanService.createBuildPlanForExercise(exercise, BuildPlanType.SOLUTION.getName(), exercise.getRepositoryURL(RepositoryType.SOLUTION));
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for Jenkins
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        return jenkinsBuildPlanService.copyBuildPlan(sourceProjectKey, sourcePlanName, targetProjectKey, targetPlanName);
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        jenkinsBuildPlanService.givePlanPermissions(programmingExercise, planName);
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch) {
        jenkinsBuildPlanService.configureBuildPlanForParticipation(participation);
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            String newDefaultBranch, Optional<List<String>> optionalTriggeredByRepositories) {
        jenkinsBuildPlanService.updateBuildPlanRepositories(buildProjectKey, buildPlanKey, ciRepoName, newRepoUrl, existingRepoUrl);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        jenkinsBuildPlanService.triggerBuild(projectKey, planKey);
    }

    @Override
    public void deleteProject(String projectKey) {
        jenkinsJobService.deleteJob(projectKey);
    }

    @Override
    public void deleteBuildPlan(String projectKey, String planKey) {
        jenkinsBuildPlanService.deleteBuildPlan(projectKey, planKey);
    }

    @Override
    public String getPlanKey(Object requestBody) throws JenkinsException {
        try {
            TestResultsDTO dto = TestResultsDTO.convert(requestBody);
            return jenkinsBuildPlanService.getBuildPlanKeyFromTestResults(dto);
        }
        catch (JsonProcessingException jsonProcessingException) {
            throw new JenkinsException("Something went wrong trying to parse the requestBody while getting the PlanKey from Jenkins!");
        }
    }

    @Override
    public AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody) {
        return TestResultsDTO.convert(requestBody);
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        var urlString = serverUrl + "/project/" + projectKey + "/" + buildPlanId;
        return Optional.of(jenkinsInternalUrlService.toInternalCiUrl(urlString));
    }

    @Override
    public void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries) {
        ZonedDateTime jobStarted = null;
        ZonedDateTime agentSetupCompleted = null;
        ZonedDateTime testsStarted = null;
        ZonedDateTime testsFinished = null;
        ZonedDateTime scaStarted = null;
        ZonedDateTime scaFinished = null;
        ZonedDateTime jobFinished = null;
        Long dependenciesDownloadedCount = null;

        if (programmingLanguage == ProgrammingLanguage.JAVA && (projectType == ProjectType.MAVEN_MAVEN || projectType == ProjectType.PLAIN_MAVEN)) {
            jobStarted = getTimestampForLogEntry(buildLogEntries, ""); // First entry
            agentSetupCompleted = getTimestampForLogEntry(buildLogEntries, "docker exec");
            testsStarted = getTimestampForLogEntry(buildLogEntries, "Scanning for projects...");
            testsFinished = getTimestampForLogEntry(buildLogEntries, "Total time:");
            scaStarted = getTimestampForLogEntry(buildLogEntries, "Scanning for projects...");
            scaFinished = getTimestampForLogEntry(buildLogEntries, "Total time:");
            jobFinished = buildLogEntries.get(buildLogEntries.size() - 1).getTime(); // Last entry
            dependenciesDownloadedCount = countMatchingLogs(buildLogEntries, "Downloaded from");
        }
        if (programmingLanguage == ProgrammingLanguage.JAVA && (projectType == ProjectType.GRADLE_GRADLE || projectType == ProjectType.PLAIN_GRADLE)) {
            jobStarted = getTimestampForLogEntry(buildLogEntries, ""); // First entry
            // agentSetupCompleted is not supported
            testsStarted = getTimestampForLogEntry(buildLogEntries, "Starting a Gradle Daemon");
            testsFinished = getTimestampForLogEntry(buildLogEntries, b -> b.getLog().contains("BUILD SUCCESSFUL in") || b.getLog().contains("BUILD FAILED in"));
            scaStarted = getTimestampForLogEntry(buildLogEntries, "Task :checkstyleMain");
            scaFinished = getTimestampForLogEntry(buildLogEntries, b -> b.getLog().contains("BUILD SUCCESSFUL in") || b.getLog().contains("BUILD FAILED in"), 1);
            jobFinished = buildLogEntries.get(buildLogEntries.size() - 1).getTime(); // Last entry
            // dependenciesDownloadedCount is not supported
        }

        var agentSetupDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(jobStarted, agentSetupCompleted);
        var testDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(testsStarted, testsFinished);
        var scaDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(scaStarted, scaFinished);
        var totalJobDuration = new BuildLogStatisticsEntry.BuildJobPartDuration(jobStarted, jobFinished);

        buildLogStatisticsEntryService.saveBuildLogStatisticsEntry(programmingSubmission, agentSetupDuration, testDuration, scaDuration, totalJobDuration,
                dependenciesDownloadedCount);
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        if (participation.getBuildPlanId() == null) {
            // The build plan does not exist, the build status cannot be retrieved
            return null;
        }

        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        return jenkinsBuildPlanService.getBuildStatusOfPlan(projectKey, planKey);
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        return jenkinsBuildPlanService.buildPlanExists(projectKey, buildPlanId);
    }

    @Override
    protected void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult) {
        final var testResults = ((TestResultsDTO) buildResult);
        final var jobs = testResults.getResults();
        final var programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();
        final var programmingLanguage = programmingExercise.getProgrammingLanguage();
        final var projectType = programmingExercise.getProjectType();

        // Extract test case feedback
        for (final var job : jobs) {
            for (final var testCase : job.getTestCases()) {
                var feedbackMessages = extractMessageFromTestCase(testCase).map(List::of).orElse(List.of());
                var feedback = feedbackRepository.createFeedbackFromTestCase(testCase.getName(), feedbackMessages, testCase.isSuccessful(), programmingLanguage, projectType);
                result.addFeedback(feedback);
            }

            int passedTestCasesAmount = (int) job.getTestCases().stream().filter(TestCaseDTO::isSuccessful).count();
            result.setTestCaseCount(result.getTestCaseCount() + job.getTests());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + passedTestCasesAmount);
        }

        // Extract static code analysis feedback if option was enabled
        final var staticCodeAnalysisReports = testResults.getStaticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            var scaFeedbackList = feedbackRepository.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            result.addFeedbacks(scaFeedbackList);
            result.setCodeIssueCount(scaFeedbackList.size());
        }

        final var testwiseCoverageReport = testResults.getTestwiseCoverageReport();
        if (Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled()) && testwiseCoverageReport != null && !testwiseCoverageReport.isEmpty()) {
            // since the test cases are not saved to the database yet, the test case is null for the entries
            var coverageFileReportsWithoutTestsByTestCaseName = testwiseCoverageService.createTestwiseCoverageFileReportsWithoutTestsByTestCaseName(testwiseCoverageReport);
            result.setCoverageFileReportsByTestCaseName(coverageFileReportsWithoutTestsByTestCaseName);
        }

        // Relevant feedback is negative, or positive with a message
        result.setHasFeedback(result.getFeedbacks().stream().anyMatch(feedback -> !feedback.isPositive() || feedback.getDetailText() != null));
    }

    /**
     * Extracts the most helpful message from the given test case.
     * @param testCase the test case information as received from Jenkins.
     * @return the most helpful message that can be added to an automatic {@link Feedback}.
     */
    private Optional<String> extractMessageFromTestCase(final TestCaseDTO testCase) {
        var hasErrors = !CollectionUtils.isEmpty(testCase.getErrors());
        var hasFailures = !CollectionUtils.isEmpty(testCase.getFailures());
        var hasSuccessInfos = !CollectionUtils.isEmpty(testCase.getSuccessInfos());
        boolean successful = testCase.isSuccessful();

        if (successful && hasSuccessInfos && testCase.getSuccessInfos().get(0).getMostInformativeMessage() != null) {
            return Optional.of(testCase.getSuccessInfos().get(0).getMostInformativeMessage());
        }
        else if (hasErrors && testCase.getErrors().get(0).getMostInformativeMessage() != null) {
            return Optional.of(testCase.getErrors().get(0).getMostInformativeMessage());
        }
        else if (hasFailures && testCase.getFailures().get(0).getMostInformativeMessage() != null) {
            return Optional.of(testCase.getFailures().get(0).getMostInformativeMessage());
        }
        else if (hasErrors && testCase.getErrors().get(0).getType() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", testCase.getErrors().get(0).getType()));
        }
        else if (hasFailures && testCase.getFailures().get(0).getType() != null) {
            return Optional.of(String.format("Unsuccessful due to an error of type: %s", testCase.getFailures().get(0).getType()));
        }
        else if (!successful) {
            // this is an edge case which typically does not happen
            return Optional.of("Unsuccessful due to an unknown error. Please contact your instructor!");
        }
        else {
            // successful and no message available => do not generate one
            return Optional.empty();
        }
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) programmingSubmission.getParticipation();
        String projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
        String buildPlanId = programmingExerciseParticipation.getBuildPlanId();
        ProgrammingLanguage programmingLanguage = programmingExerciseParticipation.getProgrammingExercise().getProgrammingLanguage();

        try {
            final var build = jenkinsJobService.getJobInFolder(projectKey, buildPlanId).getLastBuild();
            List<BuildLogEntry> buildLogEntries;

            // Attempt to parse pipeline logs
            final String pipelineLogs = build.details().getConsoleOutputText();
            if (pipelineLogs != null && pipelineLogs.contains("[Pipeline] Start of Pipeline")) {
                buildLogEntries = JenkinsBuildLogParseUtils.parseBuildLogsFromJenkinsLogs(List.of(pipelineLogs.split("\n")));
            }
            else {
                // Fallback to legacy logs
                final var logHtml = Jsoup.parse(build.details().getConsoleOutputHtml()).body();
                buildLogEntries = JenkinsBuildLogParseUtils.parseLogsLegacy(logHtml);
            }

            // Filter and save build logs
            buildLogEntries = filterUnnecessaryLogs(buildLogEntries, programmingLanguage);
            buildLogEntries = buildLogService.saveBuildLogs(buildLogEntries, programmingSubmission);
            programmingSubmission.setBuildLogEntries(buildLogEntries);
            programmingSubmissionRepository.save(programmingSubmission);
            return buildLogEntries;
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    /**
     * Removes the build logs that are not relevant to the student.
     *
     * @param buildLogEntries unfiltered build logs
     * @param programmingLanguage the programming language of the build
     * @return filtered build logs
     */
    private List<BuildLogEntry> filterUnnecessaryLogs(List<BuildLogEntry> buildLogEntries, ProgrammingLanguage programmingLanguage) {
        var filteredBuildLogs = TestResultsDTO.filterBuildLogs(buildLogEntries);
        // Filter out the remainder of unnecessary logs
        return buildLogService.removeUnnecessaryLogsForProgrammingLanguage(filteredBuildLogs, programmingLanguage);
    }

    @Override
    public ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO, not necessary for the core functionality
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        try {
            final var job = jenkinsServer.getJob(projectKey);
            if (job == null) {
                // means the project does not exist
                return null;
            }
            else if (job.getUrl() == null || job.getUrl().isEmpty()) {
                return null;
            }
            else {
                return "The project " + projectKey + " already exists in the CI Server. Please choose a different short name!";
            }
        }
        catch (Exception emAll) {
            log.warn(emAll.getMessage());
            // in case of an error message, we assume the project exist (like in Bamboo service)
            return "The project already exists on the Continuous Integration Server. Please choose a different title and short name!";
        }
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {
        jenkinsBuildPlanService.enablePlan(projectKey, planKey);
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        // Not needed since Jenkins doesn't support project permissions
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        // Not needed since Jenkins doesn't support project permissions
    }

    @Override
    public ConnectorHealth health() {
        try {
            // Note: we simply check if the login page is reachable
            shortTimeoutRestTemplate.getForObject(serverUrl + "/login", String.class);
            return new ConnectorHealth(true, Map.of("url", serverUrl));
        }
        catch (Exception emAll) {
            var health = new ConnectorHealth(false, Map.of("url", serverUrl));
            health.setException(new JenkinsException("Jenkins Server is down!"));
            return health;
        }
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException {
        try {
            jenkinsServer.createFolder(programmingExercise.getProjectKey(), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error creating folder for exercise " + programmingExercise, e);
        }
    }
}
