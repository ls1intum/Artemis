package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.FeedbackService;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.CommitDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.util.UrlUtils;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Service
public class JenkinsService implements ContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    private static final Pattern JVM_RESULT_MESSAGE_MATCHER = prepareJVMResultMessageMatcher(List.of("java.lang.AssertionError", "org.opentest4j.AssertionFailedError"));

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsBuildPlanCreatorProvider buildPlanCreatorProvider;

    private final RestTemplate restTemplate;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final JenkinsServer jenkinsServer;

    private final FeedbackService feedbackService;

    public JenkinsService(JenkinsBuildPlanCreatorProvider buildPlanCreatorFactory, @Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsServer jenkinsServer,
            ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackService feedbackService) {
        this.buildPlanCreatorProvider = buildPlanCreatorFactory;
        this.restTemplate = restTemplate;
        this.jenkinsServer = jenkinsServer;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.feedbackService = feedbackService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, URL repositoryURL, URL testRepositoryURL, URL solutionRepositoryURL) {
        try {
            // TODO support sequential test runs
            final var configBuilder = buildPlanCreatorProvider.builderFor(exercise.getProgrammingLanguage());
            Document jobConfig = configBuilder.buildBasicConfig(testRepositoryURL, repositoryURL, Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()));
            planKey = exercise.getProjectKey() + "-" + planKey;

            jenkinsServer.createJob(getFolderJob(exercise.getProjectKey()), planKey, writeXmlToString(jobConfig), useCrumb);
            getJob(exercise.getProjectKey(), planKey).build(useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Unable to create new build plan :" + planKey, e);
        }
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for Jenkins
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName,
            boolean targetProjectExists) {
        final var cleanTargetName = getCleanPlanName(targetPlanName);
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        final var targetPlanKey = targetProjectKey + "-" + cleanTargetName;
        final var jobXml = getJobXmlForBuildPlanWith(sourceProjectKey, sourcePlanKey);
        saveJobXml(jobXml, targetProjectKey, targetPlanKey);

        return targetPlanKey;
    }

    @Override
    public void givePlanPermissions(ProgrammingExercise programmingExercise, String planName) {
        // TODO after decision on how to handle users on Jenkins has been made if needed for Jenkins
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        updatePlanRepository(projectKey, planKey, ASSIGNMENT_REPO_NAME, null /* not important */, participation.getRepositoryUrl(), Optional.empty());
        enablePlan(projectKey, planKey);
    }

    // TODO this was a bad design choice. We should only have one configureBuildPlan method i.m.o
    @Override
    public void updatePlanRepository(String projectKey, String planName, String repoNameInCI, String vcsProject, String vcsRepositoryUrl, Optional<List<String>> triggeredBy) {

        // remove potential username from repo URL. Jenkins uses the Artemis Admin user and will fail if other usernames are in the URL
        final var repoUrl = vcsRepositoryUrl.replaceAll("(https?://)(.*@)(.*)", "$1$3");
        final var jobXmlDocument = getJobXmlForBuildPlanWith(projectKey, planName);
        final var remoteUrlNode = findUserRemoteConfigFor(jobXmlDocument, repoNameInCI);
        if (remoteUrlNode == null || remoteUrlNode.getFirstChild() == null) {
            throw new IllegalArgumentException("Url to replace not found in job xml document");
        }
        remoteUrlNode.getFirstChild().setNodeValue(repoUrl);
        final var errorMessage = "Error trying to configure build plan in Jenkins " + planName;
        postXml(jobXmlDocument, String.class, HttpStatus.OK, errorMessage, Endpoint.PLAN_CONFIG, projectKey, planName);
    }

    private org.w3c.dom.Node findUserRemoteConfigFor(Document jobXmlDocument, String repoNameInCI) {
        final var userRemoteConfigs = jobXmlDocument.getElementsByTagName("hudson.plugins.git.UserRemoteConfig");
        if (userRemoteConfigs.getLength() != 2) {
            throw new IllegalArgumentException("Configuration of build plans currently only supports a model with two repositories, ASSIGNMENT and TESTS");
        }
        var firstUserRemoteConfig = userRemoteConfigs.item(0).getChildNodes();
        var urlElement = findUrlElement(firstUserRemoteConfig, repoNameInCI);
        if (urlElement != null) {
            return urlElement;
        }
        var secondUserRemoteConfig = userRemoteConfigs.item(1).getChildNodes();
        urlElement = findUrlElement(secondUserRemoteConfig, repoNameInCI);
        if (urlElement != null) {
            return urlElement;
        }
        return null;
    }

    private org.w3c.dom.Node findUrlElement(NodeList nodeList, String repoNameInCI) {
        boolean found = false;
        org.w3c.dom.Node urlNode = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            var childElement = nodeList.item(i);
            if ("name".equalsIgnoreCase(childElement.getNodeName())) {
                var nameValue = childElement.hasChildNodes() ? childElement.getFirstChild().getNodeValue() : null;
                // this name was added recently, so we cannot assume that all job xml files include this name
                if (repoNameInCI.equalsIgnoreCase(nameValue)) {
                    found = true;
                }
            }
            else if ("url".equalsIgnoreCase(childElement.getNodeName())) {
                urlNode = childElement;
                if (!found) {
                    // fallback for old xmls
                    var urlValue = childElement.hasChildNodes() ? childElement.getFirstChild().getNodeValue() : null;
                    if (urlValue != null && repoNameInCI.equals(ASSIGNMENT_REPO_NAME) && ((urlValue.contains("-exercise.git") || (urlValue.contains("-solution.git"))))) {
                        found = true;
                    }
                    else if (urlValue != null && repoNameInCI.equals(TEST_REPO_NAME) && urlValue.contains("-tests.git")) {
                        found = true;
                    }
                }
            }
        }

        if (found && urlNode != null) {
            return urlNode;
        }
        else {
            return null;
        }
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();

        try {
            getJob(projectKey, planKey).build(useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error triggering build: " + planKey, e);
        }
    }

    @Override
    public void deleteProject(String projectKey) {
        try {
            jenkinsServer.deleteJob(projectKey, useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to delete folder in Jenkins for " + projectKey, e);
        }
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        try {
            jenkinsServer.deleteJob(getFolderJob(projectKey), buildPlanId, useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to delete job in Jenkins: " + buildPlanId, e);
        }
    }

    @Override
    public String getPlanKey(Object requestBody) throws Exception {
        final var result = TestResultsDTO.convert(requestBody);
        final var nameParams = result.getFullName().split(" ");
        /*
         * Jenkins gives the full name of a job as <FOLDER NAME> » <JOB NAME> <Build Number> E.g. the third build of an exercise (projectKey = TESTEXC) for its solution build
         * (TESTEXC-SOLUTION) would be: TESTEXC » TESTEXC-SOLUTION #3 ==> This would mean that at index 2, we have the actual job/plan key, i.e. TESTEXC-SOLUTION
         */
        if (nameParams.length != 4) {
            log.error("Can't extract planKey from requestBody! Not a test notification result!: " + new ObjectMapper().writeValueAsString(requestBody));
            throw new JenkinsException("Can't extract planKey from requestBody! Not a test notification result!: " + new ObjectMapper().writeValueAsString(requestBody));
        }

        return nameParams[2];
    }

    @Override
    public Result onBuildCompleted(ProgrammingExerciseParticipation participation, Object requestBody) {
        final var report = TestResultsDTO.convert(requestBody);
        final var latestPendingSubmission = programmingSubmissionRepository.findByParticipationIdAndResultIsNullOrderBySubmissionDateDesc(participation.getId()).stream()
                .filter(submission -> {
                    final var commitHash = getCommitHash(report, submission.getType());
                    return commitHash.isPresent() && submission.getCommitHash().equals(commitHash.get());
                }).findFirst();
        final var result = createResultFromBuildResult(report, (Participation) participation);
        final ProgrammingSubmission submission;
        submission = latestPendingSubmission.orElseGet(() -> createFallbackSubmission(participation, report));
        submission.setBuildFailed(result.getResultString().equals("No tests found"));
        result.setSubmission(submission);
        result.setRatedIfNotExceeded(participation.getProgrammingExercise().getDueDate(), submission);
        programmingSubmissionRepository.save(submission);
        // We can't save the result here, because we might later add more feedback items to the result (sequential test runs).
        // This seems like a bug in Hibernate/JPA: https://stackoverflow.com/questions/6763329/ordercolumn-onetomany-null-index-column-for-collection.
        return result;
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        return Optional.of(JENKINS_SERVER_URL + "/project/" + projectKey + "/" + buildPlanId);
    }

    @NotNull
    private ProgrammingSubmission createFallbackSubmission(ProgrammingExerciseParticipation participation, TestResultsDTO report) {
        ProgrammingSubmission submission;
        // There can be two reasons for the case that there is no programmingSubmission:
        // 1) Manual build triggered from Jenkins.
        // 2) An unknown error that caused the programming submission not to be created when the code commits have been pushed
        // we can still get the commit hash from the payload of the Jenkins REST Call and "reverse engineer" the programming submission object to be consistent
        final var commitHash = getCommitHash(report, SubmissionType.MANUAL);
        log.warn("Could not find pending ProgrammingSubmission for Commit-Hash {} (Participation {}, Build-Plan {}). Will create a new one subsequently...", commitHash,
                participation.getId(), participation.getBuildPlanId());
        submission = new ProgrammingSubmission();
        submission.setParticipation((Participation) participation);
        submission.setSubmitted(true);
        submission.setType(SubmissionType.OTHER);
        submission.setCommitHash(commitHash.get());
        submission.setSubmissionDate(report.getRunDate());
        // Save to avoid TransientPropertyValueException.
        programmingSubmissionRepository.save(submission);
        return submission;
    }

    private Result createResultFromBuildResult(TestResultsDTO report, Participation participation) {
        final var result = new Result();
        final var testSum = report.getSkipped() + report.getFailures() + report.getErrors() + report.getSuccessful();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(report.getSuccessful() == testSum);
        result.setCompletionDate(report.getRunDate());
        result.setScore((long) calculateResultScore(report, testSum));
        result.setParticipation(participation);
        addFeedbackToResult(result, report);
        // We assume the build has failed if no test case feedback has been sent. Static code analysis feedback might exist even though the build failed
        boolean hasTestCaseFeedback = result.getFeedbacks().stream().anyMatch(feedback -> !feedback.isStaticCodeAnalysisFeedback());
        result.setResultString(hasTestCaseFeedback ? report.getSuccessful() + " of " + testSum + " passed" : "No tests found");

        return result;
    }

    private double calculateResultScore(TestResultsDTO report, int testSum) {
        return ((1.0 * report.getSuccessful()) / testSum) * 100;
    }

    /**
     * Get the commit hash from the build map, the commit hash will be different for submission types or null.
     *
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise null.
     */
    private Optional<String> getCommitHash(TestResultsDTO report, SubmissionType submissionType) {
        final var assignmentSubmission = List.of(SubmissionType.MANUAL, SubmissionType.INSTRUCTOR).contains(submissionType);
        final var testSubmission = submissionType == SubmissionType.TEST;
        final var testSlugSuffix = RepositoryType.TESTS.getName();

        // It's either an assignment submission, so then we return the hash of the assignment (*-exercise, *-studentId),
        // or the hash of the test repository for test repo changes (*-tests)
        return report.getCommits().stream().filter(commitDTO -> (assignmentSubmission && !commitDTO.getRepositorySlug().endsWith(testSlugSuffix))
                || (testSubmission && commitDTO.getRepositorySlug().endsWith(testSlugSuffix))).map(CommitDTO::getHash).findFirst();
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        final var isQueued = getJob(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId()).isInQueue();
        if (isQueued) {
            return BuildStatus.QUEUED;
        }
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        final var url = Endpoint.LAST_BUILD.buildEndpoint(JENKINS_SERVER_URL.toString(), projectKey, planKey).build(true).toString();
        try {
            final var jobStatus = restTemplate.getForObject(url, JsonNode.class);

            return jobStatus.get("building").asBoolean() ? BuildStatus.BUILDING : BuildStatus.INACTIVE;
        }
        catch (HttpClientErrorException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error while trying to fetch build status from Jenkins for " + planKey, e);
        }
    }

    @Override
    public boolean checkIfBuildPlanExists(String projectKey, String buildPlanId) {
        try {
            getJobXmlForBuildPlanWith(projectKey, buildPlanId);
            return true;
        }
        catch (Exception emAll) {
            return false;
        }
    }

    private void addFeedbackToResult(Result result, TestResultsDTO report) {
        final ProgrammingExercise programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();
        final ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();

        // Extract test case feedback
        for (final var testSuite : report.getResults()) {
            for (final var testCase : testSuite.getTestCases()) {
                var errorMessage = Optional.ofNullable(testCase.getErrors()).map((errors) -> errors.get(0).getMessage());
                var failureMessage = Optional.ofNullable(testCase.getFailures()).map((failures) -> failures.get(0).getMessage());
                var errorList = errorMessage.or(() -> failureMessage).map(message -> processResultErrorMessage(programmingLanguage, message)).map(List::of)
                        .orElse(Collections.emptyList());
                boolean successful = Optional.ofNullable(testCase.getErrors()).map(List::isEmpty).orElse(true)
                        && Optional.ofNullable(testCase.getFailures()).map(List::isEmpty).orElse(true);

                if (!successful && errorList.isEmpty()) {
                    var errorType = Optional.ofNullable(testCase.getErrors()).map((errors) -> errors.get(0).getType());
                    var failureType = Optional.ofNullable(testCase.getFailures()).map((failures) -> failures.get(0).getType());
                    var message = errorType.or(() -> failureType).map(t -> String.format("Unsuccessful due to an error of type: %s", t));
                    if (message.isPresent()) {
                        errorList = List.of(message.get());
                    }
                }

                result.addFeedback(feedbackService.createFeedbackFromTestCase(testCase.getName(), errorList, successful, programmingLanguage, false));
            }
        }

        // Extract static code analysis feedback if option was enabled
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && report.getStaticCodeAnalysisReports() != null) {
            var scaFeedback = feedbackService.createFeedbackFromStaticCodeAnalysisReports(report.getStaticCodeAnalysisReports());
            result.addFeedbacks(scaFeedback);
        }

        result.setHasFeedback(!result.getFeedbacks().isEmpty());
    }

    /**
     * Filters and processes a feedback error message, thereby removing any unwanted strings depending on
     * the programming language, or just reformatting it to only show the most important details.
     *
     * ToDo: Move back to FeedbackService once a solution that works with Bamboo has been found
     *
     * @param programmingLanguage The programming language for which the feedback was generated
     * @param message The raw error message in the feedback
     * @return A filtered and better formatted error message
     */
    private String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final String message) {
        if (programmingLanguage == ProgrammingLanguage.JAVA || programmingLanguage == ProgrammingLanguage.KOTLIN) {
            return JVM_RESULT_MESSAGE_MATCHER.matcher(message.trim()).replaceAll("");
        }

        return message;
    }

    /**
     * Builds the regex used in {@link #processResultErrorMessage(ProgrammingLanguage, String)} on results from JVM languages.
     *
     * @param jvmExceptionsToFilter Exceptions at the start of lines that should be filtered out in the processing step
     * @return A regex that can be used to process result messages
     */
    private static Pattern prepareJVMResultMessageMatcher(List<String> jvmExceptionsToFilter) {
        // Replace all "." with "\\." and join with regex alternative symbol "|"
        String assertionRegex = jvmExceptionsToFilter.stream().map(s -> s.replaceAll("\\.", "\\\\.")).reduce("", (a, b) -> String.join("|", a, b));
        // Match any of the exceptions at the start of the line and with ": " after it
        String pattern = String.format("^(?:%s): \n*", assertionRegex);

        return Pattern.compile(pattern, Pattern.MULTILINE);
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) programmingSubmission.getParticipation();
        String projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
        String buildPlanId = programmingExerciseParticipation.getBuildPlanId();

        try {
            final var build = getJob(projectKey, buildPlanId).getLastBuild();
            final var logHtml = Jsoup.parse(build.details().getConsoleOutputHtml()).body();
            final var buildLog = new LinkedList<BuildLogEntry>();
            final var iterator = logHtml.childNodes().iterator();
            while (iterator.hasNext()) {
                final var node = iterator.next();
                final String log;
                // For timestamps, parse the <b> tag containing the time as hh:mm:ss
                if (node.attributes().get("class").contains("timestamp")) {
                    final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                    final var time = ZonedDateTime.parse(timeAsString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"));
                    log = reduceToText(iterator.next());
                    buildLog.add(new BuildLogEntry(time, stripLogEndOfLine(log)));
                }
                else {
                    // Log is from the same line as the last
                    // Look for next text node in children
                    log = reduceToText(node);
                    final var lastLog = buildLog.getLast();
                    lastLog.setLog(lastLog.getLog() + stripLogEndOfLine(log));
                }
            }
            // Jenkins logs all steps of the build pipeline. We remove those as they are irrelevant to the students
            LinkedList<BuildLogEntry> prunedBuildLog = new LinkedList<>();
            final Iterator<BuildLogEntry> buildlogIterator = buildLog.iterator();
            while (buildlogIterator.hasNext()) {
                BuildLogEntry entry = buildlogIterator.next();

                if (entry.getLog().contains("Compilation failure")) {
                    break;
                }
                // filter unnecessary logs
                if (!((entry.getLog().startsWith("[INFO]") && !entry.getLog().contains("error")) || !entry.getLog().startsWith("[ERROR]") || entry.getLog().startsWith("[WARNING]")
                        || entry.getLog().startsWith("[ERROR] [Help 1]") || entry.getLog().startsWith("[ERROR] For more information about the errors and possible solutions")
                        || entry.getLog().startsWith("[ERROR] Re-run Maven using") || entry.getLog().startsWith("[ERROR] To see the full stack trace of the errors")
                        || entry.getLog().startsWith("[ERROR] -> [Help 1]") || entry.getLog().equals("[ERROR] "))) {
                    // Remove the path from the log entries
                    String path = "/var/jenkins_home/workspace/" + projectKey + "/" + buildPlanId + "/";
                    entry.setLog(entry.getLog().replace(path, ""));
                    prunedBuildLog.add(entry);
                }
            }

            return prunedBuildLog;
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private String stripLogEndOfLine(String log) {
        return log.replaceAll("\\r|\\n", "");
    }

    private String reduceToText(Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).getWholeText();
        }

        return reduceToText(node.childNode(0));
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
            else {
                return "The project " + projectKey + " already exists in the CI Server. Please choose a different short name!";
            }
        }
        catch (Exception emAll) {
            log.warn(emAll.getMessage());
            // in case of an error message, we assume the project does not exist
            return null;
        }
    }

    @Override
    public boolean isBuildPlanEnabled(String projectKey, String planId) {
        return getJob(projectKey, planId).isBuildable();
    }

    @Override
    public void enablePlan(String projectKey, String planKey) {
        post(Endpoint.ENABLE, HttpStatus.FOUND, "Unable to enable plan " + planKey, String.class, projectKey, planKey);
    }

    @Override
    public void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        // TODO after decision on how to handle users on Jenkins has been made
    }

    @Override
    public void removeAllDefaultProjectPermissions(String projectKey) {
        // TODO after decision on how to handle users on Jenkins has been made
    }

    @Override
    public ConnectorHealth health() {
        try {
            final var isRunning = jenkinsServer.isRunning();
            if (!isRunning) {
                return new ConnectorHealth(new JenkinsException("Jenkins Server is down!"));
            }
            return new ConnectorHealth(true, Map.of("url", JENKINS_SERVER_URL));
        }
        catch (Exception emAll) {
            return new ConnectorHealth(emAll);
        }
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
        try {
            jenkinsServer.createFolder(programmingExercise.getProjectKey(), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException("Error creating folder for exercise " + programmingExercise, e);
        }
    }

    private FolderJob getFolderJob(String folderName) {
        try {
            final var job = jenkinsServer.getJob(folderName);
            if (job == null) {
                throw new JenkinsException("The job " + folderName + " does not exist!");
            }
            final var folderJob = jenkinsServer.getFolderJob(job);
            if (!folderJob.isPresent()) {
                throw new JenkinsException("Folder " + folderName + " does not exist!");
            }
            return folderJob.get();
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private JobWithDetails getJob(String projectKey, String jobName) {
        final var folder = getFolderJob(projectKey);
        try {
            return jenkinsServer.getJob(folder, jobName);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private Document getJobXmlForBuildPlanWith(String projectKey, String jobName) {
        try {
            final var xmlString = jenkinsServer.getJobXml(getFolderJob(projectKey), jobName);
            return XmlFileUtils.readFromString(xmlString);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private void saveJobXml(Document jobXml, String projectKey, String planName) {
        final var folder = getFolderJob(projectKey);
        try {
            jenkinsServer.createJob(folder, planName, writeXmlToString(jobXml), useCrumb);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private <T> T post(Endpoint endpoint, HttpStatus allowedStatus, String messageInCaseOfError, Class<T> responseType, Object... args) {
        final var builder = endpoint.buildEndpoint(JENKINS_SERVER_URL.toString(), args);
        try {
            final var response = restTemplate.postForEntity(builder.build(true).toString(), null, responseType);
            if (response.getStatusCode() != allowedStatus) {
                throw new JenkinsException(
                        messageInCaseOfError + "; statusCode=" + response.getStatusCode() + "; headers=" + response.getHeaders() + "; body=" + response.getBody());
            }
            return response.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error(messageInCaseOfError, e);
            throw new JenkinsException(messageInCaseOfError, e);
        }
    }

    private <T> T postXml(Document doc, Class<T> responseType, HttpStatus allowedStatus, String messagInCaseOfError, Endpoint endpoint, Object... args) {
        return postXml(doc, responseType, List.of(allowedStatus), messagInCaseOfError, endpoint, null, args);
    }

    private <T> T postXml(Document doc, Class<T> responseType, List<HttpStatus> allowedStatuses, String messagInCaseOfError, Endpoint endpoint,
            @Nullable Map<String, Object> queryParams, Object... args) {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        final var builder = endpoint.buildEndpoint(JENKINS_SERVER_URL.toString(), args);
        if (queryParams != null) {
            queryParams.forEach(builder::queryParam);
        }
        final var entity = new HttpEntity<>(writeXmlToString(doc), headers);

        try {
            final var response = restTemplate.exchange(builder.build(true).toString(), HttpMethod.POST, entity, responseType);
            if (!allowedStatuses.contains(response.getStatusCode())) {
                throw new JenkinsException(
                        messagInCaseOfError + "; statusCode=" + response.getStatusCode() + "; headers=" + response.getHeaders() + "; body=" + response.getBody());
            }

            return response.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error(messagInCaseOfError, e);
            throw new JenkinsException(messagInCaseOfError, e);
        }
    }

    private String writeXmlToString(Document doc) {
        try {
            final var tf = TransformerFactory.newInstance();
            final var transformer = tf.newTransformer();
            final var writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.getBuffer().toString();
        }
        catch (TransformerException e) {
            final var errorMessage = "Unable to parse XML document to String! " + doc;
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }

    private enum Endpoint {

        NEW_PLAN("job", "<projectKey>", "createItem"), NEW_FOLDER("createItem"), DELETE_FOLDER("job", "<projectKey>", "doDelete"),
        DELETE_JOB("job", "<projectKey>", "job", "<planName>", "doDelete"), PLAN_CONFIG("job", "<projectKey>", "job", "<planKey>", "config.xml"),
        TRIGGER_BUILD("job", "<projectKey>", "job", "<planKey>", "build"), ENABLE("job", "<projectKey>", "job", "<planKey>", "enable"),
        TEST_RESULTS("job", "<projectKey>", "job", "<planKey>", "lastBuild", "testResults", "api", "json"),
        LAST_BUILD("job", "<projectKey>", "job", "<planKey>", "lastBuild", "api", "json");

        private List<String> pathSegments;

        Endpoint(String... pathSegments) {
            this.pathSegments = Arrays.asList(pathSegments);
        }

        public UriComponentsBuilder buildEndpoint(String baseUrl, Object... args) {
            return UrlUtils.buildEndpoint(baseUrl, pathSegments, args);
        }
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
