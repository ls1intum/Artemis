package de.tum.in.www1.artemis.connector.jenkins;

import com.appfire.common.cli.CliClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.CIPermission;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.ErrorDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestCaseDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestResultsDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.TestsuiteDTO;
import de.tum.in.www1.artemis.util.Verifiable;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@Component
@Profile("jenkins")
public class JenkinsRequestMockProvider {

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @Autowired
    private ObjectMapper mapper;

    public JenkinsRequestMockProvider(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        MockitoAnnotations.initMocks(this);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCheckIfProjectExists(ProgrammingExercise exercise, final boolean exists) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        //final var projectName = exercise.getProjectName();
        if (!exists) {
            mockServer.expect(ExpectedCount.once(), requestTo(JENKINS_SERVER_URL + "/job/" + projectKey + "/api/json")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("Folder " + projectKey + " does not exist!"));
        } else {
            mockServer.expect(ExpectedCount.once(), requestTo(JENKINS_SERVER_URL + "/job/" + projectKey + "/api/json")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));
        }
    }

    public void mockRemoveAllDefaultProjectPermissions(ProgrammingExercise exercise) {
        //TODO after decision on how to handle users on Jenkins has been made
    }

    public void mockGiveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions) {
        // TODO after decision on how to handle users on Jenkins has been made
    }

    public void mockCopyBuildPlanForParticipation(ProgrammingExercise exercise, String username) throws CliClient.RemoteRestException, CliClient.ClientException {
        final var verifications = new LinkedList<Verifiable>();
        final var projectKey = exercise.getProjectKey();
        final var targetPlanName = username.toUpperCase();
        final var targetPlanKey = projectKey + "-" + targetPlanName;
        final var sourcePlanKey = projectKey + "-" + BuildPlanType.TEMPLATE.getName();

        mockCopyBuildPlan(projectKey, sourcePlanKey, projectKey, targetPlanKey);
    }

    public void mockCopyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetPlanName)
        throws CliClient.RemoteRestException, CliClient.ClientException {
        mockServer.expect(ExpectedCount.once(), requestTo(JENKINS_SERVER_URL + "/job/" + sourceProjectKey + "/job/" + sourcePlanName + "config.xml"))
            .andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK));
        mockServer.expect(ExpectedCount.once(), requestTo(JENKINS_SERVER_URL + "/job/" + targetProjectKey + "/createItem?name=" + targetPlanName))
            .andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        final var projectKey = exercise.getProjectKey();
        final var planName = exercise.getProjectKey() + "-" +  username.toUpperCase();
        final var repoNameInCI = Constants.ASSIGNMENT_REPO_NAME;
        final var vcsRepositoryUrl = projectKey.toLowerCase() + "-" + username;

        mockUpdatePlanRepository(exercise, projectKey, planName, repoNameInCI, vcsRepositoryUrl, List.of());
    }

    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String projectKey, String planName, String repoNameInCI, String vcsRepositoryUrl, List<String> triggeredBy) {
        //TODO
    }

    public void mockTriggerBuild(ProgrammingExerciseParticipation participation) throws URISyntaxException {
        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        mockTriggerBuild(projectKey, planKey);
    }

    public void mockTriggerBuild(String projectKey, String planKey) throws URISyntaxException {
        final var triggerBuildPath = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/" + projectKey + "/job/" + planKey + "/build").toUriString();
        mockServer.expect(requestTo(triggerBuildPath)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockQueryLatestBuildResultFromBambooServer(final String projectKey,final String planKey) throws JsonProcessingException, URISyntaxException {
        final var response = createBuildResult(projectKey + " - "+ planKey);
        final var uri = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/" + projectKey + "/job/" + planKey + "/lastBuild/testResults/api/json").toUriString();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    private TestResultsDTO createBuildResult(final String planKey) {
        final var result = new TestResultsDTO();
        final var suite = new TestsuiteDTO();
        suite.setTests(3);
        suite.setTestCases(List.of(createFailedTest("test1"), createFailedTest("test2"), createFailedTest("test3")));
        suite.setErrors(3);
        suite.setFailures(0);
        suite.setSkipped(0);
        result.setFullName(planKey);
        return result;
    }

    private TestCaseDTO createFailedTest(final String testName) {
        final var test = new TestCaseDTO();
        final var error = new ErrorDTO();

        error.setMessage("java.lang.AssertionError: Some assertion failed");
        test.setErrors(List.of(error));
        test.setName(testName);
        test.setTime(120);
        return test;
    }

    public void mockRetrieveBuildStatus(final String projectKey, final String planKey) throws URISyntaxException, JsonProcessingException {
        final var response = Map.of("building", false);
        final var uri = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/"+ projectKey+ "/job/" + planKey + "/lastBuild/api/json").toUriString();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    public void mockBuildPlanIsValid(final String projectKey, final String planKey, final boolean isValid) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/"+ projectKey+ "/job/" + planKey + "/api/json").toUriString();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(isValid ? HttpStatus.OK : HttpStatus.BAD_REQUEST));
    }

    public void mockEnablePlan(final String projectKey, final String planKey) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/"+ projectKey + "/job/" + planKey + "/enable").toUriString();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)) .andRespond(withStatus(HttpStatus.OK));
    }

    public void  mockDeleteProject(final String projectKey) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/"+ projectKey + "/doDelete").toUriString();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)) .andRespond(withStatus(HttpStatus.OK));
    }

    public void mockDeletePlan(final String projectKey, final String planKey) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("/job/"+ projectKey + "/job/" + planKey + "/doDelete").toUriString();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)) .andRespond(withStatus(HttpStatus.OK));
    }

}
