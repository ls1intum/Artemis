package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.HttpException;
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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;

@Profile("jenkins")
@Service
public class JenkinsService implements ContinuousIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JenkinsService.class);

    @Value("${artemis.jenkins.url}")
    private URL JENKINS_SERVER_URL;

    private final JenkinsBuildPlanCreatorFactory buildPlanCreatorFactory;

    private final RestTemplate restTemplate;

    public JenkinsService(JenkinsBuildPlanCreatorFactory buildPlanCreatorFactory, @Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
        this.buildPlanCreatorFactory = buildPlanCreatorFactory;
        this.restTemplate = restTemplate;
    }

    @Override
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, URL repositoryURL, URL testRepositoryURL) {
        final var configBuilder = buildPlanCreatorFactory.builderFor(exercise.getProgrammingLanguage());
        final var jobConfig = configBuilder.buildBasicConfig(testRepositoryURL, repositoryURL);

        postXml(jobConfig, String.class, HttpStatus.OK, "", Endpoint.NEW_PLAN, Map.of("name", planKey), exercise.getProjectKey());
    }

    @Override
    public String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName) {
        return null;
    }

    @Override
    public void configureBuildPlan(ProgrammingExerciseParticipation participation) {

    }

    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws HttpException {

    }

    @Override
    public void deleteProject(String projectKey) {

    }

    @Override
    public void deleteBuildPlan(String buildPlanId) {

    }

    @Override
    public Result onBuildCompletedOld(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public String getPlanKey(Object requestBody) throws Exception {
        return null;
    }

    @Override
    public Result onBuildCompletedNew(ProgrammingExerciseParticipation participation, Object requestBody) throws Exception {
        return null;
    }

    @Override
    public BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation) {
        return null;
    }

    @Override
    public Boolean buildPlanIdIsValid(String buildPlanId) {
        return null;
    }

    @Override
    public List<Feedback> getLatestBuildResultDetails(Result result) {
        return null;
    }

    @Override
    public List<BuildLogEntry> getLatestBuildLogs(String buildPlanId) {
        return null;
    }

    @Override
    public URL getBuildPlanWebUrl(ProgrammingExerciseParticipation participation) {
        return null;
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
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
    }

    @Override
    public void updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String repoProjectName, String repoName) {
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
        NEW_PLAN("job", "<projectKey>", "createItem");

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
}
