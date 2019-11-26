package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.jenkins.model.TestResults;
import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Profile("jenkins")
@Service
public class JenkinsService implements ContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    @Value("${artemis.jenkins.user}")
    private String username;

    @Value("${artemis.jenkins.password}")
    private String password;

    @Value("${artemis.jenkins.url}")
    private URL JENKINS_SERVER_URL;

    private final JenkinsBuildPlanCreatorFactory buildPlanCreatorFactory;

    private final RestTemplate restTemplate;

    private JenkinsServer jenkinsServer;

    public JenkinsService(JenkinsBuildPlanCreatorFactory buildPlanCreatorFactory, @Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
        this.buildPlanCreatorFactory = buildPlanCreatorFactory;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() throws URISyntaxException {
        this.jenkinsServer = new JenkinsServer(JENKINS_SERVER_URL.toURI(), username, password);
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, URL repositoryURL, URL testRepositoryURL) {
        final var configBuilder = buildPlanCreatorFactory.builderFor(exercise.getProgrammingLanguage());
        final var jobConfig = configBuilder.buildBasicConfig(testRepositoryURL, repositoryURL);

        postXml(jobConfig, String.class, HttpStatus.OK, "", Endpoint.NEW_PLAN, Map.of("name", planKey), exercise.getProjectKey());
    }

    @Override
    public void performEmptySetupCommit(ProgrammingExerciseParticipation participation) {
        // Not needed for Jenkins
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName) {
        final var jobXml = jobXml(sourceProjectKey, sourcePlanName);
        final var cleanTargetName = getCleanPlanName(targetPlanName);
        saveJobXml(jobXml, targetProjectKey, getCleanPlanName(cleanTargetName));

        return cleanTargetName;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {
        final var config = getPlanConfig(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());
        final var urlElements = config.getElementsByTagName("url");
        if (urlElements.getLength() != 2) {
            throw new IllegalArgumentException("Configuration of build plans currently only supports a model with two repositories, ASSIGNMENT and TESTS");
        }
        urlElements.item(1).getFirstChild().setNodeValue(participation.getRepositoryUrl());

        final var errorMessage = "Error trying to configure build plan in Jenkins " + participation.getBuildPlanId();
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        postXml(config, String.class, HttpStatus.OK, errorMessage, Endpoint.PLAN_CONFIG, projectKey, planKey);
    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();

        final var errorMessage = "Unable to trigger build " + planKey;
        post(Endpoint.TRIGGER_BUILD, HttpStatus.OK, errorMessage, String.class, projectKey, planKey);
    }

    @Override
    public void deleteProject(String projectKey) {
        final var errorMessage = "Error while trying to delete folder in Jenkins for " + projectKey;
        post(Endpoint.DELETE_FOLDER, HttpStatus.OK, errorMessage, String.class, projectKey);
    }

    @Override
    public void deleteBuildPlan(String projectKey, String buildPlanId) {
        final var errorMessage = "Error while trying to delete job in Jenkins: " + buildPlanId;
        post(Endpoint.DELETE_JOB, HttpStatus.OK, errorMessage, String.class, projectKey, buildPlanId);
    }

    @Override
    public String getPlanKey(Object requestBody) throws Exception {
        final var result = new ObjectMapper().convertValue(requestBody, TestResults.class);
        final var nameParams = result.getFullName().split(" ");
        if (nameParams.length != 4) {
            throw new JenkinsException("Can't extract planKey from requestBody! Not a test notification result!: " + new ObjectMapper().writeValueAsString(requestBody));
        }

        return nameParams[2];
    }

    @Override
    public Result onBuildCompletedNew(ProgrammingExerciseParticipation participation, Object requestBody) throws Exception {
        // TODO
        return null;
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        // TODO
        return null;
    }

    @Override
    public Boolean buildPlanIdIsValid(String projectKey, String buildPlanId) {
        try {
            getPlanConfig(projectKey, buildPlanId);
            return true;
        }
        catch (Exception emAll) {
            return false;
        }
    }

    @Override
    public Optional<Result> retrieveLatestBuildResult(ProgrammingExerciseParticipation participation, ProgrammingSubmission submission) {
        return Optional.empty();
    }

    @Override
    public List<Feedback> getLatestBuildResultDetails(Result result) {
        return null;
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
        return null;
    }

    @Override
    public String checkIfProjectExists(String projectKey, String projectName) {
        return null;
    }

    @Override
    public boolean isBuildPlanEnabled(String planId) {
        return false;
    }

    @Override
    public String enablePlan(String planKey) {
        return null;
    }

    @Override
    public void updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String repoProjectName, String repoName,
            Optional<List<String>> triggeredBy) {
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
        return null;
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
        final var resourcePath = Path.of("build", "jenkins", "exerciseConfig.xml");
        final var projectConfig = XmlFileUtils.readXmlFile(resourcePath);
        final var errorMessage = "Error creating folder for exercise " + programmingExercise;

        postXml(projectConfig, String.class, HttpStatus.OK, errorMessage, Endpoint.NEW_FOLDER, Map.of("name", programmingExercise.getProjectKey()));
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
            jenkinsServer.createJob(folder, planName, writeXmlToString(jobXml));
        }
        catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new JenkinsException(e.getMessage(), e);
        }
    }

    private Document getPlanConfig(String projectKey, String planKey) {
        final var builder = Endpoint.PLAN_CONFIG.buildEndpoint(JENKINS_SERVER_URL.toString(), projectKey, planKey);
        try {
            final var configString = restTemplate.getForObject(builder.build(true).toString(), String.class);
            return XmlFileUtils.readFromString(configString);
        }
        catch (HttpClientErrorException e) {
            final var errorMessage = "Unable to fetch job config for " + planKey;
            log.error(errorMessage);
            throw new JenkinsException(errorMessage, e);
        }
    }

    private <T> T post(Endpoint endpoint, HttpStatus allowedStatus, String messageInCaseOfError, Class<T> responseType, Object... args) {
        final var builder = endpoint.buildEndpoint(JENKINS_SERVER_URL.toString(), args);
        try {
            final var response = restTemplate.postForEntity(builder.build(true).toString(), null, responseType);
            if (response.getStatusCode() != allowedStatus) {
                throw new VersionControlException(
                        messageInCaseOfError + "; statusCode=" + response.getStatusCode() + "; headers=" + response.getHeaders() + "; body=" + response.getBody());
            }
            return response.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error(messageInCaseOfError);
            throw new VersionControlException(messageInCaseOfError, e);
        }
    }

    private <T> T postXml(Document doc, Class<T> responseType, HttpStatus allowedStatus, String messagInCaseOfError, Endpoint endpoint, Map<String, Object> queryParams,
            Object... args) {
        return postXml(doc, responseType, List.of(allowedStatus), messagInCaseOfError, endpoint, queryParams, args);
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
                throw new VersionControlException(
                        messagInCaseOfError + "; statusCode=" + response.getStatusCode() + "; headers=" + response.getHeaders() + "; body=" + response.getBody());
            }

            return response.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error(messagInCaseOfError);
            throw new VersionControlException(messagInCaseOfError, e);
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
            throw new VersionControlException(errorMessage, e);
        }
    }

    private enum Endpoint {

        NEW_PLAN("job", "<projectKey>", "createItem"), NEW_FOLDER("createItem"), DELETE_FOLDER("job", "<projectKey>", "doDelete"),
        DELETE_JOB("job", "<projectKey>", "job", "<planName>", "doDelete"), PLAN_CONFIG("job", "<projectKey>", "job", "<planKey>", "config.xml"),
        TRIGGER_BUILD("job", "<projectKey>", "job", "<planKey>", "build");

        private List<String> pathSegments;

        Endpoint(String... pathSegments) {
            this.pathSegments = Arrays.asList(pathSegments);
        }

        public UriComponentsBuilder buildEndpoint(String baseUrl, Object... args) {
            for (int i = 0, segmentCtr = 0; i < pathSegments.size(); i++) {
                if (pathSegments.get(i).matches("<.*>")) {
                    if (segmentCtr == args.length) {
                        throw new IllegalArgumentException("Unable to build endpoint. Too few arguments!");
                    }
                    pathSegments.set(i, String.valueOf(args[segmentCtr++]));
                }
            }

            return UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(pathSegments.toArray(new String[0]));
        }
    }

    private String getCleanPlanName(String name) {
        return name.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
