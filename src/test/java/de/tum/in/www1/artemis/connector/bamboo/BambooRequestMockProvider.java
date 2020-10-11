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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.*;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.util.Verifiable;

@Component
@Profile("bamboo")
public class BambooRequestMockProvider {

    @Value("${artemis.continuous-integration.url}")
    private URL BAMBOO_SERVER_URL;

    @Autowired
    private ObjectMapper mapper;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    public BambooRequestMockProvider(@Qualifier("bambooRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        enableMockingOfRequests(false);
    }

    public void enableMockingOfRequests(boolean ignoreExpectOrder) {
        MockRestServiceServer.MockRestServiceServerBuilder builder = MockRestServiceServer.bindTo(restTemplate);
        builder.ignoreExpectOrder(ignoreExpectOrder);
        mockServer = builder.build();
    }

    public void reset() {
        if (mockServer != null) {
            mockServer.reset();
        }
    }

    /**
     * This method mocks that the programming exercise with the same project key (based on the course + programming exercise short name) already exists
     *
     * @param exercise the programming exercise that already exists
     */
    public void mockProjectKeyExists(ProgrammingExercise exercise) {
        mockServer.expect(ExpectedCount.once(), requestTo(BAMBOO_SERVER_URL + "/rest/api/latest/project/" + exercise.getProjectKey())).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK));
    }

    /**
     * This method mocks that the programming exercise with the same project name already exists (depending on the boolean input exists), based on the programming exercise title
     *
     * @param exercise the programming exercise that might already exist
     * @param exists   whether the programming exercise with the same title exists
     * @throws IOException
     * @throws URISyntaxException
     */
    public void mockCheckIfProjectExists(ProgrammingExercise exercise, final boolean exists) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectName = exercise.getProjectName();
        final var bambooSearchDTO = new BambooProjectSearchDTO();
        final var searchResult = new BambooProjectSearchDTO.SearchResultDTO();
        final var foundProject = new BambooProjectDTO();
        foundProject.setProjectName(exercise.getProjectName() + (exists ? "" : "abc"));
        searchResult.setSearchEntity(foundProject);
        bambooSearchDTO.setSize(1);
        bambooSearchDTO.setSearchResults(List.of(searchResult));

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

        final var instructorURI = buildGivePermissionsURIFor(projectKey, exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName());
        mockServer.expect(ExpectedCount.once(), requestTo(instructorURI)).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json(mapper.writeValueAsString(List.of("CREATE", "READ", "ADMINISTRATION")))).andRespond(withStatus(HttpStatus.NO_CONTENT));

        if (exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName() != null) {
            final var tutorURI = buildGivePermissionsURIFor(projectKey, exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName());
            mockServer.expect(ExpectedCount.once(), requestTo(tutorURI)).andExpect(method(HttpMethod.PUT)).andExpect(content().json(mapper.writeValueAsString(List.of("READ"))))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));
        }
    }

    private URI buildGivePermissionsURIFor(String projectKey, String groupName) throws URISyntaxException {
        return UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/permissions/project/").pathSegment(projectKey).path("/groups/").pathSegment(groupName)
                .build().toUri();
    }

    public List<Verifiable> mockCopyBuildPlanForParticipation(ProgrammingExercise exercise, String username) {
        final var verifications = new LinkedList<Verifiable>();
        final var projectKey = exercise.getProjectKey();
        final var targetPlanName = username.toUpperCase();
        final var targetPlanKey = projectKey + "-" + targetPlanName;
        final var sourcePlanKey = projectKey + "-" + BuildPlanType.TEMPLATE.getName();
        final var buildProjectName = exercise.getCourseViaExerciseGroupOrCourseMember().getShortName().toUpperCase() + " " + exercise.getTitle();

        when(planHelper.clonePlan(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn("success");
        verifications.add((() -> verify(planHelper, times(1)).clonePlan(sourcePlanKey, targetPlanKey, targetPlanName, "", buildProjectName, true)));

        return verifications;
    }

    public List<Verifiable> mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        final var projectKey = exercise.getProjectKey();
        final var bambooRepoName = Constants.ASSIGNMENT_REPO_NAME;
        final var bitbucketRepoName = projectKey.toLowerCase() + "-" + username;

        return mockUpdatePlanRepository(exercise, username, bambooRepoName, bitbucketRepoName, List.of());
    }

    public List<Verifiable> mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String bambooRepoName, String bitbucketRepoName, List<String> triggeredBy) {
        final var verifications = new LinkedList<Verifiable>();
        final var projectKey = exercise.getProjectKey();
        final var planKey = (projectKey + "-" + planName).toUpperCase();
        final var repositoryResponse = new BambooRepositoryDTO(null, 12345678L, "testName");

        doReturn(repositoryResponse).when(repositoryHelper).getRemoteRepository(bambooRepoName, planKey, false);
        verifications.add(() -> verify(repositoryHelper, times(1)).getRemoteRepository(bambooRepoName, planKey, false));

        doNothing().when(bambooBuildPlanUpdateProvider).updateRepository(any(), anyString(), anyString(), anyString());

        if (!triggeredBy.isEmpty()) {
            // Bamboo specific format for the used CLI dependency. Nothing we can improve here
            final var oldTriggers = "foo,123,artemis\nbar,456,artemis";
            doReturn(oldTriggers).when(triggerHelper).getTriggerList(anyString(), isNull(), isNull(), anyInt(), any());
            doReturn("foobar").when(triggerHelper).removeTrigger(planKey, null, null, 123L, null, false);
            doReturn("foobar").when(triggerHelper).removeTrigger(planKey, null, null, 456L, null, false);
            verifications.add(() -> {
                verify(triggerHelper).removeTrigger(planKey, null, null, 123L, null, false);
                verify(triggerHelper).removeTrigger(planKey, null, null, 456L, null, false);
            });
            for (final var repo : triggeredBy) {
                doReturn("foobar").when(triggerHelper).addTrigger(planKey, null, "remoteBitbucketServer", null, null, repo, null, null, false);
                verifications.add(() -> verify(triggerHelper).addTrigger(planKey, null, "remoteBitbucketServer", null, null, repo, null, null, false));
            }
        }

        return verifications;
    }

    public void mockTriggerBuild(ProgrammingExerciseParticipation participation) throws URISyntaxException {
        final var buildPlan = participation.getBuildPlanId();
        mockTriggerBuild(buildPlan);
    }

    public void mockTriggerBuild(String buildPlan) throws URISyntaxException {
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

    /**
     * This method mocks that the artifact page the latest build result is empty
     */
    public void mockRetrieveEmptyArtifactPage() throws URISyntaxException, JsonProcessingException, MalformedURLException {
        var indexOfResponse = "href=\"/download/1\"";
        var noArtifactsResponse = "";
        final var uri = new URL("https://bamboo.ase.in.tum.de/download/").toURI();
        final var uri2 = new URL("https://bamboo.ase.in.tum.de/download/1").toURI();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_HTML).body(indexOfResponse));
        mockServer.expect(requestTo(uri2)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(noArtifactsResponse));
    }

    /**
     * This method mocks that the build log of a given plan has failed
     *
     * @param planKey the build plan id
     */
    public void mockFetchBuildLogs(String planKey) throws URISyntaxException, JsonProcessingException, MalformedURLException {
        var newDate = new Date().getTime();
        Map firstLogEntry = Map.of("log", "java.lang.AssertionError: BubbleSort does not sort correctly", "date", newDate);
        Map response = Map.of("logEntries", Map.of("logEntry", List.of(firstLogEntry)));
        final var uri = UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/result").pathSegment(planKey.toUpperCase() + "-JOB1")
                .pathSegment("latest.json").queryParam("expand", "logEntries&max-results=2000").build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(response)));
    }

    private QueriedBambooBuildResultDTO createBuildResult(final String planKey) throws JsonProcessingException, MalformedURLException {
        final var buildResult = new QueriedBambooBuildResultDTO();
        final var testResults = new QueriedBambooBuildResultDTO.BambooTestResultsDTO();
        final var failedTests = new QueriedBambooBuildResultDTO.BambooFailedTestsDTO();
        final var changes = new BambooChangesDTO();
        final var artifacts = new BambooArtifactsDTO();
        final var buildArtifact1 = new BambooArtifactsDTO.BambooArtifactDTO();
        final var buildArtifact2 = new BambooArtifactsDTO.BambooArtifactDTO();
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
        buildResult.setBuildState(QueriedBambooBuildResultDTO.BuildState.FAILED);
        buildResult.setBuildTestSummary("0 of 3 passed");
        buildResult.setVcsRevisionKey(TestConstants.COMMIT_HASH_STRING);
        buildResult.setTestResults(testResults);

        changes.setSize(0);
        changes.setExpand("change");
        changes.setChanges(new LinkedList<>());
        buildResult.setChanges(changes);

        buildLink.setLinkToArtifact(new URL("https://bamboo.ase.in.tum.de/download/"));
        buildLink.setRel("self");
        buildArtifact1.setLink(buildLink);
        buildArtifact1.setName("Build log");
        buildArtifact1.setProducerJobKey(planKey + "-JOB-1");
        buildArtifact1.setShared(false);
        buildArtifact2.setLink(buildLink);
        buildArtifact2.setName("Mock Build log");
        buildArtifact2.setProducerJobKey(planKey + "-JOB-1");
        buildArtifact2.setShared(false);
        artifacts.setArtifacts(List.of(buildArtifact1, buildArtifact2));
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

    public void mockBuildPlanIsValid(final String buildPlanId, final boolean isValid) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(BAMBOO_SERVER_URL.toURI()).path("/rest/api/latest/plan/").pathSegment(buildPlanId).build().toUri();

        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(isValid ? HttpStatus.OK : HttpStatus.BAD_REQUEST));
    }

    public Verifiable mockCopyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetPlanName) {
        final var targetPlanKey = targetProjectKey + "-" + targetPlanName;
        final var sourcePlanKey = sourceProjectKey + "-" + sourcePlanName;
        doReturn(targetPlanKey).when(planHelper).clonePlan(eq(sourcePlanKey), eq(targetPlanKey), eq(targetPlanName), eq(""), anyString(), eq(true));

        return () -> verify(planHelper, times(1)).clonePlan(eq(sourcePlanKey), eq(targetPlanKey), eq(targetPlanName), eq(""), anyString(), eq(true));
    }

    public Verifiable mockEnablePlan(String projectKey, String planName) {
        final var planKey = projectKey + "-" + planName;
        doReturn("foobar").when(planHelper).enablePlan(planKey, true);

        return () -> verify(planHelper, times(1)).enablePlan(eq(planKey), eq(true));
    }

    public Verifiable mockDeleteProject(String projectKey) {
        doReturn("foobar").when(projectHelper).deleteProject(projectKey);
        return () -> verify(projectHelper).deleteProject(projectKey);
    }

    public Verifiable mockDeletePlan(String planKey) {
        doReturn("foobar").when(planHelper).deletePlan(planKey);
        return () -> verify(planHelper).deletePlan(planKey);
    }
}
