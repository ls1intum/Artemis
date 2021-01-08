package de.tum.in.www1.artemis.service.connectors.jenkins;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.validation.constraints.NotNull;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
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
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
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

    private static final String PIPELINE_SCRIPT_DETECTION_COMMENT = "// ARTEMIS: JenkinsPipeline";

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsBuildPlanCreator jenkinsBuildPlanCreator;

    private final RestTemplate restTemplate;

    private final RestTemplate shortTimeoutRestTemplate;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResultRepository resultRepository;

    private final JenkinsServer jenkinsServer;

    private final FeedbackService feedbackService;

    private final BuildLogEntryService buildLogService;

    // Pattern of the DateTime that is included in the logs received from Jenkins
    private final DateTimeFormatter logDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

    public JenkinsService(JenkinsBuildPlanCreator jenkinsBuildPlanCreator, @Qualifier("jenkinsRestTemplate") RestTemplate restTemplate, JenkinsServer jenkinsServer,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository, FeedbackService feedbackService,
            ResultRepository resultRepository, @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate, BuildLogEntryService buildLogService) {
        this.jenkinsBuildPlanCreator = jenkinsBuildPlanCreator;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.restTemplate = restTemplate;
        this.jenkinsServer = jenkinsServer;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.feedbackService = feedbackService;
        this.resultRepository = resultRepository;
        this.buildLogService = buildLogService;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL) {
        try {
            // TODO support sequential test runs
            final var configBuilder = builderFor(exercise.getProgrammingLanguage());
            Document jobConfig = configBuilder.buildBasicConfig(exercise.getProgrammingLanguage(), testRepositoryURL, repositoryURL,
                    Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()));
            planKey = exercise.getProjectKey() + "-" + planKey;

            jenkinsServer.createJob(getFolderJob(exercise.getProjectKey()), planKey, writeXmlToString(jobConfig), useCrumb);
            getJob(exercise.getProjectKey(), planKey).build(useCrumb);
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
        // Refetch the programming exercise with the template participation and assign it to programmingExerciseParticipation to make sure it is initialized (and not a proxy)
        final var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(participation.getProgrammingExercise().getId()).get();
        participation.setProgrammingExercise(programmingExercise);
        final var projectKey = programmingExercise.getProjectKey();
        final var planKey = participation.getBuildPlanId();
        final var templateRepoUrl = programmingExercise.getTemplateRepositoryUrl();
        updatePlanRepository(projectKey, planKey, ASSIGNMENT_REPO_NAME, null /* not needed */, participation.getRepositoryUrl(), templateRepoUrl, Optional.empty());
        enablePlan(projectKey, planKey);
    }

    @Override
    public void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl,
            Optional<List<String>> optionalTriggeredByRepositories) {

        // remove potential username from repo URL. Jenkins uses the Artemis Admin user and will fail if other usernames are in the URL
        final var repoUrl = newRepoUrl.replaceAll("(https?://)(.*@)(.*)", "$1$3");
        final var jobXmlDocument = getJobXmlForBuildPlanWith(buildProjectKey, buildPlanKey);

        try {
            replaceScriptParameters(jobXmlDocument, ciRepoName, repoUrl, existingRepoUrl);
        }
        catch (IllegalArgumentException e) {
            log.info("Falling back to old Jenkins setup replacement for build xml");
            replaceRemoteURLs(jobXmlDocument, repoUrl, ciRepoName);
        }

        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        final var entity = new HttpEntity(writeXmlToString(jobXmlDocument), headers);

        URI uri = Endpoint.PLAN_CONFIG.buildEndpoint(jenkinsServerUrl.toString(), buildProjectKey, buildPlanKey).build(true).toUri();

        final var errorMessage = "Error trying to configure build plan in Jenkins " + buildPlanKey;
        try {
            final var response = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new JenkinsException(errorMessage + "; statusCode=" + response.getStatusCode() + "; headers=" + response.getHeaders() + "; body=" + response.getBody());
            }
        }
        catch (HttpClientErrorException e) {
            log.error(errorMessage, e);
            throw new JenkinsException(errorMessage, e);
        }
    }

    private void replaceScriptParameters(Document jobXmlDocument, String ciRepoName, String repoUrl, String baseRepoUrl) throws IllegalArgumentException {
        final var scriptNode = findScriptNode(jobXmlDocument);
        if (scriptNode == null || scriptNode.getFirstChild() == null) {
            log.debug("Pipeline Script not found");
            throw new IllegalArgumentException("Pipeline Script not found");
        }

        String pipeLineScript = scriptNode.getFirstChild().getTextContent().trim();
        // If the script does not start with "pipeline" or the special comment,
        // it is not actually a pipeline script, but a deprecated programming exercise with an old build xml configuration
        if (!pipeLineScript.startsWith("pipeline") && !pipeLineScript.startsWith(PIPELINE_SCRIPT_DETECTION_COMMENT)) {
            log.debug("Pipeline Script not found");
            throw new IllegalArgumentException("Pipeline Script not found");
        }
        // Replace repo URL
        // TODO: properly replace the baseRepoUrl with repoUrl by looking up the ciRepoName in the pipelineScript
        pipeLineScript = pipeLineScript.replace(baseRepoUrl, repoUrl);

        scriptNode.getFirstChild().setTextContent(pipeLineScript);
    }

    /**
     * Replace old XML files that are not based on pipelines.
     * Will be removed in the future
     * @param jobXmlDocument the Document where the remote config should replaced
     */
    @Deprecated
    private void replaceRemoteURLs(Document jobXmlDocument, String repoUrl, String repoNameInCI) throws IllegalArgumentException {
        final var remoteUrlNode = findUserRemoteConfigFor(jobXmlDocument, repoNameInCI);
        if (remoteUrlNode == null || remoteUrlNode.getFirstChild() == null) {
            throw new IllegalArgumentException("Url to replace not found in job xml document");
        }
        remoteUrlNode.getFirstChild().setNodeValue(repoUrl);
    }

    private org.w3c.dom.Node findScriptNode(Document jobXmlDocument) {
        final var userRemoteConfigs = jobXmlDocument.getElementsByTagName("script");
        return userRemoteConfigs.item(0);
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
        return urlElement;
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
        final var latestPendingSubmission = programmingSubmissionRepository.findByParticipationIdAndResultsIsNullOrderBySubmissionDateDesc(participation.getId()).stream()
                .filter(submission -> {
                    final var commitHash = getCommitHash(report, submission.getType());
                    return commitHash.isPresent() && submission.getCommitHash().equals(commitHash.get());
                }).findFirst();
        var result = createResultFromBuildResult(report, (Participation) participation);
        final ProgrammingSubmission submission;
        submission = latestPendingSubmission.orElseGet(() -> createFallbackSubmission(participation, report));
        submission.setBuildFailed(result.getResultString().equals("No tests found"));

        // save result to create entry in DB before establishing relation with submission for ordering
        result = resultRepository.save(result);

        result.setSubmission(submission);
        submission.addResult(result);
        result.setRatedIfNotExceeded(participation.getProgrammingExercise().getDueDate(), submission);
        programmingSubmissionRepository.save(submission);
        // We can't save the result here, because we might later add more feedback items to the result (sequential test runs).
        // This seems like a bug in Hibernate/JPA: https://stackoverflow.com/questions/6763329/ordercolumn-onetomany-null-index-column-for-collection.
        return result;
    }

    @Override
    public Optional<String> getWebHookUrl(String projectKey, String buildPlanId) {
        return Optional.of(jenkinsServerUrl + "/project/" + projectKey + "/" + buildPlanId);
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
        if (participation.getBuildPlanId() == null) {
            // The build plan does not exist, the build status cannot be retrieved
            return null;
        }
        final var isQueued = getJob(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId()).isInQueue();
        if (isQueued) {
            return BuildStatus.QUEUED;
        }
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        final var url = Endpoint.LAST_BUILD.buildEndpoint(jenkinsServerUrl.toString(), projectKey, planKey).build(true).toString();
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
                var errorMessage = Optional.ofNullable(testCase.getErrors()).map((errors) -> errors.get(0).getMostInformativeMessage());
                var failureMessage = Optional.ofNullable(testCase.getFailures()).map((failures) -> failures.get(0).getMostInformativeMessage());
                var errorList = errorMessage.or(() -> failureMessage).map(List::of).orElse(Collections.emptyList());
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

                result.addFeedback(feedbackService.createFeedbackFromTestCase(testCase.getName(), errorList, successful, programmingLanguage));
            }
        }

        // Extract static code analysis feedback if option was enabled
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && report.getStaticCodeAnalysisReports() != null) {
            var scaFeedback = feedbackService.createFeedbackFromStaticCodeAnalysisReports(report.getStaticCodeAnalysisReports());
            result.addFeedbacks(scaFeedback);
        }

        // Relevant feedback is negative
        result.setHasFeedback(result.getFeedbacks().stream().anyMatch(fb -> !fb.isPositive()));
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) programmingSubmission.getParticipation();
        String projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
        String buildPlanId = programmingExerciseParticipation.getBuildPlanId();
        ProgrammingLanguage programmingLanguage = programmingExerciseParticipation.getProgrammingExercise().getProgrammingLanguage();

        try {
            final var build = getJob(projectKey, buildPlanId).getLastBuild();
            final var logHtml = Jsoup.parse(build.details().getConsoleOutputHtml()).body();

            List<BuildLogEntry> buildLog;
            try {
                buildLog = parsePipelineLogs(logHtml);
            }
            catch (IllegalArgumentException e) {
                buildLog = parseLogsLegacy(logHtml);
            }

            // Jenkins logs all steps of the build pipeline. We remove those as they are irrelevant to the students
            List<BuildLogEntry> prunedBuildLogs = new ArrayList<>();
            for (BuildLogEntry entry : buildLog) {
                String logString = entry.getLog();
                if (logString.contains("Compilation failure")) {
                    break;
                }

                // filter unnecessary logs and illegal reflection logs
                if (buildLogService.isUnnecessaryBuildLogForProgrammingLanguage(logString, programmingLanguage) || buildLogService.isIllegalReflectionLog(logString)) {
                    continue;
                }

                // Jenkins outputs each executed shell command with '+ <shell command>'
                if (logString.startsWith("+ ")) {
                    continue;
                }

                // Remove the path from the log entries
                // TODO: use shortenedLogString from Bamboo?
                // When using local agents, this is the path where the workspace should be located
                String path = "/var/jenkins_home/workspace/" + projectKey + "/" + buildPlanId + ASSIGNMENT_DIRECTORY;
                entry.setLog(entry.getLog().replace(path, ""));

                // When using remote agents, this is the path where the workspace should be located
                path = "/home/jenkins/remote_agent/workspace/" + projectKey + "/" + buildPlanId + ASSIGNMENT_DIRECTORY;
                entry.setLog(entry.getLog().replace(path, ""));

                prunedBuildLogs.add(entry);
            }

            // Save build logs
            var savedBuildLogs = buildLogService.saveBuildLogs(prunedBuildLogs, programmingSubmission);
            programmingSubmission.setBuildLogEntries(savedBuildLogs);
            programmingSubmissionRepository.save(programmingSubmission);

            return prunedBuildLogs;
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private List<BuildLogEntry> parsePipelineLogs(Element logHtml) throws IllegalArgumentException {
        final var buildLog = new LinkedList<BuildLogEntry>();
        if (logHtml.childNodes().stream().noneMatch(child -> child.attr("class").contains("pipeline"))) {
            throw new IllegalArgumentException("Log is not pipeline log");
        }
        for (Element elem : logHtml.children()) {
            // Only pipeline-node-ID elements contain actual log entries
            if (elem.attributes().get("class").contains("pipeline-node")) {
                // At least one child must have a timestamp class
                if (elem.childNodes().stream().anyMatch(child -> child.attr("class").contains("timestamp"))) {
                    Iterator<Node> nodeIterator = elem.childNodes().iterator();

                    while (nodeIterator.hasNext()) {
                        Node node = nodeIterator.next();
                        String log;
                        if (node.attributes().get("class").contains("timestamp")) {
                            final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                            final var time = ZonedDateTime.parse(timeAsString, logDateTimeFormatter);
                            var contentCandidate = nodeIterator.next();

                            // Skip invisible entries (they contain only the timestamp, but we already got that above)
                            if (contentCandidate.attr("style").contains("display: none")) {
                                contentCandidate = nodeIterator.next();
                            }
                            log = reduceToText(contentCandidate);

                            // There are color codes in the logs that need to be filtered out.
                            // This is needed for old programming exercises
                            // For example:[[1;34mINFO[m] is changed to [INFO]
                            log = log.replace("\u001B[1;34m", "");
                            log = log.replace("\u001B[m", "");
                            log = log.replace("\u001B[1;31m", "");
                            buildLog.add(new BuildLogEntry(time, stripLogEndOfLine(log).trim()));
                        }
                        else {
                            // Log is from the same line as the last
                            // Look for next text node in children
                            log = reduceToText(node);
                            final var lastLog = buildLog.getLast();
                            lastLog.setLog(lastLog.getLog() + stripLogEndOfLine(log).trim());
                        }
                    }
                }
            }
        }
        return buildLog;

    }

    private List<BuildLogEntry> parseLogsLegacy(Element logHtml) {
        final var buildLog = new LinkedList<BuildLogEntry>();
        final var iterator = logHtml.childNodes().iterator();
        while (iterator.hasNext()) {
            final var node = iterator.next();
            final String log;
            // For timestamps, parse the <b> tag containing the time as hh:mm:ss
            if (node.attributes().get("class").contains("timestamp")) {
                final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                final var time = ZonedDateTime.parse(timeAsString, logDateTimeFormatter);
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

    private String stripLogEndOfLine(String log) {
        return log.replaceAll("[\\r\\n]", "");
    }

    private String reduceToText(Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).getWholeText();
        }

        return reduceToText(node.childNode(node.childNodeSize() - 1));
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
            // Note: we simply check if the login page is reachable
            shortTimeoutRestTemplate.getForObject(jenkinsServerUrl + "/login", String.class);
            return new ConnectorHealth(true, Map.of("url", jenkinsServerUrl));
        }
        catch (Exception emAll) {
            return new ConnectorHealth(new JenkinsException("Jenkins Server is down!"));
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
        if (projectKey == null || jobName == null) {
            log.warn("Cannot get the job, because projectKey " + projectKey + " or jobName " + jobName + " is null");
            return null;
        }
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
        final var builder = endpoint.buildEndpoint(jenkinsServerUrl.toString(), args);
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

        private final List<String> pathSegments;

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
