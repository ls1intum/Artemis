package de.tum.in.www1.artemis.connector.bamboo;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooBuildPlanUpdateProvider;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooProjectSearchDTO;
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

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

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
        final var tutorURI = buildGivePermissionsURIFor(projectKey, exercise.getCourse().getTeachingAssistantGroupName());

        mockServer.expect(ExpectedCount.once(), requestTo(instructorURI)).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json(mapper.writeValueAsString(List.of("CREATE", "READ", "ADMINISTRATION")))).andRespond(withStatus(HttpStatus.NO_CONTENT));
        mockServer.expect(ExpectedCount.once(), requestTo(tutorURI)).andExpect(method(HttpMethod.PUT)).andExpect(content().json(mapper.writeValueAsString(List.of("READ"))))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
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
}
