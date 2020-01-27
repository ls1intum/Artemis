package de.tum.in.www1.artemis.connector.bamboo;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.appfire.bamboo.cli.BambooClient;
import com.appfire.bamboo.cli.helpers.PlanHelper;
import com.appfire.bamboo.cli.helpers.RepositoryHelper;
import com.appfire.bamboo.cli.objects.RemoteRepository;
import com.appfire.common.cli.CliClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooBuildPlanUpdateProvider;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.*;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.util.Verifiable;

@Component
@Profile("bamboo")
public class BambooRequestMockProvider {

    @Mock
    private PlanHelper planHelper;

    @Mock
    private RepositoryHelper repositoryHelper;

    @Value("${artemis.continuous-integration.url}")
    private URL BAMBOO_SERVER_URL;

    @SpyBean
    @InjectMocks
    private BambooClient bambooClient;

    @SpyBean
    private BambooBuildPlanUpdateProvider bambooBuildPlanUpdateProvider;

    @Autowired
    private ObjectMapper mapper;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    public BambooRequestMockProvider(@Qualifier("bambooRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        MockitoAnnotations.initMocks(this);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCheckIfProjectExists(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var bambooSearchDTO = new BambooProjectSearchDTO();
        bambooSearchDTO.setSize(0);
        bambooSearchDTO.setSearchResults(new ArrayList<>());

        mockServer.expect(ExpectedCount.once(), requestTo(BAMBOO_SERVER_URL + "/rest/api/latest/project/" + projectKey)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        final var projectSearchPath = UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/search/projects").queryParam("searchTerm", projectName);
        mockServer.expect(ExpectedCount.once(), requestTo(projectSearchPath.build().toUri())).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body(mapper.writeValueAsString(bambooSearchDTO)).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockRemoveAllDefaultProjectPermissions(ProgrammingExercise exercise) {
        final var projectKey = exercise.getProjectKey();
        List.of("ANONYMOUS", "LOGGED_IN").stream().map(role -> {
            try {
                return UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/permissions/project/").pathSegment(projectKey).path("/roles/")
                        .pathSegment(role).build().toUri();
            }
            catch (URISyntaxException e) {
                throw new AssertionError("Should be able to build URIs for Bamboo roles in mock setup");
            }
        }).forEach(rolePath -> mockServer.expect(ExpectedCount.once(), requestTo(rolePath)).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NO_CONTENT)));
    }

    public void mockGiveProjectPermissions(ProgrammingExercise exercise) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();

        final var instructorURI = buildGivePermissionsURIFor(projectKey, exercise.getCourse().getInstructorGroupName());
        mockServer.expect(ExpectedCount.once(), requestTo(instructorURI)).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json(mapper.writeValueAsString(List.of("CREATE", "READ", "ADMINISTRATION")))).andRespond(withStatus(HttpStatus.NO_CONTENT));

        if (exercise.getCourse().getTeachingAssistantGroupName() != null) {
            final var tutorURI = buildGivePermissionsURIFor(projectKey, exercise.getCourse().getTeachingAssistantGroupName());
            mockServer.expect(ExpectedCount.once(), requestTo(tutorURI)).andExpect(method(HttpMethod.PUT)).andExpect(content().json(mapper.writeValueAsString(List.of("READ"))))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));
        }
    }

    private URI buildGivePermissionsURIFor(String projectKey, String groupName) throws URISyntaxException {
        return UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/permissions/project/").pathSegment(projectKey).path("/groups/").pathSegment(groupName)
                .build().toUri();
    }

    public List<Verifiable> mockCopyBuildPlanForParticipation(ProgrammingExercise exercise, String username) throws CliClient.RemoteRestException, CliClient.ClientException {
        final var verifications = new LinkedList<Verifiable>();
        final var projectKey = exercise.getProjectKey();
        final var targetPlanName = username.toUpperCase();
        final var targetPlanKey = projectKey + "-" + targetPlanName;
        final var sourcePlanKey = projectKey + "-" + BuildPlanType.TEMPLATE.getName();
        final var buildProjectName = exercise.getCourse().getShortName().toUpperCase() + " " + exercise.getTitle();

        when(planHelper.clonePlan(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("success");
        verifications.add((() -> verify(planHelper, times(1)).clonePlan(sourcePlanKey, targetPlanKey, targetPlanName, "", buildProjectName, true)));

        return verifications;
    }

    public List<Verifiable> mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username)
            throws CliClient.RemoteRestException, CliClient.ClientException {
        final var verifications = new LinkedList<Verifiable>();
        final var projectKey = exercise.getProjectKey();
        final var bambooRepoName = Constants.ASSIGNMENT_REPO_NAME;
        final var planKey = (projectKey + "-" + username).toUpperCase();
        final var repositoryResponse = new RemoteRepository(null, null, "testName");
        final var bitbucketRepoName = projectKey.toLowerCase() + "-" + username;

        when(repositoryHelper.getRemoteRepository(anyString(), anyString(), anyBoolean())).thenReturn(repositoryResponse);
        verifications.add(() -> verify(repositoryHelper, times(1)).getRemoteRepository(bambooRepoName, planKey, false));

        doNothing().when(bambooBuildPlanUpdateProvider).updateRepository(repositoryResponse, bitbucketRepoName, projectKey.toUpperCase(), planKey);

        return verifications;
    }

    public void mockTriggerBuild(ProgrammingExerciseParticipation participation) throws URISyntaxException {
        final var buildPlan = participation.getBuildPlanId();
        final var triggerBuildPath = UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/queue/").pathSegment(buildPlan).build().toUri();

        mockServer.expect(requestTo(triggerBuildPath)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    public void mockQueryLatestBuildResultFromBambooServer(String planKey) throws URISyntaxException, JsonProcessingException, MalformedURLException {
        final var response = createBuildResult(planKey);
        final var uri = UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/result").pathSegment(planKey.toUpperCase() + "-JOB1")
                .pathSegment("latest.json").queryParam("expand", "testResults.failedTests.testResult.errors,artifacts,changes,vcsRevisions").build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    private BambooBuildResultDTO createBuildResult(final String planKey) throws JsonProcessingException, MalformedURLException {
        final var buildResult = new BambooBuildResultDTO();
        final var testResults = new BambooBuildResultDTO.BambooTestResultsDTO();
        final var failedTests = new BambooBuildResultDTO.BambooFailedTestsDTO();
        final var changes = new BambooChangesDTO();
        final var artifacts = new BambooArtifactsDTO();
        final var buildArtifact = new BambooArtifactsDTO.BambooArtifactDTO();
        final var buildLink = new BambooArtifactsDTO.BambooArtifactLinkDTO();

        failedTests.setExpand("testResult");
        failedTests.setSize(3);
        failedTests.setTestResults(List.of(createFailedTest("test1"), createFailedTest("test2"), createFailedTest("test3")));

        testResults.setAll(3);
        testResults.setExistingFailed(0);
        testResults.setFailed(3);
        testResults.setFixed(0);
        testResults.setNewFailed(0);
        testResults.setQuarantined(0);
        testResults.setSkipped(0);
        testResults.setSuccessful(0);
        testResults.setFailedTests(failedTests);

        buildResult.setBuildCompletedDate(ZonedDateTime.now().minusMinutes(1));
        buildResult.setBuildReason("Initial clean build");
        buildResult.setBuildState(BambooBuildResultDTO.BuildState.FAILED);
        buildResult.setBuildTestSummary("3 of 3 failed");
        buildResult.setVcsRevisionKey(TestConstants.COMMIT_HASH_STRING);
        buildResult.setTestResults(testResults);

        changes.setSize(0);
        changes.setExpand("change");
        changes.setChanges(new LinkedList<>());
        buildResult.setChanges(changes);

        buildLink.setLinkToArtifact(new URL("https://bamboobruegge.in.tum.de/download/"));
        buildLink.setRel("self");
        buildArtifact.setLink(buildLink);
        buildArtifact.setName("Build log");
        buildArtifact.setProducerJobKey(planKey + "-JOB-1");
        buildArtifact.setShared(false);
        artifacts.setArtifacts(List.of(buildArtifact));
        artifacts.setSize(1);
        buildResult.setArtifacts(artifacts);

        return buildResult;
    }

    private BambooTestResultDTO createFailedTest(final String testName) {
        final var failed = new BambooTestResultDTO();
        final var resultError = new BambooTestResultDTO.BambooTestResultErrorsDTO();
        final var error = new BambooTestResultDTO.BambooTestErrorDTO();

        error.setMessage("java.lang.AssertionError: Some assertion failed");

        resultError.setMaxResult(1);
        resultError.setSize(1);
        resultError.setErrorMessages(List.of(error));

        failed.setClassName("some.package.ClassName");
        failed.setMethodName(testName);
        failed.setDuration(2);
        failed.setDurationInSeconds(120);
        failed.setStatus("failed");
        failed.setErrors(resultError);

        return failed;
    }

    public void mockRetrieveBuildStatus(final String planKey) throws URISyntaxException, JsonProcessingException {
        final var response = Map.of("isActive", true, "isBuilding", false);
        final var uri = UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/plan").pathSegment(planKey + ".json").build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }
}
