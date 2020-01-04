package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
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

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    private final JenkinsBuildPlanCreatorProvider buildPlanCreatorProvider;

    private final RestTemplate restTemplate;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private JenkinsServer jenkinsServer;

    public JenkinsService(JenkinsBuildPlanCreatorProvider buildPlanCreatorFactory, @Qualifier("jenkinsRestTemplate") RestTemplate restTemplate,
            ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.buildPlanCreatorProvider = buildPlanCreatorFactory;
        this.restTemplate = restTemplate;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
    }

    @PostConstruct
    public void init() throws URISyntaxException {
        this.jenkinsServer = new JenkinsServer(JENKINS_SERVER_URL.toURI(), username, password);
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, URL repositoryURL, URL testRepositoryURL) {
        final var configBuilder = buildPlanCreatorProvider.builderFor(exercise.getProgrammingLanguage());
        final var jobConfig = configBuilder.buildBasicConfig(testRepositoryURL, repositoryURL);
        planKey = exercise.getProjectKey() + "-" + planKey;

        try {
            jenkinsServer.createJob(folder(exercise.getProjectKey()), planKey, writeXmlToString(jobConfig), true);
            job(exercise.getProjectKey(), planKey).build(true);
        }
        catch (IOException e) {
            throw new JenkinsException("Unable to create new build plan :" + planKey);
        }
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for Jenkins
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName) {
        final var cleanTargetName = getCleanPlanName(targetPlanName);
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        final var targetPlanKey = targetProjectKey + "-" + cleanTargetName;
        final var jobXml = jobXml(sourceProjectKey, sourcePlanKey);
        saveJobXml(jobXml, targetProjectKey, targetPlanKey);

        return targetPlanKey;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        updatePlanRepository(projectKey, planKey, null /* not important */, null /* not important */, participation.getRepositoryUrl(), Optional.empty());
        enablePlan(projectKey, planKey);
    }

    // TODO this was a bad design choice. We should only have one configureBuildPlan method i.m.o
    @Override
    public void updatePlanRepository(String projectKey, String planName, String repoNameInCI, String vcsProject, String vcsRepositoryUrl, Optional<List<String>> triggeredBy) {
        // remove potential username from repo URL. Jenkins uses the Artemis Admin user and will fail if other usernames are in the URL
        final var repoUrl = vcsRepositoryUrl.replaceAll("(https?://)(.*@)(.*)", "$1$3");
        final var config = jobXml(projectKey, planName);
        final var urlElements = config.getElementsByTagName("url");
        if (urlElements.getLength() != 2) {
            throw new IllegalArgumentException("Configuration of build plans currently only supports a model with two repositories, ASSIGNMENT and TESTS");
        }
        urlElements.item(1).getFirstChild().setNodeValue(repoUrl);

        final var errorMessage = "Error trying to configure build plan in Jenkins " + planName;
        postXml(config, String.class, HttpStatus.OK, errorMessage, Endpoint.PLAN_CONFIG, projectKey, planName);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();

        try {
            job(projectKey, planKey).build(true);
        }
        catch (IOException e) {
            throw new JenkinsException("Error triggering build: " + planKey, e);
        }
    }

    @Override
    public void deleteProject(String projectKey) {
        try {
            jenkinsServer.deleteJob(projectKey, true);
        }
        catch (IOException e) {
            throw new JenkinsException("Error while trying to delete folder in Jenkins for " + projectKey, e);
        }
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        try {
            jenkinsServer.deleteJob(folder(projectKey), buildPlanId, true);
        }
        catch (IOException e) {
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
            throw new JenkinsException("Can't extract planKey from requestBody! Not a test notification result!: " + new ObjectMapper().writeValueAsString(requestBody));
        }

        return nameParams[2];
    }

    @Override
    public Result onBuildCompletedNew(ProgrammingExerciseParticipation participation, Object requestBody) {
        final var report = TestResultsDTO.convert(requestBody);
        final var latestPendingSubmission = programmingSubmissionRepository.findByParticipationIdAndResultIsNullOrderBySubmissionDateDesc(participation.getId()).stream()
                .filter(submission -> {
                    final var commitHash = getCommitHash(report, submission.getType());
                    return commitHash.isPresent() && submission.getCommitHash().equals(commitHash.get());
                }).findFirst();
        final var result = createResultFromBuildResult(report, (Participation) participation);
        final ProgrammingSubmission submission;
        submission = latestPendingSubmission.orElseGet(() -> createFallbackSubmission(participation, report));
        result.setSubmission(submission);
        result.setRatedIfNotExceeded(participation.getProgrammingExercise().getDueDate(), submission);
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
        result.setResultString(result.getHasFeedback() ? report.getSuccessful() + " of " + testSum + " passed" : "Build Error");

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
        final var isQueued = job(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId()).isInQueue();
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
            throw new JenkinsException("Error while trying to fetch build status from Jenkins for " + planKey, e);
        }
    }

    @Override
    public boolean buildPlanIdIsValid(String projectKey, String buildPlanId) {
        try {
            jobXml(projectKey, buildPlanId);
            return true;
        }
        catch (Exception emAll) {
            return false;
        }
    }

    @Override
    public Optional<Result> retrieveLatestBuildResult(ProgrammingExerciseParticipation participation, ProgrammingSubmission submission) {
        final var report = fetchLatestBuildResultFromJenkins(participation);

        // The retrieved build result must match the commitHash of the provided submission.
        if (report.getCommits().stream().map(CommitDTO::getHash).noneMatch(hash -> hash.equals(submission.getCommitHash()))) {
            return Optional.empty();
        }

        final var result = createResultFromBuildResult(report, (Participation) participation);
        result.setRatedIfNotExceeded(report.getRunDate(), submission);
        result.setSubmission(submission);

        return Optional.empty();
    }

    private void addFeedbackToResult(Result result, TestResultsDTO report) {
        // No feedback for build errors
        if (report.getResults() == null || report.getResults().isEmpty()) {
            result.setHasFeedback(false);
            return;
        }

        final var feedbacks = report.getResults().stream().flatMap(testsuite -> testsuite.getTestCases().stream()).map(testCase -> {
            final var feedback = new Feedback();
            feedback.setPositive(testCase.getErrors() == null && testCase.getFailures() == null);
            feedback.setText(testCase.getName());
            String errorMessage = null;
            // If we have errors or failures, they will always be of length == 1 since JUnit (and the format itself)
            // should generally only report the first failure in a test case
            if (testCase.getErrors() != null) {
                errorMessage = testCase.getErrors().get(0).getMessage();
            }
            else if (testCase.getFailures() != null) {
                errorMessage = testCase.getFailures().get(0).getMessage();
            }
            // The assertion message can be longer than the allowed char limit, so we shorten it here if needed.
            if (errorMessage != null && errorMessage.length() > FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS) {
                errorMessage = errorMessage.substring(0, FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS);
            }
            feedback.setDetailText(errorMessage);

            return feedback;
        }).collect(Collectors.toList());

        result.setHasFeedback(true);
        result.addFeedbacks(feedbacks);
    }

    private TestResultsDTO fetchLatestBuildResultFromJenkins(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        final var url = Endpoint.TEST_RESULTS.buildEndpoint(JENKINS_SERVER_URL.toString(), projectKey, planKey).build(true);

        return restTemplate.getForObject(url.toUri(), TestResultsDTO.class);
    }

    @Override
    public List<Feedback> getLatestBuildResultDetails(Result result) {
        // TODO since this is unused as of now
        throw new UnsupportedOperationException("Jenkins service does not support fetching the latest feedback for a result");
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(String projectKey, String buildPlanId) {
        try {
            final var build = job(projectKey, buildPlanId).getLastBuild();
            final var logHtml = Jsoup.parse(build.details().getConsoleOutputHtml()).body();
            final var buildLog = new LinkedList<BuildLogEntry>();
            final var iterator = logHtml.childNodes().iterator();
            while (iterator.hasNext()) {
                final var node = iterator.next();
                final String log;
                // For timestamps, parse the <b> tag containing the time as hh:mm:ss
                if (node.attributes().get("class").contains("timestamp")) {
                    final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                    final var time = ZonedDateTime.parse(timeAsString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
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

            return buildLog;
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
    public ResponseEntity retrieveLatestArtifact(ProgrammingExerciseParticipation participation) {
        // TODO, not necessary for the core functionality
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        try {
            folder(projectKey);
        }
        catch (Exception emAll) {
            return emAll.getMessage();
        }

        return null;
    }

    @Override
    public boolean isBuildPlanEnabled(String projectKey, String planId) {
        return job(projectKey, planId).isBuildable();
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
            jenkinsServer.createFolder(programmingExercise.getProjectKey(), true);
        }
        catch (IOException e) {
            throw new JenkinsException("Error creating folder for exercise " + programmingExercise, e);
        }
    }

    private FolderJob folder(String folderName) {
        try {
            final var folder = jenkinsServer.getFolderJob(jenkinsServer.getJob(folderName));
            if (!folder.isPresent()) {
                throw new JenkinsException("Folder " + folderName + " does not exist!");
            }
            return folder.get();
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private JobWithDetails job(String projectKey, String jobName) {
        final var folder = folder(projectKey);
        try {
            return jenkinsServer.getJob(folder, jobName);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private Document jobXml(String projectKey, String jobName) {
        try {
            final var xmlString = jenkinsServer.getJobXml(folder(projectKey), jobName);
            return XmlFileUtils.readFromString(xmlString);
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private void saveJobXml(Document jobXml, String projectKey, String planName) {
        final var folder = folder(projectKey);
        try {
            jenkinsServer.createJob(folder, planName, writeXmlToString(jobXml), true);
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
            log.error(messageInCaseOfError);
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
            log.error(messagInCaseOfError);
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
            log.error(errorMessage);
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
